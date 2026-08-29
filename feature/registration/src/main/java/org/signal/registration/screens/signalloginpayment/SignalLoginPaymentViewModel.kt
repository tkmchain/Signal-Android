/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.signal.core.ui.compose.EventDrivenViewModel
import org.signal.core.util.logging.Log
import org.signal.libsignal.net.RequestResult
import org.signal.network.api.RegistrationApiV2.RegisterAccountWithoutPhoneNumberError
import org.signal.network.api.RegistrationApiV2.TkmMailboxVerificationError
import org.signal.registration.RegistrationFlowEvent
import org.signal.registration.RegistrationRepository
import org.signal.registration.RestoreDecision
import org.signal.registration.TkmMailbox
import org.signal.registration.screens.util.navigateBack
import java.security.SecureRandom

class SignalLoginPaymentViewModel(
  private val repository: RegistrationRepository,
  private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
) : EventDrivenViewModel<SignalLoginPaymentScreenEvents>(TAG) {

  companion object {
    private val TAG = Log.tag(SignalLoginPaymentViewModel::class)
  }

  private val _state = MutableStateFlow(SignalLoginPaymentState(clientNonce = generateClientNonce()))
  val state: StateFlow<SignalLoginPaymentState> = _state.asStateFlow()

  private val _actions = Channel<SignalLoginPaymentScreenActions>(Channel.BUFFERED)
  val actions: Flow<SignalLoginPaymentScreenActions> = _actions.receiveAsFlow()

  override suspend fun processEvent(event: SignalLoginPaymentScreenEvents) {
    when (event) {
      SignalLoginPaymentScreenEvents.BackClicked -> parentEventEmitter.navigateBack()
      SignalLoginPaymentScreenEvents.LearnMoreClicked -> _actions.trySend(SignalLoginPaymentScreenActions.OpenLearnMoreArticle)
      is SignalLoginPaymentScreenEvents.MailboxChanged -> _state.value = _state.value.copy(mailbox = event.value, inlineError = null)
      is SignalLoginPaymentScreenEvents.VerificationCodeChanged -> {
        _state.value = _state.value.copy(verificationCode = event.value.filter(Char::isDigit).take(12), inlineError = null)
      }
      SignalLoginPaymentScreenEvents.ContinueClicked -> continueRegistration()
      SignalLoginPaymentScreenEvents.NetworkErrorDialogDismissed -> _state.value = _state.value.copy(dialogs = _state.value.dialogs.copy(networkError = false))
      SignalLoginPaymentScreenEvents.UnknownErrorDialogDismissed -> _state.value = _state.value.copy(dialogs = _state.value.dialogs.copy(unknownError = false))
    }
  }

  private suspend fun continueRegistration() {
    if (_state.value.showSpinner) return
    when (_state.value.phase) {
      SignalLoginPaymentState.Phase.Mailbox -> requestMailboxCode()
      SignalLoginPaymentState.Phase.Code -> verifyMailboxAndRegister()
    }
  }

  private suspend fun requestMailboxCode() {
    val mailbox = TkmMailbox.normalize(_state.value.mailbox)
    if (!TkmMailbox.isValid(mailbox)) {
      _state.value = _state.value.copy(mailbox = mailbox, inlineError = SignalLoginPaymentState.Error.InvalidMailbox)
      return
    }

    _state.value = _state.value.copy(mailbox = mailbox, showSpinner = true, inlineError = null)
    when (val result = repository.createTkmMailboxVerificationSession(mailbox, _state.value.clientNonce)) {
      is RequestResult.Success -> _state.value = _state.value.copy(
        mailbox = result.result.mailbox,
        sessionId = result.result.sessionId,
        phase = SignalLoginPaymentState.Phase.Code,
        showSpinner = false
      )
      is RequestResult.NonSuccess -> handleTkmMailboxVerificationError(result.error)
      is RequestResult.RetryableNetworkError -> _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(networkError = true))
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unable to create TKM mailbox verification session", result.cause)
        _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(unknownError = true))
      }
    }
  }

  private suspend fun verifyMailboxAndRegister() {
    val sessionId = _state.value.sessionId ?: return
    _state.value = _state.value.copy(showSpinner = true, inlineError = null)
    when (val verification = repository.submitTkmMailboxVerificationCode(sessionId, _state.value.verificationCode)) {
      is RequestResult.Success -> {
        val token = verification.result.registrationToken
        if (!verification.result.verified || token.isNullOrBlank()) {
          _state.value = _state.value.copy(showSpinner = false, inlineError = SignalLoginPaymentState.Error.IncorrectCode)
          return
        }
        registerAccount(token)
      }
      is RequestResult.NonSuccess -> handleTkmMailboxVerificationError(verification.error)
      is RequestResult.RetryableNetworkError -> _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(networkError = true))
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unable to verify TKM mailbox", verification.cause)
        _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(unknownError = true))
      }
    }
  }

  private suspend fun registerAccount(registrationToken: String) {
    when (val result = repository.registerAccountWithTkmMailbox(_state.value.mailbox, _state.value.clientNonce, registrationToken)) {
      is RequestResult.Success -> {
        val (response, keyMaterial) = result.result
        parentEventEmitter(RegistrationFlowEvent.Registered(keyMaterial.accountEntropyPool, response.storageCapable))
        repository.setPinOptedOut()
        repository.setRestoreDecision(RestoreDecision.NEW_ACCOUNT)
        parentEventEmitter(RegistrationFlowEvent.RegistrationComplete)
      }
      is RequestResult.NonSuccess -> {
        val error = when (result.error) {
          is RegisterAccountWithoutPhoneNumberError.RateLimited -> SignalLoginPaymentState.Error.RateLimited
          else -> SignalLoginPaymentState.Error.RegistrationRejected
        }
        _state.value = _state.value.copy(showSpinner = false, inlineError = error)
      }
      is RequestResult.RetryableNetworkError -> _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(networkError = true))
      is RequestResult.ApplicationError -> {
        Log.w(TAG, "Unable to register TKMChat account", result.cause)
        _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(unknownError = true))
      }
    }
  }

  private fun TkmMailboxVerificationError.toScreenError(): SignalLoginPaymentState.Error = when (this) {
    is TkmMailboxVerificationError.InvalidRequest -> SignalLoginPaymentState.Error.InvalidMailbox
    TkmMailboxVerificationError.IncorrectCode -> SignalLoginPaymentState.Error.IncorrectCode
    TkmMailboxVerificationError.MailboxOrSessionNotFound -> SignalLoginPaymentState.Error.MailboxNotFound
    TkmMailboxVerificationError.MailboxAlreadyRegistered -> SignalLoginPaymentState.Error.MailboxAlreadyRegistered
    is TkmMailboxVerificationError.RateLimited -> SignalLoginPaymentState.Error.RateLimited
    TkmMailboxVerificationError.ServiceUnavailable -> SignalLoginPaymentState.Error.ServiceUnavailable
  }

  private fun handleTkmMailboxVerificationError(error: TkmMailboxVerificationError) {
    if (error == TkmMailboxVerificationError.ServiceUnavailable) {
      _state.value = _state.value.copy(showSpinner = false, dialogs = _state.value.dialogs.copy(networkError = true))
    } else {
      _state.value = _state.value.copy(showSpinner = false, inlineError = error.toScreenError())
    }
  }

  private fun generateClientNonce(): String {
    val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
    return bytes.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
  }

  class Factory(
    private val repository: RegistrationRepository,
    private val parentEventEmitter: (RegistrationFlowEvent) -> Unit
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SignalLoginPaymentViewModel(repository, parentEventEmitter) as T
  }
}
