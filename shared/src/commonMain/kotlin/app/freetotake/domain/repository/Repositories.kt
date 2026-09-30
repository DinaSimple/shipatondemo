// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.domain.repository

import app.freetotake.domain.model.Claim
import app.freetotake.domain.model.ClaimId
import app.freetotake.domain.model.DomainResult
import app.freetotake.domain.model.Item
import app.freetotake.domain.model.ItemId
import app.freetotake.domain.model.Session
import app.freetotake.domain.model.Timestamp
import kotlinx.coroutines.flow.Flow

/**
 * Data-layer contracts. Supabase implementations arrive with each flow (1.x);
 * every mutating call maps 1:1 to a server RPC that re-validates [app.freetotake.domain.rules.ClaimRules].
 */
interface AuthRepository {
    val session: Flow<Session>
    suspend fun signOut()
}

interface ItemRepository {
    /** Public feed — works for guests (RLS allows anon SELECT on AVAILABLE/RESERVED items). */
    fun feed(): Flow<List<Item>>
    suspend fun get(id: ItemId): Item?
    suspend fun myItems(): List<Item>
}

interface ClaimRepository {
    suspend fun myClaims(): List<Claim>
    suspend fun claimsForItem(itemId: ItemId): List<Claim>
    suspend fun submit(itemId: ItemId, message: String?): DomainResult<Claim>
    suspend fun cancel(claimId: ClaimId): DomainResult<Claim>
    suspend fun approve(claimId: ClaimId, pickupAt: Timestamp): DomainResult<Claim>
    suspend fun reject(claimId: ClaimId): DomainResult<Claim>
    suspend fun complete(claimId: ClaimId): DomainResult<Claim>
}
