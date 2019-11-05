require 'sinatra/base'
require 'digest'
require 'yaml'
require 'expect'
require 'pty'
require 'timeout'

class CertProtector < Sinatra::Base
  # Setup basic auth to use the config file credentials
  use Rack::Auth::Basic, "Protected Area" do |username, password|
    username == YAML.load_file('config/config.yml')['auth']['username'] && password == YAML.load_file('config/config.yml')['auth']['password']
  end

  # Setup a logger
  use Rack::Logger
  # The input file variable for an uploaded file name and path
  attr_accessor :infile
  # The output file variable for an file name and path that will be sent back to the requestor
  attr_accessor :outfile
  # The yaml variable containing all configuration needed for execution
  attr_accessor :config
  # The password required to execute a given command
  attr_accessor :password
  # The string password or passphrase prompt
  attr_accessor :prompt
  # The action to perform
  attr_accessor :action

  before do
    # Load the configuration
    @config = YAML.load_file('config/config.yml')
    # Create temp files
    @infile = generate_filename
    @outfile = generate_filename
  end

  not_found do
    status 404
    ''
  end

  put '/sign/gpg' do
    @password = config['gpg']['password']
    @prompt   = "Enter passphrase:"
    run("gpg --armor --local-user #{config['gpg']['key']} --output #{outfile} --detach-sig #{infile}")
  end

  put '/sign/openssl' do
    @password = config['openssl']['password']
    @prompt   = "Enter pass phrase for #{config['openssl']['keypath']}:"
    run("openssl dgst -sha256 -sign #{config['openssl']['keypath']} -out #{outfile} #{infile}")
  end

  put '/sign/codesign' do
    @password = config['codesign']['password']
    @prompt   = "Password:"
    run("osslsigncode -askpass -h sha256 -pkcs12 #{config['codesign']['keypath']} -t #{config['codesign']['timestamp']} -in #{infile} -out #{outfile}")
  end

  # Runs the command for the associated target and pushes the file back to the requestor
  # +command+:: command to execute
  def run(command)
    begin
      @action = command.split(' ').first
      logger.info("[#{action}] Receiving upload file...")
      write_file
      logger.info("[#{action}] Received upload file with SHA256: #{generate_digest(infile)}")
      run_system_cmd(command)
      logger.info("[#{action}] Sending processed file with SHA256: #{generate_digest(outfile)}")
      push_file
    rescue
      cleanup
    end
  end

  # Pushes a file back to the requestor after processing
  def push_file
    return_content = File.read(outfile)
    cleanup
    return_content
  end

  # Removes the input and output files on the system
  def cleanup
    File.delete(outfile) if File.exist?(outfile)
    File.delete(infile) if File.exist?(infile)
  end

  # Runs the provided system command, waits for a prompt then enters a password or passphrase at the prompt
  # +command+:: command to execute
  def run_system_cmd(command)
    begin
      Timeout.timeout(config['app']['timeout']) do
        PTY.spawn(command) do |reader, writer, pid|
          begin
            reader.expect(/#{prompt}/, 5)
            writer.puts(password)
            begin
              reader.read() while !reader.eof?
            rescue Errno::EIO => e
              # Do nothing here: http://stackoverflow.com/a/10306463
            end
          ensure
            Process.wait(pid)
          end
        end
      end
    rescue Timeout::Error
      logger.info("[#{action}] Timed out during execution of command!")
    end
  end

  # Writes an uploaded file to disk
  def write_file
    File.open(infile, 'w+') do |file|
      file.write(request.body.read)
    end
  end

  # Generates a 256 SHA for a provided file
  # +file+:: full path and filename to generate a sha
  def generate_digest(file)
    Digest::SHA256.hexdigest(File.read(file))
  end

  # Create a temporary filepath and name
  def generate_filename
    Dir::Tmpname.create([[*('a'..'z')].sample(1).join, [*('a'..'z')].sample(1).join]) { }
  end
end
