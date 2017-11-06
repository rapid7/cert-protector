/*
 * NOTE: Most of this code was adapted from https://github.com/cjnygard/rest-maven-plugin
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.rapid7;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.codehaus.plexus.util.FileUtils;

/**
 * Codesigns file using the Platform Delivery cert-protector service.
 *
 */
@Mojo( name = "codesign")
public class Plugin extends AbstractMojo
{

    /**
     * A URL path to the base of the REST request resource.
     *
     * This URL path is the base path, and can be used with multiple instances
     * (executions) in combination with the <code>resource</code> element to
     * specify different URL resources with a common base URL.
     *
     */
    @Parameter( defaultValue = "https://filesigner.osdc.lax.rapid7.com", property = "endpoint" )
    private URI endpoint;

    /**
     * A resource path added to the endpoint URL to access the REST resource.
     *
     * The <code>resource</code> path will be concatenated onto the
     * <code>endpoint</code> URL to create the full resource path.
     *
     * Query parameters can be added to the URL <code>resource</code> but the
     * preference is to use the <code>queryParams</code> map to add parameters
     * to the URL.
     */
    @Parameter( defaultValue = "/sign/codesign", property = "resource" )
    private String resource;

    /**
     * The username to use with the filesigner service via Basic HTTP auth.
     */
    @Parameter( property = "username" )
    private String username;

    /**
     * The password to use with the filesigner service via Basic HTTP auth.
     */
    @Parameter( property = "password" )
    private String password;

    /**
     * The method to use for the REST request.
     *
     * The REST request method can be configured via the <code>method</code>
     * tag.
     *
     * Defaults to <code>PUT</code>
     *
     */
    @Parameter( defaultValue = "PUT", property = "method" )
    private String method;

    /**
     * A list of {@link org.apache.maven.model.FileSet} rules to select files
     * and directories.
     *
     * This list of <code>fileset</code> elements will be used to gather all the
     * files to be submitted in the REST request. One REST request will be made
     * per file.
     */
    @Parameter( property = "filesets" )
    private List<FileSet> filesets = new ArrayList<>();

    /**
     * A {@link org.apache.maven.model.FileSet} rule to select files to send in
     * the REST request.
     *
     * The fileset will be used to gather all the files to be submitted in the
     * REST request. One REST request will be made per file.
     *
     * Internally, this element will be added to the list of
     * <code>filesets</code>, so it will be processed in addition to the list of
     * <code>filesets</code>
     */
    @Parameter( property = "fileset" )
    private FileSet fileset;

    /**
     * Path where REST query result files are stored.
     *
     * Defaults to <code>${project.build.directory}/rest</code>
     *
     */
    @Parameter( defaultValue = "${project.build.directory}/signed", property = "outputDir", required = true )
    private File outputDir;

    @Override
    public void execute() throws MojoExecutionException
    {
        List<File> files = getFilesToProcess();
        if ( (null == files) || (files.size() <= 0) )
        {
            getLog().info( "No files to sign" );
            return;
        }

        // Skip if username is set but password is blank.
        // Since we commonly pass the password via environment variables in Jenkins, this makes it easy
        // to skip code signing in development environments where the password variable isn't set.
        if ( null != getUsername() && !getUsername().isEmpty() )
        {
            if ( null == getPassword() || getPassword().isEmpty() )
            {
                getLog().warn( String.format(
                            "Skipping codesigning because no password is set for user: %s", getUsername() ) );
                return;
            }
        }

        validateOutputDir();

        Client client = ClientBuilder.newClient();

        WebTarget baseTarget = client.target( getEndpoint() );
        if ( null != getResource() )
        {
            getLog().debug( String.format( "Setting resource [%s]", getResource() ) );
            baseTarget = baseTarget.path( getResource() );
        }

        Invocation.Builder builder = baseTarget
            .request()
            .accept( getResponseType() )
            .header( "Content-Type", getRequestType() )
            .header( "Authorization", getBasicAuth() );

        for ( File f : files )
        {
            getLog().info( String.format( "Uploading %s to %s%s", f.getName(), getEndpoint(), getResource() ) );
            processResponse( builder.method( getMethod(), Entity.entity( f, getRequestType() ) ), f.getName() );
        }
    }

    protected List<File> getFilesToProcess() throws MojoExecutionException
    {
        List<File> files = new ArrayList<>();
        if ( null != getFileset() )
        {
            if ( null == getFilesets() )
            {
                filesets = new ArrayList<>();
            }
            getFilesets().add( getFileset() );

        }
        if ( null != getFilesets() )
        {
            for ( FileSet fs : getFilesets() )
            {
                if ( (null != fs) && (null != fs.getDirectory()) )
                {
                    FileSetTransformer fileMgr = new FileSetTransformer( fs );
                    files.addAll( fileMgr.toFileList() );
                }
            }
        }
        return files;
    }

