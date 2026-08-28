# TKMChat Android releases

Release APKs are built on GitHub Actions so the production build does not consume the TKMChain VPS. Push a tag matching `tkmchat-v*`, for example `tkmchat-v1.0.0`.

## Required GitHub secrets

- `TKMCHAT_RELEASE_KEYSTORE_BASE64`: base64 of the stable Android release keystore
- `TKMCHAT_RELEASE_STORE_PASSWORD`
- `TKMCHAT_RELEASE_KEY_ALIAS`
- `TKMCHAT_RELEASE_KEY_PASSWORD`
- `GRADLE_ENCRYPTION_KEY`: optional but recommended for encrypted configuration-cache reuse

Back up the keystore and passwords offline. Losing this key prevents upgrade installation over an existing TKMChat release. Never commit it to Git.

## Optional GitHub repository variables

The workflow accepts `TKMCHAT_SERVICE_URL`, `TKMCHAT_STORAGE_URL`, `TKMCHAT_CDN_URL`, `TKMCHAT_CDN2_URL`, `TKMCHAT_CDN3_URL`, `TKMCHAT_CDSI_URL`, `TKMCHAT_SVR2_URL`, `TKMCHAT_SFU_URL`, `TKMCHAT_STATUS_HOST`, `TKMCHAT_CONTENT_PROXY_HOST`, `TKMCHAT_CAPTCHA_URL`, and `TKMCHAT_RECAPTCHA_URL`. TKMChain host defaults are compiled when an endpoint variable is unset or empty. The GitHub flavor defaults `TKMCHAT_SERVICE_URL` to `https://wallet.tkmchain.site`; serve `/v1/tkmchat/` from that host or override the variable before tagging.

The TKMChat server deployment must generate and publish these cryptographic values as GitHub repository variables: `TKMCHAT_ZKGROUP_SERVER_PUBLIC_PARAMS`, `TKMCHAT_GENERIC_SERVER_PUBLIC_PARAMS`, `TKMCHAT_BACKUP_SERVER_PUBLIC_PARAMS`, `TKMCHAT_UNIDENTIFIED_SENDER_TRUST_ROOT`, and `TKMCHAT_SVR2_MRENCLAVE`. The release workflow refuses to publish if any is absent, preventing an apparently independent APK from accidentally shipping Signal production server parameters.

The checked-in TKMChat Firebase app and sender IDs come from the project's `google-services.json` for package `site.tkmchain.chat`. `TKMCHAT_FIREBASE_APP_ID` and `TKMCHAT_FIREBASE_SENDER_ID` may override them for a replacement Firebase project. Add `TKMCHAT_FIREBASE_WEB_CLIENT_ID` only when that project has a web OAuth client; TKMChat explicitly uses an empty value otherwise. The TKMChat service—not Signal's service—must hold the corresponding Firebase server credential.

## Publish

```bash
git tag -s tkmchat-v1.0.0 -m "TKMChat 1.0.0"
git push origin tkmchat-v1.0.0
```

The workflow builds `githubProdRelease` with `--max-workers=2`, signs each ABI and universal APK with the same stable key, verifies signatures, creates `SHA256SUMS`, and publishes the artifacts to a GitHub release.
