require 'simplecov'
require 'simplecov-rcov'
require 'rspec'

lib = File.expand_path('../lib', File.dirname(__FILE__))
$LOAD_PATH.unshift(lib) unless $LOAD_PATH.include?(lib)
require 'cert-protector'

DUMMY_PATH = File.join(File.expand_path(File.dirname(__FILE__)), 'dummy')

ENV['RACK_ENV'] = 'test'

module RSpecMixin
    include Rack::Test::Methods
      def app() Sinatra::Application end
end

RSpec.configure do |config|

  # Disable old "should" syntax.  Force all specs to use
  # the new "expect" syntax.
  config.expect_with(:rspec) {|c| c.syntax = :expect}
  config.mock_with(:rspec) {|c| c.syntax = :expect}

  config.after(:all) do
    FileUtils.rm_rf(DUMMY_PATH)
  end

  def capture(stream)
    begin
      stream = stream.to_s
      eval "$#{stream} = StringIO.new"
      yield
      result = eval("$#{stream}").string
    ensure
      eval("$#{stream} = #{stream.upcase}")
    end

    result
  end
end

SimpleCov.formatter = SimpleCov::Formatter::RcovFormatter
