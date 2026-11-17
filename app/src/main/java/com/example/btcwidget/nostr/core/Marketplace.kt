package com.example.btcwidget.nostr.core

import org.json.JSONObject
import java.util.Locale

/** An asking price, as written by the seller: [amount] in [currency] (SATS, BTC, USD, THB…). */
data class Price(val amount: Double, val currency: String, val frequency: String? = null)

/**
 * Something for sale on Nostr: a NIP-99 classified listing (kind 30402, as Shopstr and Plebeian
 * Market publish) or, read-only, a NIP-15 product (kind 30018). One shape for both, so the
 * store shows them side by side.
 */
data class Listing(
    val event: Event,
    val identifier: String,
    val title: String,
    val summary: String?,
    val description: String,
    val images: List<String>,
    val price: Price?,
    val location: String?,
    val categories: List<String>,
    val sold: Boolean,
    val shipping: String?,
    val publishedAt: Long
) {
    val pubkey get() = event.pubkey
    val kind get() = event.kind
    /** "kind:pubkey:d", the listing's address (stable across edits). */
    val address get() = "$kind:$pubkey:$identifier"
    val editable get() = kind == Marketplace.CLASSIFIED
    fun naddr(relays: List<String> = emptyList()) = Nip19.naddr(kind, pubkey, identifier, relays)
}

object Marketplace {
    /** NIP-99 classified listing (active). */
    const val CLASSIFIED = 30402
    /** NIP-99 draft listing (never shown in the store). */
    const val CLASSIFIED_DRAFT = 30403
    /** NIP-15 product (Plebeian Market, LNbits Nostrmarket): read only. */
    const val NIP15_PRODUCT = 30018

    val KINDS = listOf(CLASSIFIED, NIP15_PRODUCT)
    const val SATS_PER_BTC = 100_000_000L



    /** The listing [e] describes, or null when it isn't one (or carries nothing to show). */
    fun parse(e: Event): Listing? = when (e.kind) {
        CLASSIFIED -> parseClassified(e)
        NIP15_PRODUCT -> parseNip15(e)
        else -> null
    }

    private fun parseClassified(e: Event): Listing? {
        fun tag(name: String) = e.firstTag(name)?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val title = tag("title") ?: return null
        val price = e.firstTag("price")?.let { t ->
            val amount = t.getOrNull(1)?.replace(",", "")?.toDoubleOrNull() ?: return@let null
            Price(amount, normalizeCurrency(t.getOrNull(2) ?: "SATS"), t.getOrNull(3)?.takeIf { it.isNotBlank() })
        }
        val images = e.tags.filter { it.size >= 2 && it[0] == "image" }.map { it[1].trim() }
            .filter { it.startsWith("https://") }.distinct()
        val status = tag("status")?.lowercase()
        val shipping = e.firstTag("shipping")?.drop(1)?.filter { it.isNotBlank() }?.joinToString(" ")?.takeIf { it.isNotBlank() }
        return Listing(
            event = e,
            identifier = e.firstTag("d")?.get(1).orEmpty(),
            title = title,
            summary = tag("summary"),
            description = e.content.trim(),
            images = images,
            price = price,
            location = tag("location"),
            categories = e.tagValues("t").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            sold = status == "sold",
            shipping = shipping,
            publishedAt = tag("published_at")?.toLongOrNull() ?: e.createdAt
        )
    }

    private fun parseNip15(e: Event): Listing? = runCatching {
        val o = JSONObject(e.content)
        val name = o.optString("name").trim().takeIf { it.isNotEmpty() } ?: return null
        val images = o.optJSONArray("images")?.let { a -> (0 until a.length()).map { a.optString(it).trim() } }.orEmpty()
            .filter { it.startsWith("https://") }.distinct()
        val price = if (o.has("price")) Price(o.optDouble("price"), normalizeCurrency(o.optString("currency", "SATS"))) else null
        val quantity = if (o.has("quantity") && !o.isNull("quantity")) o.optInt("quantity", -1) else -1
        Listing(
            event = e,
            identifier = e.firstTag("d")?.get(1) ?: o.optString("id"),
            title = name,
            summary = null,
            description = o.optString("description").trim(),
            images = images,
            price = price?.takeIf { !it.amount.isNaN() },
            location = null,
            categories = e.tagValues("t").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            sold = quantity == 0,
            shipping = null,
            publishedAt = e.createdAt
        )
    }.getOrNull()

    /** "sat", "Sats", "SATS" → SATS; "btc" → BTC; anything else upper-cased. */
    fun normalizeCurrency(raw: String): String = when (val c = raw.trim().uppercase(Locale.US)) {
        "SAT", "SATS", "SATOSHI", "SATOSHIS", "MSAT" -> if (c == "MSAT") "MSAT" else "SATS"
        "XBT" -> "BTC"
        "" -> "SATS"
        else -> c
    }

