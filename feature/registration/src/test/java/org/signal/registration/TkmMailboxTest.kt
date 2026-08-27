/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class TkmMailboxTest {
  @Test
  fun `normalizes case and whitespace`() {
    assertThat(TkmMailbox.normalize(" Info@TKM ")).isEqualTo("info@tkm")
  }

  @Test
  fun `accepts EmailVM mailbox formats`() {
    assertThat(TkmMailbox.isValid("info@tkm")).isTrue()
    assertThat(TkmMailbox.isValid("alice.smith@john")).isTrue()
  }

  @Test
  fun `rejects ambiguous or unsafe mailbox formats`() {
    assertThat(TkmMailbox.isValid("info@tkm@example")).isFalse()
    assertThat(TkmMailbox.isValid("@tkm")).isFalse()
    assertThat(TkmMailbox.isValid("info@john.example")).isFalse()
    assertThat(TkmMailbox.isValid("info+tag@tkm")).isFalse()
  }

  @Test
  fun `accepts only 256-bit lowercase hexadecimal client nonces`() {
    assertThat(TkmMailbox.isValidClientNonce("a".repeat(64))).isTrue()
    assertThat(TkmMailbox.isValidClientNonce("A".repeat(64))).isFalse()
    assertThat(TkmMailbox.isValidClientNonce("a".repeat(63))).isFalse()
  }
}
