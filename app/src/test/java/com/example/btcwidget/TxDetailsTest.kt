package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading Esplora (mempool.space) transactions and the words the card shows for them. */
class TxDetailsTest {

    private val confirmedTx = """{
      "txid":"${"ab".repeat(32)}","version":2,"locktime":0,"size":400,"weight":901,"fee":450,
      "vin":[
        {"txid":"${"11".repeat(32)}","vout":0,"is_coinbase":false,
         "prevout":{"scriptpubkey_type":"v0_p2wpkh","scriptpubkey_address":"bc1qinputaaaaaaaaaaaaaaaa1","value":150000000}},
        {"txid":"${"22".repeat(32)}","vout":1,"is_coinbase":false,
         "prevout":{"scriptpubkey_type":"v1_p2tr","scriptpubkey_address":"bc1pinputbbbbbbbbbbbbbbbb2","value":50000450}}
      ],
      "vout":[
        {"scriptpubkey_type":"v0_p2wpkh","scriptpubkey_address":"bc1qoutaaaaaaaaaaaaaaaaaaa1","value":199990000},
        {"scriptpubkey_type":"p2sh","scriptpubkey_address":"3Outbbbbbbbbbbbbbbbbbbbbb2","value":10000},
        {"scriptpubkey_type":"op_return","value":0}
      ],
      "status":{"confirmed":true,"block_height":976400,"block_hash":"00","block_time":1795000000}
    }"""

    @Test
    fun readsInputsOutputsFeeAndBlock() {
        val tx = TxDetails.parse(confirmedTx)
        assertEquals("ab".repeat(32), tx.txid)
        assertEquals(2, tx.inputs.size)
        assertEquals(3, tx.outputs.size)
        assertEquals("bc1qinputaaaaaaaaaaaaaaaa1", tx.inputs[0].address)
        assertEquals(TxIo.Kind.OP_RETURN, tx.outputs[2].kind)
        assertNull(tx.outputs[2].address)
        assertEquals(200_000_450L, tx.totalIn)
        assertEquals(200_000_000L, tx.totalOut)
        assertEquals(450L, tx.feeSats)
        assertEquals(226, tx.vsize) // ceil(901 / 4)
        assertEquals(450.0 / 226, tx.feeRate, 1e-9)
        assertTrue(tx.confirmed)
        assertEquals(976400, tx.blockHeight)
        assertEquals(1795000000L, tx.blockTime)
        assertFalse(tx.isCoinbase)
    }

    @Test
    fun countsConfirmationsFromTheTip() {
        val tx = TxDetails.parse(confirmedTx)
        assertEquals(1, tx.confirmations(976400))
        assertEquals(6, tx.confirmations(976405))
        // Tip not known yet: it's in a block, so at least one.
        assertEquals(1, tx.confirmations(null))
        // A stale tip below the block never gives 0 or less.
        assertEquals(1, tx.confirmations(976390))
    }

    @Test
    fun mempoolAndCoinbase() {
        val pending = TxDetails.parse("""{"txid":"x","weight":400,"fee":100,"vin":[],"vout":[],"status":{"confirmed":false}}""")
        assertFalse(pending.confirmed)
        assertEquals(0, pending.confirmations(900000))
        assertNull(pending.blockHeight)

        val coinbase = TxDetails.parse(
            """{"txid":"c","weight":800,"fee":0,"vin":[{"is_coinbase":true,"prevout":null}],
                "vout":[{"scriptpubkey_type":"v0_p2wpkh","scriptpubkey_address":"bc1qminer","value":312500000}],
                "status":{"confirmed":true,"block_height":1}}"""
        )
        assertTrue(coinbase.isCoinbase)
        assertEquals(TxIo.Kind.COINBASE, coinbase.inputs.single().kind)
    }

    @Test
    fun amounts() {
        assertEquals("2,484.5653 BTC", TxFormat.amount(248_456_530_000))
        assertEquals("1.00 BTC", TxFormat.amount(100_000_000))
        assertEquals("0.05 BTC", TxFormat.amount(5_000_000))
        assertEquals("0.00123456 BTC", TxFormat.amount(123_456))
        assertEquals("99,999 sats", TxFormat.amount(99_999))
        assertEquals("0 sats", TxFormat.amount(0))
    }

