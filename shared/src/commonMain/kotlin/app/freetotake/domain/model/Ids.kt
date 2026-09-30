// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.model

import kotlin.jvm.JvmInline

@JvmInline value class UserId(val value: String)
@JvmInline value class ItemId(val value: String)
@JvmInline value class ClaimId(val value: String)

/**
 * UTC instant in epoch milliseconds. Kept dependency-free so the rule engine
 * compiles on every KMP target; map to/from kotlinx-datetime at the data layer.
 */
@JvmInline
value class Timestamp(val epochMillis: Long) : Comparable<Timestamp> {
    override fun compareTo(other: Timestamp): Int = epochMillis.compareTo(other.epochMillis)
    operator fun minus(other: Timestamp): Long = epochMillis - other.epochMillis
    fun plusHours(hours: Long): Timestamp = Timestamp(epochMillis + hours * 3_600_000L)
}

fun interface Clock {
    fun now(): Timestamp
}
