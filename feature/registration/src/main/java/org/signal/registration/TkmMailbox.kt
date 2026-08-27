/*
 * Copyright 2026 TKMChain contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.registration

import java.util.Locale

/** Canonical validation shared by the TKMChat registration UI and network layer. */
object TkmMailbox {
  private val LOCAL_PART = Regex("^[a-z0-9](?:[a-z0-9._-]{0,62}[a-z0-9])?$")
  private val DOMAIN = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
  private val CLIENT_NONCE = Regex("^[0-9a-f]{64}$")

  fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT)

  fun isValid(value: String): Boolean {
    val canonical = normalize(value)
    if (canonical.length > 128 || canonical.count { it == '@' } != 1) return false
    val (localPart, domain) = canonical.split('@', limit = 2)
    return LOCAL_PART.matches(localPart) && DOMAIN.matches(domain)
  }

  fun isValidClientNonce(value: String): Boolean = CLIENT_NONCE.matches(value)
}
