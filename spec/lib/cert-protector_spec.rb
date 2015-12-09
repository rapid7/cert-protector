require File.expand_path '../../spec_helper.rb', __FILE__

require 'rspec'
require 'rack/test'

describe "Cert-Protector" do

  describe "Application" do

    it "should 404 accessing the home page" do
      get '/'
      expect(last_response.status).to eq(404)
    end

    it "should return an empty body for the home page" do
      get '/'
      expect(last_response.body).to eq('')
    end

    it "should 404 accessing specific paths" do
      get '/sign'
      expect(last_response.status).to eq(404)
      get '/sign/gpg'
      expect(last_response.status).to eq(404)
      get '/sign/codesign'
      expect(last_response.status).to eq(404)
      get '/sign/openssl'
      expect(last_response.status).to eq(404)
      put '/sign'
      expect(last_response.status).to eq(404)
    end

  end
end
