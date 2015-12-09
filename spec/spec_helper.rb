require 'rack/test'
require 'rspec'

require File.expand_path '../../lib/cert-protector.rb', __FILE__

ENV['RACK_ENV'] = 'test'

module RSpecMixin
    include Rack::Test::Methods
      def app() CertProtector end
end

# For RSpec 2.x
RSpec.configure { |c| c.include RSpecMixin }
