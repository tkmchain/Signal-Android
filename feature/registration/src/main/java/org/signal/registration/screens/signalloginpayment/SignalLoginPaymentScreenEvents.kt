/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

sealed interface SignalLoginPaymentScreenEvents {
  data object BackClicked : SignalLoginPaymentScreenEvents
  data object LearnMoreClicked : SignalLoginPaymentScreenEvents
  data class MailboxChanged(val value: String) : SignalLoginPaymentScreenEvents
  data class VerificationCodeChanged(val value: String) : SignalLoginPaymentScreenEvents
  data object ContinueClicked : SignalLoginPaymentScreenEvents
  data object NetworkErrorDialogDismissed : SignalLoginPaymentScreenEvents
  data object UnknownErrorDialogDismissed : SignalLoginPaymentScreenEvents
}
