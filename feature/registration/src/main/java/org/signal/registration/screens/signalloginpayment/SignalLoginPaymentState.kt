/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import org.signal.core.util.censor
import org.signal.registration.TkmMailbox

data class SignalLoginPaymentState(
  val clientNonce: String = "",
  val mailbox: String = "",
  val verificationCode: String = "",
  val sessionId: String? = null,
  val phase: Phase = Phase.Mailbox,
  val showSpinner: Boolean = false,
  val inlineError: Error? = null,
  val dialogs: Dialogs = Dialogs()
) {
  val isActionEnabled: Boolean
    get() = clientNonce.length == 64 && !showSpinner && when (phase) {
      Phase.Mailbox -> TkmMailbox.isValid(mailbox)
      Phase.Code -> verificationCode.length in 6..12 && verificationCode.all(Char::isDigit) && sessionId != null
    }

  override fun toString(): String {
    return "SignalLoginPaymentState(clientNonce=${clientNonce.censor()}, mailbox=${mailbox.censor()}, verificationCode=${verificationCode.censor()}, sessionId=${sessionId?.censor()}, phase=$phase, showSpinner=$showSpinner, inlineError=$inlineError, dialogs=$dialogs)"
  }

  enum class Phase { Mailbox, Code }

  enum class Error {
    InvalidMailbox,
    MailboxNotFound,
    MailboxAlreadyRegistered,
    IncorrectCode,
    RegistrationRejected,
    RateLimited,
    ServiceUnavailable
  }

  data class Dialogs(
    val networkError: Boolean = false,
    val unknownError: Boolean = false
  )
}
