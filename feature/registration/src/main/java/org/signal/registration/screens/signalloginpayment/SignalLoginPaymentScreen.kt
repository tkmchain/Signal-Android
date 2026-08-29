/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Buttons
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.signal.registration.test.TestTags

@Composable
fun SignalLoginPaymentScreen(
  state: SignalLoginPaymentState,
  onEvent: (SignalLoginPaymentScreenEvents) -> Unit,
  modifier: Modifier = Modifier
) {
  val dialogMessage = when {
    state.dialogs.networkError -> stringResource(R.string.TkmMailboxRegistration__network_error)
    state.dialogs.unknownError -> stringResource(R.string.TkmMailboxRegistration__unknown_error)
    else -> null
  }
  dialogMessage?.let { message ->
    Dialogs.SimpleMessageDialog(
      message = message,
      dismiss = stringResource(android.R.string.ok),
      onDismiss = {
        onEvent(
          if (state.dialogs.networkError) SignalLoginPaymentScreenEvents.NetworkErrorDialogDismissed
          else SignalLoginPaymentScreenEvents.UnknownErrorDialogDismissed
        )
      }
    )
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag(TestTags.SIGNAL_LOGIN_PAYMENT_SCREEN)
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
      TextButton(
        onClick = { onEvent(SignalLoginPaymentScreenEvents.BackClicked) },
        modifier = Modifier.align(Alignment.Start)
      ) {
        Text(stringResource(R.string.TkmMailboxRegistration__back))
      }

      Spacer(Modifier.height(32.dp))

      Text(
        text = stringResource(R.string.TkmMailboxRegistration__title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(12.dp))

      Text(
        text = if (state.phase == SignalLoginPaymentState.Phase.Mailbox) {
          stringResource(R.string.TkmMailboxRegistration__mailbox_explanation)
        } else {
          stringResource(R.string.TkmMailboxRegistration__code_explanation, state.mailbox)
        },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(Modifier.height(28.dp))

      if (state.phase == SignalLoginPaymentState.Phase.Mailbox) {
        OutlinedTextField(
          value = state.mailbox,
          onValueChange = { onEvent(SignalLoginPaymentScreenEvents.MailboxChanged(it)) },
          enabled = !state.showSpinner,
          label = { Text(stringResource(R.string.TkmMailboxRegistration__mailbox_label)) },
          placeholder = { Text("info@tkm") },
          supportingText = { InlineError(state.inlineError) },
          isError = state.inlineError != null,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
          keyboardActions = KeyboardActions(onNext = { onEvent(SignalLoginPaymentScreenEvents.ContinueClicked) }),
          modifier = Modifier.fillMaxWidth()
        )
      } else {
        OutlinedTextField(
          value = state.verificationCode,
          onValueChange = { onEvent(SignalLoginPaymentScreenEvents.VerificationCodeChanged(it)) },
          enabled = !state.showSpinner,
          label = { Text(stringResource(R.string.TkmMailboxRegistration__code_label)) },
          supportingText = { InlineError(state.inlineError) },
          isError = state.inlineError != null,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { onEvent(SignalLoginPaymentScreenEvents.ContinueClicked) }),
          modifier = Modifier.fillMaxWidth()
        )
      }

      Spacer(Modifier.height(24.dp))

      Buttons.LargePrimary(
        onClick = { onEvent(SignalLoginPaymentScreenEvents.ContinueClicked) },
        enabled = state.isActionEnabled,
        modifier = Modifier.fillMaxWidth()
      ) {
        if (state.showSpinner) {
          CircularProgressIndicator(strokeWidth = 2.dp)
        } else {
          Text(
            stringResource(
              if (state.phase == SignalLoginPaymentState.Phase.Mailbox) R.string.TkmMailboxRegistration__send_code
              else R.string.TkmMailboxRegistration__verify_and_register
            )
          )
        }
      }

      TextButton(onClick = { onEvent(SignalLoginPaymentScreenEvents.LearnMoreClicked) }) {
        Text(stringResource(R.string.TkmMailboxRegistration__learn_more))
      }
    }
  }
}

@Composable
private fun InlineError(error: SignalLoginPaymentState.Error?) {
  val message = when (error) {
    SignalLoginPaymentState.Error.InvalidMailbox -> R.string.TkmMailboxRegistration__invalid_mailbox
    SignalLoginPaymentState.Error.MailboxNotFound -> R.string.TkmMailboxRegistration__mailbox_not_found
    SignalLoginPaymentState.Error.MailboxAlreadyRegistered -> R.string.TkmMailboxRegistration__mailbox_already_registered
    SignalLoginPaymentState.Error.IncorrectCode -> R.string.TkmMailboxRegistration__incorrect_code
    SignalLoginPaymentState.Error.RegistrationRejected -> R.string.TkmMailboxRegistration__registration_rejected
    SignalLoginPaymentState.Error.RateLimited -> R.string.TkmMailboxRegistration__rate_limited
    SignalLoginPaymentState.Error.ServiceUnavailable -> R.string.TkmMailboxRegistration__network_error
    null -> return
  }
  Text(stringResource(message))
}

@AllDevicePreviews
@Composable
private fun TkmMailboxRegistrationPreview() {
  Previews.Preview {
    SignalLoginPaymentScreen(state = SignalLoginPaymentState(clientNonce = "a".repeat(64), mailbox = "info@tkm"), onEvent = {})
  }
}
