/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.tkmchat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal JSON-RPC client for the TKM Phone namespace exposed by `gtkm`.
 *
 * This keeps TKMPhone calls available to Android without carrying wallet
 * passphrases or daemon internals in UI code. Callers must pass already
 * encrypted payloads and locally-produced owner/device signatures.
 */
object TkmPhoneRpcClient {
  private const val DEFAULT_RPC_URL = "https://wallet.tkmchain.site/rpc"
  private val nextId = AtomicLong(1)

  suspend fun status(rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_status")
  }

  suspend fun registeredNumber(number: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_registeredNumber", number)
  }

  suspend fun registeredNumbers(rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_registeredNumbers")
  }

  suspend fun deviceKeys(number: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_deviceKeys", number)
  }

  suspend fun deviceKeySigningHash(number: String, device: String, publicKey: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_deviceKeySigningHash", number, device, publicKey)
  }

  suspend fun sendMessageSigningHash(fromNumber: String, toNumber: String, nonceHex: String, ciphertextHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_sendMessageSigningHash", fromNumber, toNumber, nonceHex, ciphertextHex)
  }

  suspend fun encryptPayload(fromNumber: String, toNumber: String, nonceHex: String, payloadHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_encryptPayload", fromNumber, toNumber, nonceHex, payloadHex)
  }

  suspend fun encryptPayloadForDevices(fromNumber: String, toNumber: String, payloadHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_encryptPayloadForDevices", fromNumber, toNumber, payloadHex)
  }

  suspend fun sendEncryptedMessage(fromNumber: String, toNumber: String, ciphertextHex: String, nonceHex: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_sendEncryptedMessage", fromNumber, toNumber, ciphertextHex, nonceHex, signatureHex)
  }

  suspend fun messagesForNumber(number: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_messagesForNumber", number)
  }

  suspend fun webRTCConfig(rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_webRTCConfig")
  }

  suspend fun startCallSigningHash(fromNumber: String, toNumber: String, offerNonceHex: String, offerCiphertextHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_startCallSigningHash", fromNumber, toNumber, offerNonceHex, offerCiphertextHex)
  }

  suspend fun startCall(fromNumber: String, toNumber: String, offerCiphertextHex: String, offerNonceHex: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_startCall", fromNumber, toNumber, offerCiphertextHex, offerNonceHex, signatureHex)
  }

  suspend fun startCallWithExpiry(fromNumber: String, toNumber: String, offerCiphertextHex: String, offerNonceHex: String, expiresAt: Long, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_startCallWithExpiry", fromNumber, toNumber, offerCiphertextHex, offerNonceHex, expiresAt, signatureHex)
  }

  suspend fun acceptCallSigningHash(callId: Long, answerNonceHex: String, answerCiphertextHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_acceptCallSigningHash", quantity(callId), answerNonceHex, answerCiphertextHex)
  }

  suspend fun acceptCall(callId: Long, answerCiphertextHex: String, answerNonceHex: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_acceptCall", quantity(callId), answerCiphertextHex, answerNonceHex, signatureHex)
  }

  suspend fun rejectCallSigningHash(callId: Long, number: String, reason: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_rejectCallSigningHash", quantity(callId), number, reason)
  }

  suspend fun rejectCall(callId: Long, number: String, reason: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_rejectCall", quantity(callId), number, reason, signatureHex)
  }

  suspend fun endCallSigningHash(callId: Long, number: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_endCallSigningHash", quantity(callId), number)
  }

  suspend fun endCall(callId: Long, number: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_endCall", quantity(callId), number, signatureHex)
  }

  suspend fun callCandidateHash(callId: Long, number: String, nonceHex: String, ciphertextHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_callCandidateHash", quantity(callId), number, nonceHex, ciphertextHex)
  }

  suspend fun callCandidateSigningHash(callId: Long, number: String, nonceHex: String, ciphertextHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_callCandidateSigningHash", quantity(callId), number, nonceHex, ciphertextHex)
  }

  suspend fun addCallCandidate(callId: Long, number: String, ciphertextHex: String, nonceHex: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_addCallCandidate", quantity(callId), number, ciphertextHex, nonceHex, signatureHex)
  }

  suspend fun callCandidateListSigningHash(callId: Long, number: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_callCandidateListSigningHash", quantity(callId), number)
  }

  suspend fun callCandidates(callId: Long, number: String, signatureHex: String, rpcUrl: String = DEFAULT_RPC_URL): JSONObject {
    return call(rpcUrl, "tkmphone_callCandidates", quantity(callId), number, signatureHex)
  }

  private fun quantity(value: Long): String {
    require(value >= 0) { "quantity cannot be negative" }
    return "0x${value.toString(16)}"
  }

  private suspend fun call(rpcUrl: String, method: String, vararg params: Any): JSONObject {
    return withContext(Dispatchers.IO) {
      val body = JSONObject()
        .put("jsonrpc", "2.0")
        .put("id", nextId.getAndIncrement())
        .put("method", method)
        .put("params", JSONArray(params.toList()))
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

      val connection = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 30_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
      }

      try {
        connection.outputStream.use { it.write(body) }
        val response = if (connection.responseCode in 200..299) {
          connection.inputStream
        } else {
          connection.errorStream ?: throw IOException("TKMPhone RPC HTTP ${connection.responseCode}")
        }.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }

        val json = JSONObject(response)
        if (json.has("error") && !json.isNull("error")) {
          throw IOException("TKMPhone RPC $method failed: ${json.getJSONObject("error").optString("message", json.get("error").toString())}")
        }
        JSONObject().put("result", json.opt("result")).put("id", json.opt("id"))
      } finally {
        connection.disconnect()
      }
    }
  }
}
