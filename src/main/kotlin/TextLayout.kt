package com.bcz

import java.awt.FontMetrics

internal fun ellipsize(text: String, metrics: FontMetrics, maxWidth: Int): String {
    if (maxWidth <= 0) return ""
    if (metrics.stringWidth(text) <= maxWidth) return text
    val suffix = "…"
    if (metrics.stringWidth(suffix) > maxWidth) return ""
    var low = 0
    var high = text.codePointCount(0, text.length)
    while (low < high) {
        val middle = (low + high + 1) / 2
        val prefix = text.substring(0, text.offsetByCodePoints(0, middle))
        if (metrics.stringWidth(prefix + suffix) <= maxWidth) low = middle else high = middle - 1
    }
    return text.substring(0, text.offsetByCodePoints(0, low)) + suffix
}
