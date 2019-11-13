# cert-protector

The goal of this project is to allow you to lock down a system with your
signing keys (gpg, openssl or authenticode) and use this api _only_ to remotely
sign your objects rather than putting your high risk signing keys on a
jenkins server/slave or similar system.

## Installation

```bash
# Clone this repo
# Create a config/config.yml based on example file and modify
cp config/config.yml.example config/config.yml
# Install required gems
bundle install
# Start the server
rackup
# start signing things!
```

#### API
The api is intentionally simple.  More features may be added later.

```bash
# To gpg sign:
curl -v -o sample.file.sig  --upload-file ./sample.file http://127.0.0.1:9292/sign/gpg

# To openssl sign:
curl -v -o sample.file.sig --upload-file ./sample.file http://127.0.0.1:9292/sign/openssl

# To authenticode sign:
curl -v -o ./signed_installer.exe  --upload-file ./installer.exe http://127.0.0.1:9292/sign/codesign

# To productsign:
curl -v -o ./signed_installer.pkg  --upload-file ./installer.pkg http://127.0.0.1:9292/sign/productsign
```

#### Dependencies:
The following packages are required for command line execution during
object signing:
- gpg (for gpg signatures)
- osslsigncode (for authenticode signing)
- openssl (for openssl signatures)
- productsign (for macOS pkg signatures)

#### Notes
- openssl examples: http://stackoverflow.com/a/18359743
- authenticode examples: http://development.adaptris.net/users/lchan/blog/2013/06/07/signing-windows-installers-on-linux
- gpg examples: http://www.spywarewarrior.com/uiuc/gpg/gpg-com-4.htm#2-3

#### Maven

For an easy way to sign files within the Maven lifecycle, use the [codesign-maven-plugin](https://github.com/rapid7/cert-protector/tree/master/codesign-maven-plugin).

#### TODO
- Use GPGme / openssl ruby rather than shelling out
- Provide some customization options (such as allowing alteration of
  commands)