    @Test
    fun shortForms() {
        assertEquals("bc1qinpu…aaa1", TxFormat.shortAddress("bc1qinputaaaaaaaaaaaaaaaa1"))
        assertEquals("1BoatSLRHtKNngkd", TxFormat.shortAddress("1BoatSLRHtKNngkd"))
        assertEquals("abababab…abab", TxFormat.shortTxid("ab".repeat(32)))
        assertEquals("1,234", TxFormat.count(1234))
        assertEquals("100,000+", TxFormat.count(250_000))
    }

    @Test
    fun longListsEndInAMoreRow() {
        val ios = (1..10).map { TxIo("a$it", it * 1000L, TxIo.Kind.ADDRESS) }
        val (shown, more) = TxFormat.visibleRows(ios, 4)
        assertEquals(listOf("a1", "a2", "a3"), shown.map { it.address })
        assertEquals(7, more!!.count)
        assertEquals((4..10).sumOf { it * 1000L }, more.sats)
        // Exactly the limit: all of them, no "more" row.
        val (all, none) = TxFormat.visibleRows(ios.take(4), 4)
        assertEquals(4, all.size)
        assertNull(none)
    }

    private val a = "a9".repeat(32)
    private val b = "b7".repeat(32)

    @Test
    fun findsEveryTransactionInOrderOnce() {
        val text = "Big move https://mempool.space/tx/$b then mempool.space/tx/$a and again https://blockstream.info/tx/$b · txid: ${a.uppercase()}"
        assertEquals(listOf(b, a), TxLinks.find(text).txids)
    }

    @Test
    fun capsTheList() {
        val text = (1..20).joinToString(" ") { "mempool.space/tx/" + "%064x".format(it) }
        assertEquals(12, TxLinks.find(text).txids.size)
    }

    @Test
    fun testnetLinksAndLongerHexDontCount() {
        assertTrue(TxLinks.find("https://mempool.space/testnet/tx/$a").txids.isEmpty())
        assertTrue(TxLinks.find("mempool.space/tx/${a}ff").txids.isEmpty())
    }

    @Test
    fun aBlockOnlyWhenThereAreNoTransactions() {
        assertEquals("976400", TxLinks.find("see https://mempool.space/block/976400").block)
        assertEquals("925000", TxLinks.find("Found in Block #925,000!").block)
        assertNull(TxLinks.find("Found in Block #925,000!", stamped = true).block)
        val both = TxLinks.find("https://mempool.space/block/976400 https://mempool.space/tx/$a")
        assertEquals(listOf(a), both.txids)
        assertNull(both.block)
        assertNull(TxLinks.find("block #12").block)
    }

    @Test
    fun readsBlocksWithAndWithoutMempoolExtras() {
        val mempool = BlockDetails.parse(
            """{"id":"0000abc","height":976400,"timestamp":1795000000,"tx_count":3412,"size":1620000,"weight":3993000,
                "difficulty":126270000000000.5,"extras":{"totalFees":2345678,"medianFee":2.4,"feeRange":[1.0,1.5,3.2,250.7],
                "reward":314845678,"pool":{"id":1,"name":"Foundry USA","slug":"foundryusa"}}}"""
        )
        assertEquals(976400, mempool.height)
        assertEquals(3412, mempool.txCount)
        assertEquals("Foundry USA", mempool.extras!!.pool)
        assertEquals(2.4, mempool.extras!!.medianFee!!, 0.0)
        assertEquals(1.0 to 250.7, mempool.extras!!.feeRange)
        assertEquals(314_845_678L, mempool.extras!!.rewardSats)
        assertEquals(3993000 / 4_000_000f, mempool.fullness, 1e-6f)
        assertEquals(6, mempool.confirmations(976405))
        assertEquals("1.62 MB", formatBytes(mempool.sizeBytes))
        assertEquals("126.27 T", formatDifficulty(mempool.difficulty))

        val esplora = BlockDetails.parse("""{"id":"0000abc","height":10,"timestamp":1,"tx_count":1,"size":285,"weight":1140,"difficulty":1}""")
        assertNull(esplora.extras)
        assertEquals("285 B", formatBytes(285))
        assertEquals("1", formatDifficulty(1.0))
    }
}