    /**
     * The price in sats, converting fiat at the given spot prices (BTC in USD and THB).
     * Null for a currency we have no rate for.
     */
    fun toSats(price: Price, btcUsd: Double, btcThb: Double): Long? {
        val btc = when (price.currency) {
            "SATS" -> return price.amount.toLong()
            "MSAT" -> return (price.amount / 1000).toLong()
            "BTC" -> price.amount
            "USD" -> if (btcUsd > 0) price.amount / btcUsd else return null
            "THB" -> if (btcThb > 0) price.amount / btcThb else return null
            else -> return null
        }
        return Math.round(btc * SATS_PER_BTC)
    }

    /** [sats] in [currency] ("USD" or "THB"), or null without a rate. */
    fun toFiat(sats: Long, currency: String, btcUsd: Double, btcThb: Double): Double? {
        val rate = if (currency == "THB") btcThb else btcUsd
        return if (rate > 0) sats.toDouble() / SATS_PER_BTC * rate else null
    }

    /** Sats until a tenth of a bitcoin ("63,800 sats"), then BTC ("₿0.1250"). */
    fun formatSats(sats: Long): String =
        if (sats >= SATS_PER_BTC / 10) "₿" + String.format(Locale.US, "%.4f", sats.toDouble() / SATS_PER_BTC).trimEnd('0').trimEnd('.')
        else String.format(Locale.US, "%,d sats", sats)

    /** "฿1,500" / "$12.50". */
    fun formatFiat(amount: Double, currency: String): String {
        val symbol = if (currency == "THB") "฿" else "$"
        return if (amount >= 100) symbol + String.format(Locale.US, "%,.0f", amount) else symbol + String.format(Locale.US, "%,.2f", amount)
    }

    /** How the seller wrote the price, for currencies we can't convert ("€25"). */
    fun formatRaw(price: Price): String = when (price.currency) {
        "SATS" -> formatSats(price.amount.toLong())
        "BTC" -> formatSats(Math.round(price.amount * SATS_PER_BTC))
        "USD", "THB" -> formatFiat(price.amount, price.currency)
        else -> String.format(Locale.US, "%,.2f", price.amount).removeSuffix(".00") + " " + price.currency
    }

    /** True when every word of [query] is in the title, summary, description, location or tags. */
    fun matches(l: Listing, query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.removePrefix("#") }
        if (words.isEmpty()) return true
        val hay = (listOfNotNull(l.title, l.summary, l.description, l.location) + l.categories).joinToString(" ").lowercase()
        return words.all { it in hay }
    }

    /** Newest version of each listing (by address), drafts and empties dropped. */
    fun latest(events: List<Event>): List<Listing> =
        events.filter { it.kind in KINDS }
            .groupBy { "${it.kind}:${it.pubkey}:${it.firstTag("d")?.get(1).orEmpty()}" }
            .mapNotNull { (_, versions) -> parse(versions.maxBy { it.createdAt }) }

    /** A NIP-99 listing ready to sign. [identifier] keeps edits at the same address. */
    fun build(
        pubkey: String,
        identifier: String,
        title: String,
        summary: String?,
        description: String,
        images: List<String>,
        price: Price,
        location: String?,
        categories: List<String>,
        sold: Boolean,
        shipping: String?,
        publishedAt: Long,
        now: Long = System.currentTimeMillis() / 1000
    ): UnsignedEvent {
        val tags = ArrayList<List<String>>()
        tags += listOf("d", identifier)
        tags += listOf("title", title.trim())
        summary?.trim()?.takeIf { it.isNotEmpty() }?.let { tags += listOf("summary", it) }
        tags += listOf("published_at", publishedAt.toString())
        location?.trim()?.takeIf { it.isNotEmpty() }?.let { tags += listOf("location", it) }
        val amount = if (price.amount % 1.0 == 0.0) price.amount.toLong().toString() else price.amount.toBigDecimal().stripTrailingZeros().toPlainString()
        tags += listOfNotNull("price", amount, price.currency, price.frequency)
        images.forEach { tags += listOf("image", it) }
        categories.map { it.trim().lowercase().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().forEach { tags += listOf("t", it) }
        tags += listOf("status", if (sold) "sold" else "active")
        shipping?.trim()?.takeIf { it.isNotEmpty() }?.let { tags += listOf("shipping", it) }
        return UnsignedEvent(pubkey, now, CLASSIFIED, tags, description.trim())
    }
}
