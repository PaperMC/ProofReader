# ProofReader

Collection of services to help with "proofreading"/reviewing Paper PRs.

* builds PRs and provides artifacts for testing (api, dev bundle, paperclip jar)
* provides patched source code in private repo (with diffs.dev links) for easy browsing
* automated rebasing of PRs

## Setup

* create github app with issues and PRs read and write access
* create a private key
* install app into desired repos (and copy the installation id from the url)
* configure the application.yml:
```yml
proofreader:
  sourceRepo:
    owner: PaperMC
    name: Paper
  targetRepo:
    owner: PaperMC
    name: ProofReading
  installationId: 12345
  clientId: AbCd123
  privateKey: |
  -----BEGIN RSA PRIVATE KEY-----
```
* optionally the paper shared build cache can be utilized by providing credentials:
```yml
  buildCacheUser: USER_
  buildCachePassword: xxxx
```
