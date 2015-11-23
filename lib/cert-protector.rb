require 'sinatra'
require 'openssl'
require 'gpgme'
require 'fileutils'
require 'digest'
require 'yaml'
require 'expect'
require 'pty'

class CertProtector < Sinatra::Base
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

  before do
    load_config
  end

  not_found do
    status 404
    ''
  end

  get '/' do
    ''
  end

  put '/:sign_type' do
    action = params[:sign_type]
    case action
    when "gpg"
      do_setup
      @password = config['gpg']['password']
      @prompt   = "Enter passphrase:"
      run("gpg --armor --output #{outfile} --detach-sig #{infile}")
    when "openssl"
      do_setup
      @password = config['openssl']['password']
      @prompt   = "Enter pass phrase for (.*):"
      run("openssl dgst -sha256 -sign #{config['openssl']['keypath']} -out #{outfile} #{infile}")
    when "codesign"
      do_setup
      @password = config['codesign']['password']
      @prompt   = "Password:"
      run("osslsigncode -askpass -pkcs12 #{config['codesign']['keypath']} -t http://timestamp.verisign.com/scripts/timstamp.dll -in #{infile} -out #{outfile}")
    else
      ''
    end
  end

  # Runs the command for the associated target and pushes the file back to the requestor
  def run(command)
    run_system_cmd(command)
    push_file
  end

  # Handles the uploaded file and generates an outfile name
  def do_setup
    process_upload
    @outfile = generate_filename
  end

  # Pushes a file back to the requestor after processing
  def push_file
    logger.info("Sending #{params[:sign_type]} file with SHA256: #{generate_digest(outfile)}")
    content = File.read(outfile)
    cleanup
    content
  end

  # Removes the input and output files on the system
  def cleanup
    File.delete(outfile)
    File.delete(infile)
  end

  # Runs the provided system command, waits for a prompt then enters a password or passphrase at the prompt
  def run_system_cmd(command)
    PTY.spawn(command) do |reader, writer|
      reader.expect(/#{prompt}/, 5)
      writer.puts(password)
    end
    sleep(5) # let command finish
  end

  # Load the yaml config with required properties
  def load_config
    @config = YAML.load_file('config/config.yml')
  end

  # Generates a random file path for uploaded file and writes the file to disk
  def process_upload
    @infile = generate_filename
    write_file
  end

  # Writes an uploaded file to disk
  def write_file
    logger.info("Receiving #{params[:sign_type]} upload file...")
    File.open(infile, 'w+') do |file|
      file.write(request.body.read)
    end
    logger.info("Received #{params[:sign_type]} upload file with SHA256: #{generate_digest(infile)}")
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
