# TKMChat mailbox registration

TKMChat uses an existing TKMChain EmailVM mailbox as the human-readable account identity. It does not encode a mailbox into a fake telephone number and it does not create a PNI.

## User flow

1. The user purchases and configures an EmailVM mailbox through the TKMChain domain system, including an encryption key.
2. In TKMChat, the user enters the canonical mailbox, for example `info@tkm`.
3. The TKMChat service resolves the mailbox from a chain-ID `8979` node using `tkmdomain_mailbox` and rejects missing, malformed, or unkeyed mailboxes.
4. The service generates a cryptographically random one-time code and delivers it as an encrypted EmailVM message. The RPC response and application logs must never contain the code.
5. The user enters the code in TKMChat. The service returns a short-lived, single-use registration token.
6. The app locally creates its ACI identity key, signed prekey, Kyber last-resort prekey, account entropy pool, profile key, and service password.
7. The app submits the token, canonical mailbox, and public registration material. The service atomically consumes the token and permanently binds the mailbox to the new ACI.

## Service API contract

All endpoints are served by the TKMChat messaging service configured as `TKMCHAT_SERVICE_URL`. The GitHub flavor defaults to `https://wallet.tkmchain.site` so first-release APKs do not depend on a separate `chat.tkmchain.site` DNS record. Put the registration service behind nginx at `/v1/tkmchat/` on that host, or set `TKMCHAT_SERVICE_URL` to another HTTPS origin before building.

Example nginx route:

```nginx
location /v1/tkmchat/ {
    proxy_pass http://127.0.0.1:8791/v1/tkmchat/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

### Create a challenge

`POST /v1/tkmchat/verification/session`

```json
{"mailbox":"info@tkm","clientNonce":"64-lowercase-hex-characters"}
```

Success:

```json
{
  "sessionId":"opaque-random-id",
  "mailbox":"info@tkm",
  "expiresAt":1787821200,
  "verified":false,
  "retryAfterSeconds":60
}
```

### Submit the code

`PUT /v1/tkmchat/verification/session/{sessionId}/code`

```json
{"code":"123456"}
```

Success:

```json
{
  "sessionId":"opaque-random-id",
  "mailbox":"info@tkm",
  "expiresAt":1787821200,
  "verified":true,
  "registrationToken":"opaque-single-use-token"
}
```

### Register the account

`POST /v1/tkmchat/registration` uses the same ACI registration material as Signal's numberless registration, with these additional body properties:

```json
{
  "tkmMailbox":"info@tkm",
  "tkmClientNonce":"64-lowercase-hex-characters",
  "tkmRegistrationToken":"opaque-single-use-token"
}
```

`pniIdentityKey`, `pniSignedPreKey`, `pniPqLastResortPreKey`, `pniRegistrationId`, phone discoverability, and E.164 are absent. The response must return an ACI with null/absent `number` and `pni`.

## Required security controls

- Generate codes with a CSPRNG; store only a keyed hash of each code.
- Expire sessions and tokens quickly, make tokens single-use, and rotate them after every successful check.
- Bind the token to the canonical mailbox, client nonce, and hash of submitted ACI public registration material.
- Rate-limit by mailbox, source network, device attestation signal, and session.
- Check final chain state again when consuming the token to close ownership-transfer races.
- Require TLS, reject redirects to untrusted origins, and never expose registration tokens in logs or URLs.
- Keep EmailVM private keys and all TKMChat ACI private keys client-side.
- Enforce a unique database constraint on canonical mailbox and its on-chain registry hash.

## Why GTKm needs no new RPC

Mailbox existence, owner, registry hash, and encryption-key publication are durable chain facts and are already queryable. OTPs and bearer tokens are temporary authentication state belonging to the messaging service. Putting them in GTKm would expand the node attack surface, leak sensitive metadata, and incorrectly turn authentication into consensus state.
