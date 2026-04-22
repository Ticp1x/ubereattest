package com.jieyi.ubereats

/**
 * 从 OCR 结果或 Accessibility 文本里抽派单 3 要素（price/km/min）+ marker 判断。
 * OCR 文本可能有换行、噪音、空格异常，正则要放宽：\b 在中文前后不可靠，这里不用 \b。
 */
object OrderParser {

    private val PRICE_RE = Regex("""(?:CA)?\$\s*([0-9]+(?:\.[0-9]{1,2})?)""")
    private val MIN_RE = Regex("""([0-9]+)\s*(?:分钟|minutes?|min)""", RegexOption.IGNORE_CASE)
    private val KM_RE = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:公里|km|kilometers?)""", RegexOption.IGNORE_CASE)
    private val ACCEPT_MARKERS = listOf("接受", "Accept", "派送", "专属优惠", "总计")

    data class Extracted(
        val hasMarker: Boolean,
        val price: Double?,
        val km: Double?,
        val minutes: Double?,
    ) {
        val isOrder: Boolean
            get() = hasMarker && price != null && km != null && minutes != null
    }

    fun extract(text: String): Extracted {
        val hasMarker = ACCEPT_MARKERS.any { text.contains(it) }
        val price = PRICE_RE.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val km = KM_RE.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val minutes = MIN_RE.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return Extracted(hasMarker, price, km, minutes)
    }
}
