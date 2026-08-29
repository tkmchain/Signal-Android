# TKMChat Android

TKMChat is an AGPLv3 Android messenger fork based on Signal Android. New accounts use a registered TKMChain EmailVM mailbox such as `info@tkm` or `alice@john` instead of a telephone number.

The Android package is `site.tkmchain.chat`, so TKMChat can be installed alongside Signal. Internal Java/Kotlin package names remain unchanged to keep upstream security fixes practical to merge.

## Current integration

- The registration client normalizes and validates TKM mailbox names.
- The TKMChat service sends a one-time confirmation code to the registered encrypted EmailVM mailbox.
- A successful challenge produces a short-lived, single-use token used to create an ACI-only account with PQ prekeys and no E.164 or PNI identity.
- GitHub tags matching `tkmchat-v*` build, sign, verify, checksum, and publish release APKs on GitHub Actions with two Gradle workers.
- The GitHub build flavor uses TKMChat hosts and cannot fall back to Signal domain-fronting infrastructure.

See [docs/tkmchat-registration.md](docs/tkmchat-registration.md) for the server contract and security rules, and [docs/releases.md](docs/releases.md) for release setup.

## Important deployment requirement

This repository is the Android client. A compatible TKMChat messaging service must implement the mailbox verification and numberless registration endpoints before the APK can register or exchange messages. GTKm supplies the canonical EmailVM mailbox registry through `tkmdomain_mailbox` and delivers verification codes through `emailvm_deliverOTP`; OTP session hashes, registration tokens, account bindings, and private keys must remain outside consensus.

## Upstream and license

TKMChat is derived from [Signal Android](https://github.com/signalapp/Signal-Android). Signal is a trademark of Signal Messenger, LLC; TKMChat is an independent project and is not endorsed by Signal.

Copyright notices in upstream source files are retained. New TKMChat code is licensed under the GNU AGPLv3. See [LICENSE](LICENSE).
