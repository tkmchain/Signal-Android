/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

/**
 * Guards flags in [Environment] that are only meant to be flipped on for local development.
 */
class EnvironmentTest {

  @Test
  fun `TKM mailbox registration is enabled`() {
    assertThat(Environment.PHONENUMBERLESS_REGISTRATION).isTrue()
  }

  @Test
  fun `MOCK_PHONE_NUMBERLESS_REGISTRATION is disabled`() {
    assertThat(Environment.MOCK_PHONE_NUMBERLESS_REGISTRATION).isFalse()
  }
}
