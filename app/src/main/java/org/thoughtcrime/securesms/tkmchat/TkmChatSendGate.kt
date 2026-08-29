/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.tkmchat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import java.math.BigInteger
import java.util.Locale

/**
 * Enforces TKMChat network policy before a message is inserted into the local outbox.
 *
 * - No connected TKM peers means no chat traffic is queued.
 * - Group chats are free once peers exist.
 * - Personal chats require the recipient to be a TKMChat account.
 * - If the recipient set a personal-chat price, the sender must complete the payment flow first.
 */
object TkmChatSendGate {
  private val TAG = Log.tag(TkmChatSendGate::class.java)
  private val ZERO = BigInteger.ZERO

  @JvmStatic
  @WorkerThread
  fun allowBeforeSend(context: Context, recipient: Recipient): Boolean {
    val selfMailbox = SignalStore.account.tkmMailbox?.trim()?.lowercase(Locale.US)

    if (selfMailbox.isNullOrBlank()) {
      return true
    }

    val result = try {
      runBlocking {
        enforce(selfMailbox, recipient, context)
      }
    } catch (e: TkmChatSendBlockedException) {
      SendGateResult.Blocked(e.message ?: "TKMChat send blocked.")
    } catch (e: Exception) {
      Log.w(TAG, "TKMChat send policy check failed.", e)
      SendGateResult.Blocked("TKMChat network check failed. Try again when your node is reachable.")
    }

    return when (result) {
      SendGateResult.Allowed -> true
      is SendGateResult.Blocked -> {
        showToast(context, result.reason)
        false
      }
    }
  }

  private suspend fun enforce(selfMailbox: String, recipient: Recipient, context: Context): SendGateResult {
    val peers = AppDependencies.registrationApiV2.getTkmChatPeers().successOrBlock(
      fallback = "Could not reach the TKMChat service."
    )

    if (parseQuantity(peers.peerCount) <= ZERO) {
      throw TkmChatSendBlockedException("No TKM peers are connected. Chat is disabled until the node has peers.")
    }

    if (recipient.isGroup || recipient.isDistributionList) {
      return SendGateResult.Allowed
    }

    val target = resolveRecipientLookup(recipient, context)
      ?: throw TkmChatSendBlockedException("Recipient is not a TKMChat email account.")

    if (target == selfMailbox || target == selfMailbox.substringBefore("@")) {
      return SendGateResult.Allowed
    }

    val account = AppDependencies.registrationApiV2.getTkmChatAccount(target).successOrBlock(
      fallback = "Recipient is not registered on TKMChat."
    )

    val price = account.personalChatPriceWei.toBigIntegerOrNull() ?: ZERO
    if (price > ZERO) {
      throw TkmChatSendBlockedException("This user charges ${formatTkm(price)} TKM before personal chat. Pay their shield2 address before sending.")
    }

    AppDependencies.registrationApiV2.createTkmChat(
      kind = "personal",
      from = selfMailbox,
      participants = listOf(selfMailbox, account.mailbox)
    ).successOrBlock(fallback = "Could not register this TKMChat session.")

    return SendGateResult.Allowed
  }

  private fun resolveRecipientLookup(recipient: Recipient, context: Context): String? {
    val candidates = listOfNotNull(
      recipient.username.orElse(null),
      recipient.email.orElse(null),
      recipient.getDisplayName(context)
    )

    return candidates
      .asSequence()
      .map { it.trim().lowercase(Locale.US) }
      .map { it.removePrefix("@") }
      .firstOrNull { candidate ->
        candidate.matches(Regex("[a-z0-9._-]+@[a-z0-9._-]+")) ||
          candidate.matches(Regex("[a-z0-9._-]{2,64}"))
      }
  }

  private fun parseQuantity(value: String?): BigInteger {
    if (value.isNullOrBlank()) {
      return ZERO
    }

    return try {
      if (value.startsWith("0x", ignoreCase = true)) {
        BigInteger(value.substring(2), 16)
      } else {
        BigInteger(value, 10)
      }
    } catch (_: NumberFormatException) {
      ZERO
    }
  }

  private fun showToast(context: Context, message: String) {
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }
  }

  private fun formatTkm(wei: BigInteger): String {
    val base = BigInteger.TEN.pow(18)
    val whole = wei.divide(base)
    val fraction = wei.mod(base).toString().padStart(18, '0').trimEnd('0')
    return if (fraction.isBlank()) whole.toString() else "$whole.$fraction"
  }

  private fun <T> RequestResult<T, *>.successOrBlock(fallback: String): T {
    return when (this) {
      is RequestResult.Success -> result
      is RequestResult.NonSuccess -> throw TkmChatSendBlockedException(fallback)
      is RequestResult.RetryableNetworkError -> throw TkmChatSendBlockedException(fallback)
      is RequestResult.ApplicationError -> throw TkmChatSendBlockedException(fallback)
    }
  }

  private sealed interface SendGateResult {
    data object Allowed : SendGateResult
    data class Blocked(val reason: String) : SendGateResult
  }
}

class TkmChatSendBlockedException(message: String) : RuntimeException(message)
