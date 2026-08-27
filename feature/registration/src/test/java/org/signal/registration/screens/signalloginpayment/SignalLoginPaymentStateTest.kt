/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration.screens.signalloginpayment

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class SignalLoginPaymentStateTest {
  private val nonce = "a".repeat(64)

  @Test
  fun `mailbox phase requires a valid EmailVM mailbox`() {
    assertThat(SignalLoginPaymentState(clientNonce = nonce, mailbox = "info@tkm").isActionEnabled).isTrue()
    assertThat(SignalLoginPaymentState(clientNonce = nonce, mailbox = "+12025550123").isActionEnabled).isFalse()
  }

  @Test
  fun `code phase requires numeric code and session`() {
    val ready = SignalLoginPaymentState(
      clientNonce = nonce,
      mailbox = "info@tkm",
      verificationCode = "123456",
      sessionId = "session",
      phase = SignalLoginPaymentState.Phase.Code
    )
    assertThat(ready.isActionEnabled).isTrue()
    assertThat(ready.copy(verificationCode = "12ab56").isActionEnabled).isFalse()
    assertThat(ready.copy(sessionId = null).isActionEnabled).isFalse()
  }
}
