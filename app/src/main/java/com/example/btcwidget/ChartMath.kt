package com.example.btcwidget

/** Time windows the price chart can show. */
enum class ChartRange(val label: String, val hours: Int) {
    DAY("24H", 24),
    WEEK("7D", 24 * 7),
    MONTH("30D", 24 * 30),
    YEAR("1Y", 24 * 365),
    HALVING("4Y", 24 * 365 * 4),
    ALL("ALL", Int.MAX_VALUE)
}

/** Summary of a price series for the chart header and accessibility. */
data class ChartSummary(val first: Double, val last: Double, val high: Double, val low: Double) {
    val changePct: Double get() = PriceMath.changePct(first, last)
}

/** Pure helpers for the price chart, unit tested. */
object ChartMath {

    /** Points within the last [range] ending at the newest point, converted by [factor]. */
    fun slice(points: List<PricePoint>, range: ChartRange, factor: Double = 1.0): List<PricePoint> {
        if (points.isEmpty()) return emptyList()
        val since = points.last().time - range.hours * 3600L
        val hourly = range == ChartRange.DAY || range == ChartRange.WEEK
        return points.filter {
            it.time >= since && if (hourly) it.resolutionSeconds <= 3600L else it.resolutionSeconds >= 86_400L
        }
            .map { it.copy(price = it.price * factor) }
    }

    fun summary(points: List<PricePoint>): ChartSummary? {
        if (points.size < 2) return null
        return ChartSummary(
            first = points.first().price,
            last = points.last().price,
            high = points.maxOf { it.price },
            low = points.minOf { it.price }
        )
    }

    /** X position (0..width) of point [index] when points are spaced evenly in time. */
    fun x(points: List<PricePoint>, index: Int, width: Float): Float {
        val t0 = points.first().time
        val span = (points.last().time - t0).coerceAtLeast(1)
        return (points[index].time - t0).toFloat() / span * width
    }

    /**
     * Y position (0 = top) for [price], with [padFraction] of the range kept free above
     * and below so the line never touches the edges.
     */
    fun y(price: Double, low: Double, high: Double, height: Float, padFraction: Float = 0.12f): Float {
        val range = (high - low).takeIf { it > 0 } ?: 1.0
        val pad = height * padFraction
        val usable = height - 2 * pad
        return pad + ((high - price) / range).toFloat() * usable
    }

    /** Index of the point nearest to x-coordinate [x] (the crosshair snaps to it). */
    fun nearestIndex(points: List<PricePoint>, x: Float, width: Float): Int {
        if (points.isEmpty()) return -1
        var best = 0
        var bestDist = Float.MAX_VALUE
        for (i in points.indices) {
            val d = kotlin.math.abs(x(points, i, width) - x)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }

    /** Encodes history for the offline cache: "time:price,time:price". */
    fun encode(points: List<PricePoint>): String = points.joinToString(",") { "${it.time}:${it.price}:${it.resolutionSeconds}" }

    fun decode(text: String?): List<PricePoint> =
        text.orEmpty().split(',').mapNotNull { part ->
            val fields = part.split(':')
            if (fields.size !in 2..3) return@mapNotNull null
            val (t, p) = fields
            val time = t.toLongOrNull() ?: return@mapNotNull null
            val price = p.toDoubleOrNull() ?: return@mapNotNull null
            val resolution = fields.getOrNull(2)?.toLongOrNull() ?: 3600L
            PricePoint(time, price, resolution)
        }
}
