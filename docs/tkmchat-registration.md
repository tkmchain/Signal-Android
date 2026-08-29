# TKMChat mailbox registration

TKMChat uses an existing TKMChain EmailVM mailbox as the human-readable account identity. It does not encode a mailbox into a fake telephone number and it does not create a PNI.

## User flow

1. The user purchases and configures an EmailVM mailbox through the TKMChain domain system, including an encryption key.
2. In TKMChat, the user enters the canonical mailbox, for example `info@tkm`.
3. The TKMChat service resolves the mailbox from a chain-ID `8979` node using `tkmdomain_mailbox` and rejects missing, malformed, or unkeyed mailboxes.
4. The service generates a cryptographically random one-time code and asks GTKm to deliver it into the target EmailVM mailbox through `emailvm_deliverOTP`. The REST response and application logs must never contain the code.
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
  "tkmRegistrationToken":"opaque-single-use-token",
  "tkmShield2PaymentCode":"optional-tkmshield2-payment-code"
}
```

`pniIdentityKey`, `pniSignedPreKey`, `pniPqLastResortPreKey`, `pniRegistrationId`, phone discoverability, and E.164 are absent. The response must return an ACI with null/absent `number` and `pni`.

## Required security controls

- Generate codes with a CSPRNG; store only a keyed hash of each code.
- Deliver codes through GTKm RPC `emailvm_deliverOTP`; the daemon must already expose the `emailvm` RPC namespace and be upgraded to a release that includes this method.
- Expire sessions and tokens quickly, make tokens single-use, and rotate them after every successful check.
- Bind the token to the canonical mailbox, client nonce, and hash of submitted ACI public registration material.
- Rate-limit by mailbox, source network, device attestation signal, and session.
- Check final chain state again when consuming the token to close ownership-transfer races.
- Require TLS, reject redirects to untrusted origins, and never expose registration tokens in logs or URLs.
- Keep EmailVM private keys and all TKMChat ACI private keys client-side.
- Enforce a unique database constraint on canonical mailbox and its on-chain registry hash.

### Account lookup and TKM network metadata

The service stores registration state in a backend database under `TKMCHAT_REG_DATA_DIR`. The current reference service uses an atomic JSON database so it does not need native SQLite modules on small VPS hosts.

`GET /v1/tkmchat/account/{mailbox}` returns the public TKMChat account binding:

```json
{
  "aci": "uuid",
  "mailbox": "info@tkm",
  "shield2PaymentCode": "tkmshield2...",
  "createdAt": 1787821200,
  "updatedAt": 1787821200
}
```

`PUT /v1/tkmchat/account/{mailbox}` can update the public shield2 payment code metadata:

```json
{"tkmShield2PaymentCode":"tkmshield2..."}
```

`GET /v1/tkmchat/peers` returns the chain ID, public RPC/prover URLs, configured known GTKm peers, and best-effort local peer count:

```json
{
  "chainId": 8979,
  "rpcUrl": "https://wallet.tkmchain.site/rpc",
  "proverUrl": "https://wallet.tkmchain.site/prover",
  "peers": [],
  "peerCount": "0x3"
}
```

Set `TKMCHAT_KNOWN_PEERS` to a comma-separated list of enodes or peer URLs when deploying.

### Shield2 payment-before-chat

Each user should publish their own shield2 payment code before payment-gated personal chat is enforced. The registration service stores and serves `tkmShield2PaymentCode` and `personalChatPriceWei`.

Personal chat policy:

- one-to-one chat may require payment to the recipient before the chat is created;
- group chat is always free;
- if the TKM node has zero peers, chat creation and message persistence return `503`;
- payment proof is represented by `paymentTxHash`;
- the backend must not generate shield2 addresses for users, because that would create server-owned spend keys.

Chat/message persistence endpoints:

```http
POST /v1/tkmchat/chats
GET  /v1/tkmchat/chats/{chatId}/messages
POST /v1/tkmchat/messages
```

Create a free group chat:

```json
{
  "kind": "group",
  "from": "alice@tkm",
  "participants": ["alice@tkm", "bob@tkm", "carol@tkm"],
  "title": "Team"
}
```

Create a personal chat after payment:

```json
{
  "kind": "personal",
  "from": "alice@tkm",
  "participants": ["alice@tkm", "bob@tkm"],
  "paymentTxHash": "0x..."
}
```

Persist an encrypted message:

```json
{
  "chatId": "chat-id",
  "from": "alice@tkm",
  "to": "bob@tkm",
  "ciphertext": "0x...",
  "nonce": "0x...",
  "txHash": "0x..."
}
```

### Android send enforcement

TKMChat Android enforces the network policy before inserting a message into the local outbox:

- if the local account has no `tkmMailbox`, the normal legacy Signal path is left unchanged;
- if `GET /v1/tkmchat/peers` reports zero connected GTKm peers, the app blocks the send and shows a local warning;
- group and distribution-list messages are free once peers exist;
- one-to-one messages must resolve the recipient as a TKMChat account;
- if the recipient account has `personalChatPriceWei > 0`, the app blocks the send until a shield2 payment transaction hash is available.

The Android app does not upload plaintext message bodies to the TKMChat service. The `/v1/tkmchat/messages` endpoint is only for encrypted payloads or transaction-backed message records.

## GTKm RPC dependency

Mailbox existence, owner, registry hash, and encryption-key publication are durable chain facts and are queryable through GTKm. The registration bridge also depends on `emailvm_deliverOTP` so the daemon performs the actual mailbox delivery. OTP sessions, hashes, registration tokens, and TKMChat account bindings still belong to the TKMChat service and must not become consensus state.
