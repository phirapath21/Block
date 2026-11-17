package com.example.btcwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers checked against the documented response shapes of each API. */
class ApiParsersTest {

    @Test
    fun coinbaseRates() {
        val json = """{"data":{"currency":"BTC","rates":{"USD":"74455.12","THB":"2415000.5","EUR":"69000"}}}"""
        val rates = ApiParsers.coinbaseRates(json)
        assertEquals(74455.12, rates.usd, 1e-9)
        assertEquals(2415000.5, rates.thb, 1e-9)
    }

    @Test
    fun coinbaseStats_changeOver24h() {
        val json = """{"open":"72000.00","high":"75000","low":"71000","last":"73800.00","volume":"12345.6","volume_30day":"1"}"""
        val stats = ApiParsers.coinbaseStats(json)
        assertEquals(72000.0, stats.open, 1e-9)
        assertEquals(2.5, stats.changePct, 1e-9)
    }

    @Test
    fun krakenLast_readsFirstPairKey() {
        val json = """{"error":[],"result":{"XXBTZUSD":{"a":["74460.1","1","1.000"],"b":["74460.0","2","2.000"],"c":["74455.3","0.01"],"o":"73000.0"}}}"""
        assertEquals(74455.3, ApiParsers.krakenLast(json), 1e-9)
    }

    @Test(expected = IllegalStateException::class)
    fun krakenLast_reportsApiErrors() {
        ApiParsers.krakenLast("""{"error":["EQuery:Unknown asset pair"],"result":{}}""")
    }

    @Test
    fun bitkubTicker_v3Array() {
        // Sample from Bitkub's official REST v3 docs.
        val json = """[{"symbol":"BTC_THB","base_volume":"1875227.0489781","high_24_hr":"3400000","highest_bid":"3380000",
            "last":"3385000","low_24_hr":"3300000","lowest_ask":"3390000","percent_change":"2.69","quote_volume":"69080877.73"}]"""
        val t = ApiParsers.bitkubTicker(json)
        assertEquals(3_385_000.0, t.last, 1e-9)
        assertEquals(2.69, t.percentChange, 1e-9)
    }

    @Test
    fun coinbaseCandles_newestFirstBecomesOldestFirst() {
        val json = """[[1700007200,1,2,3,74500.5,9],[1700003600,1,2,3,74400,9],[1700000000,1,2,3,74300,9]]"""
        val points = ApiParsers.coinbaseCandles(json)
        assertEquals(listOf(1700000000L, 1700003600L, 1700007200L), points.map { it.time })
        assertEquals(74500.5, points.last().price, 1e-9)
    }

    @Test
    fun satsMoveOppositeToPrice() {
        assertEquals(-9.0909, PriceMath.satsChangePct(10.0), 1e-3)
        assertTrue(PriceMath.satsChangePct(Double.NaN).isNaN())
        assertTrue(PriceMath.changePct(0.0, 5.0).isNaN())
    }

    @Test
    fun treasury_findsStrategyAndStrive() {
        val json = """{"total_holdings":1,"companies":[
            {"name":"Strategy","symbol":"MSTR.US","total_holdings":640031},
            {"name":"Strive, Inc.","symbol":"ASST.US","total_holdings":27461},
            {"name":"Other","symbol":"OTHR","total_holdings":5}]}"""
        val t = ApiParsers.treasury(json)
        assertEquals(640031.0, t.mstr!!, 0.0)
        assertEquals(27461.0, t.asst!!, 0.0)
    }

    @Test
    fun treasury_striveByNameAndMissingCompanies() {
        val byName = ApiParsers.treasury("""{"companies":[{"name":"Strive","symbol":"XYZ","total_holdings":100}]}""")
        assertEquals(100.0, byName.asst!!, 0.0)
        assertEquals(null, byName.mstr)
        val empty = ApiParsers.treasury("""{"companies":[{"name":"Strategy","symbol":"MSTR","total_holdings":0}]}""")
        assertEquals(null, empty.mstr)
        assertEquals(null, empty.asst)
    }
}
