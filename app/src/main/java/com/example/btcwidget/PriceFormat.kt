package com.example.btcwidget

import java.util.Locale

/** The Bitcoin price in the chosen currency, and how it's written. */
object PriceFormat {
    fun price(data: PriceData, currency: String): Double =
        if (currency == "THB") data.btcThb else data.btcUsd

    fun format(value: Double, currency: String): String =
        if (currency == "THB") String.format(Locale.US, "฿%,.0f", value)
        else String.format(Locale.US, "$%,.0f", value)
}
