package com.example.btcwidget.nostr

import com.example.btcwidget.PriceData
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Marketplace
import com.example.btcwidget.nostr.core.Price
import com.example.btcwidget.ui.social.formatListingPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceTest {

    private val samplePubkey = "a".repeat(64)

    @Test
    fun buildAndParseClassifiedListing_roundTripsAllFields() {
        val unsigned = Marketplace.build(
            pubkey = samplePubkey,
            identifier = "bitaxe-supra-01",
            title = "Bitaxe Supra 600 GH/s",
            summary = "Open-source solo Bitcoin miner",
            description = "Full kit with PSU and stand.",
            images = listOf("https://nostr.build/img1.jpg", "https://nostr.build/img2.jpg"),
            price = Price(150_000.0, "SATS"),
            location = "Bangkok, TH",
            categories = listOf("Hardware", "#Bitcoin"),
            sold = false,
            shipping = "Free in Thailand",
            publishedAt = 1_700_000_000L,
            now = 1_700_000_100L
        )
        val event = Event(
            id = "b".repeat(64),
            pubkey = unsigned.pubkey,
            createdAt = unsigned.createdAt,
            kind = unsigned.kind,
            tags = unsigned.tags,
            content = unsigned.content,
            sig = "c".repeat(128)
        )
        val parsed = Marketplace.parse(event)
        assertNotNull(parsed)
        parsed!!
        assertEquals("bitaxe-supra-01", parsed.identifier)
        assertEquals("Bitaxe Supra 600 GH/s", parsed.title)
        assertEquals("Open-source solo Bitcoin miner", parsed.summary)
        assertEquals("Full kit with PSU and stand.", parsed.description)
        assertEquals(listOf("https://nostr.build/img1.jpg", "https://nostr.build/img2.jpg"), parsed.images)
        assertEquals(Price(150_000.0, "SATS"), parsed.price)
        assertEquals("Bangkok, TH", parsed.location)
        assertEquals(listOf("hardware", "bitcoin"), parsed.categories)
        assertFalse(parsed.sold)
        assertEquals("Free in Thailand", parsed.shipping)
        assertEquals(1_700_000_000L, parsed.publishedAt)
        assertTrue(parsed.editable)
    }

    @Test
    fun parseNip15Product_readsJsonContent() {
        val json = """
            {
              "id": "coldcard-mk4",
              "name": "Coldcard Mk4",
              "description": "Air-gapped Bitcoin hardware wallet",
              "price": 159.99,
              "currency": "USD",
              "quantity": 0,
              "images": ["https://example.com/cc1.jpg"]
            }
        """.trimIndent()
        val event = Event(
            id = "1".repeat(64),
            pubkey = samplePubkey,
            createdAt = 1_700_000_500L,
            kind = Marketplace.NIP15_PRODUCT,
            tags = listOf(listOf("d", "coldcard-mk4"), listOf("t", "hardware")),
            content = json,
            sig = "2".repeat(128)
        )
        val parsed = Marketplace.parse(event)
        assertNotNull(parsed)
        parsed!!
        assertEquals("Coldcard Mk4", parsed.title)
        assertEquals(Price(159.99, "USD"), parsed.price)
        assertTrue(parsed.sold) // quantity == 0
        assertFalse(parsed.editable)
        assertEquals(listOf("https://example.com/cc1.jpg"), parsed.images)
    }

    @Test
    fun priceConversion_satsPrimaryAndFiatSecondary() {
        val data = PriceData(
            btcUsd = 100_000.0,
            btcThb = 3_400_000.0,
            moscowTime = 1000,
            mstrUsd = 400.0,
            mstrBtc = 0.004,
            blockHeight = 900_000,
            mstrBtcHeld = 500_000.0,
            btcCirculation = 19_800_000.0,
            timestamp = 0L
        )
        // 100,000 sats at $100k/BTC = $100 or ฿3,400
        val inThb = formatListingPrice(Price(100_000.0, "SATS"), data, "THB")
        assertEquals("⚡ 100,000 sats", inThb.primary)
        assertEquals("(฿3,400)", inThb.secondary)
        assertEquals(100_000L, inThb.sats)

        // Seller listed in THB (฿3,400) -> converts to 100,000 sats primary + (฿3,400) secondary
        val fromThb = formatListingPrice(Price(3_400.0, "THB"), data, "USD")
        assertEquals("⚡ 100,000 sats", fromThb.primary)
        assertEquals("(฿3,400)", fromThb.secondary)
        assertEquals(100_000L, fromThb.sats)
    }

    @Test
    fun latest_deduplicatesByAddressKeepingNewest() {
        val older = Marketplace.build(
            pubkey = samplePubkey,
            identifier = "item-1",
            title = "Old Title",
            summary = null,
            description = "",
            images = emptyList(),
            price = Price(1000.0, "SATS"),
            location = null,
            categories = emptyList(),
            sold = false,
            shipping = null,
            publishedAt = 100L,
            now = 100L
        ).let { Event("1".repeat(64), it.pubkey, it.createdAt, it.kind, it.tags, it.content, "0".repeat(128)) }

        val newer = Marketplace.build(
            pubkey = samplePubkey,
            identifier = "item-1",
            title = "Updated Title",
            summary = null,
            description = "",
            images = emptyList(),
            price = Price(2000.0, "SATS"),
            location = null,
            categories = emptyList(),
            sold = true,
            shipping = null,
            publishedAt = 100L,
            now = 200L
        ).let { Event("2".repeat(64), it.pubkey, it.createdAt, it.kind, it.tags, it.content, "0".repeat(128)) }

        val list = Marketplace.latest(listOf(older, newer))
        assertEquals(1, list.size)
        assertEquals("Updated Title", list.single().title)
        assertTrue(list.single().sold)
    }

    @Test
    fun draftEventsAreIgnored() {
        val draft = Event(
            id = "3".repeat(64),
            pubkey = samplePubkey,
            createdAt = 100L,
            kind = Marketplace.CLASSIFIED_DRAFT,
            tags = listOf(listOf("d", "d1"), listOf("title", "Draft")),
            content = "",
            sig = "0".repeat(128)
        )
        assertNull(Marketplace.parse(draft))
    }
}
