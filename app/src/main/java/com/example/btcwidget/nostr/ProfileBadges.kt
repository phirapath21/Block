package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Filter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Badges (NIP-58) people show on their profiles, checked against the issuers' awards. */
class ProfileBadges internal constructor(private val repo: NostrRepository) {
    /** Badges [pubkey] shows on their profile, verified against the issuers' awards. */
    suspend fun of(pubkey: String): List<com.example.btcwidget.nostr.core.Badges.Definition> {
        val profile = repo.queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(com.example.btcwidget.nostr.core.Badges.PROFILE), tags = mapOf("d" to listOf(com.example.btcwidget.nostr.core.Badges.PROFILE_D)), limit = 2)))
            .maxByOrNull { it.createdAt }
        val shown = com.example.btcwidget.nostr.core.Badges.shown(profile).take(30)
        if (shown.isEmpty()) return emptyList()
        val (awards, definitions) = coroutineScope {
            val a = async { repo.queryEverywhere(listOf(Filter(ids = shown.map { it.second }))) }
            val d = async {
                repo.queryEverywhere(listOf(Filter(
                    authors = shown.map { it.first.issuer }.distinct(), kinds = listOf(com.example.btcwidget.nostr.core.Badges.DEFINITION),
                    tags = mapOf("d" to shown.map { it.first.id }.distinct())
                )))
            }
            a.await() to d.await()
        }
        val awardById = awards.associateBy { it.id }
        val defs = definitions.mapNotNull { com.example.btcwidget.nostr.core.Badges.Definition.of(it) }.groupBy { it.ref }.mapValues { it.value.first() }
        return shown.mapNotNull { (ref, awardId) ->
            val award = awardById[awardId] ?: return@mapNotNull null
            if (!com.example.btcwidget.nostr.core.Badges.isValidAward(award, ref, pubkey)) null else defs[ref]
        }
    }
}
