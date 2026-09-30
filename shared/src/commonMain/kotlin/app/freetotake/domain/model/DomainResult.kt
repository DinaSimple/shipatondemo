// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Yol (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.model

sealed interface DomainResult<out T> {
    data class Ok<T>(val value: T) : DomainResult<T>
    data class Err(val error: DomainError) : DomainResult<Nothing>
}

enum class DomainError {
    ALREADY_REJECTED,
    PICKUP_PASSED,
    LOGIN_REQUIRED,
    NOT_ITEM_OWNER,
    NOT_CLAIM_OWNER,
    CANNOT_CLAIM_OWN_ITEM,
    ITEM_NOT_CLAIMABLE,
    ALREADY_HAS_ACTIVE_CLAIM,
    ITEM_ALREADY_RESERVED,
    INVALID_TRANSITION,
    PICKUP_TIME_IN_PAST,
    CLAIM_ITEM_MISMATCH,
}

fun <T> DomainResult<T>.getOrNull(): T? = (this as? DomainResult.Ok<T>)?.value
fun <T> DomainResult<T>.errorOrNull(): DomainError? = (this as? DomainResult.Err)?.error