    protected void pipeToFile( InputStream stream, File outputFile ) throws IOException
    {
        getLog().info( String.format( "Writing signed file: %s", outputFile.getCanonicalPath() ) );
        OutputStream outStream = new FileOutputStream( outputFile );

        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ( (bytesRead = stream.read( buffer )) != -1 )
        {
            outStream.write( buffer, 0, bytesRead );
        }
        IOUtils.closeQuietly( stream );
        IOUtils.closeQuietly( outStream );
    }

    private void processResponse( Response response, String outputFilename ) throws MojoExecutionException
    {
        if ( response.getStatusInfo().getFamily() == Family.SUCCESSFUL )
        {
            getLog().debug( String.format( "Status: [%d]", response.getStatus() ) );
            InputStream in = response.readEntity( InputStream.class );
            try
            {
                File of = new File( getOutputDir(), outputFilename );
                pipeToFile( in, of );
            }
            catch ( IOException ex )
            {
                getLog().debug( String.format( "IOException: [%s]", ex.toString() ) );
                throw new MojoExecutionException( String.format( "IOException: [%s]", ex.getMessage() ) );
            }

        }
        else
        {
            getLog().warn( String.format( "Error code: [%d]: %s", response.getStatus(), response.getStatusInfo() ) );
            getLog().debug( response.getEntity().toString() );
            throw new MojoExecutionException(
                    String.format( "Error code: [%d]: %s", response.getStatus(), response.getStatusInfo() )
            );
        }
    }

    protected boolean validateOutputDir() throws MojoExecutionException
    {
        try
        {
            if ( !outputDir.isDirectory() )
            {
                if ( outputDir.isFile() )
                {
                    getLog().error( String.format( "Error: [%s] is not a directory", outputDir.getCanonicalPath() ) );
                }
                else
                {
                    if ( !outputDir.mkdirs() )
                    {
                        getLog().error(
                                String.format( "Error: Unable to create path[%s]", outputDir.getCanonicalPath() ) );

                    }
                }
            }
        }

        catch ( IOException ex )
        {
            getLog().error( String.format( "IOException: [%s]", ex.toString() ) );
            throw new MojoExecutionException(
                    String.format( "Unable to create destination dir [%s]: [%s]", outputDir.toString(),
                            ex.toString() ) );
        }
        return true;
    }

    public final class FileSetTransformer
    {

        private final FileSet fileSet;

        private FileSetTransformer( FileSet fileSet )
        {
            this.fileSet = fileSet;
        }

        public List<File> toFileList() throws MojoExecutionException
        {
            return toFileList( fileSet );
        }

        public List<File> toFileList( FileSet fs ) throws MojoExecutionException
        {
            try
            {
                if ( fs.getDirectory() != null )
                {
                    File directory = new File( fs.getDirectory() );
                    String includes = toString( fs.getIncludes() );
                    String excludes = toString( fs.getExcludes() );
                    return FileUtils.getFiles( directory, includes, excludes );
                }
                else
                {
                    getLog().warn( String.format( "Fileset [%s] directory empty", fs.toString() ) );
                    return new ArrayList<>();
                }
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( String.format( "Unable to get paths to fileset [%s]", fs.toString() ),
                        e );
            }
        }

        private String toString( List<String> strings )
        {
            StringBuilder sb = new StringBuilder();
            for ( String string : strings )
            {
                if ( sb.length() > 0 )
                {
                    sb.append( ", " );
                }
                sb.append( string );
            }
            return sb.toString();
        }
    }

    /**
     * @return the endpoint
     */
    private String getBasicAuth()
    {
        String token = getUsername() + ":" + getPassword();
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes());
    }

    /**
     * @return the endpoint
     */
    public URI getEndpoint()
    {
        return endpoint;
    }

    /**
     * @return the filesets
     */
    public List<FileSet> getFilesets()
    {
        return filesets;
    }

    /**
     * @return the fileset
     */
    public FileSet getFileset()
    {
        return fileset;
    }

    /**
     * @return the method
     */
    public String getMethod()
    {
        return method;
    }

    /**
     * @return the outputDir
     */
    public File getOutputDir()
    {
        return outputDir;
    }

    /**
     * @return the password
     */
    public String getPassword()
    {
        return password;
    }

    /**
     * @return the resource
     */
    public String getResource()
    {
        return resource;
    }

    /**
     * @return the requestType
     */
    public MediaType getRequestType()
    {
        return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }

    /**
     * @return the responseType
     */
    public MediaType getResponseType()
    {
        return MediaType.APPLICATION_OCTET_STREAM_TYPE;
    }

    /**
     * @return the username
     */
    public String getUsername()
    {
        return username;
    }
}
