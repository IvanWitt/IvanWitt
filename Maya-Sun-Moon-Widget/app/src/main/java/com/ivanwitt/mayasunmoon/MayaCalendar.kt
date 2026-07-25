package com.ivanwitt.mayasunmoon

import java.time.LocalDate
import kotlin.math.abs

data class MayaDate(
    val longCount: String,
    val tzolkin: String,
    val haab: String
)

object MayaCalendar {
    private val tzolkinNames = arrayOf(
        "Imix", "Ik’", "Ak’bal", "K’an", "Chikchan",
        "Kimi", "Manik’", "Lamat", "Muluk", "Ok",
        "Chuwen", "Eb’", "B’en", "Ix", "Men",
        "Kib’", "Kab’an", "Etz’nab’", "Kawak", "Ajaw"
    )

    private val haabMonths = arrayOf(
        "Pop", "Wo’", "Sip", "Sotz’", "Sek",
        "Xul", "Yaxk’in", "Mol", "Ch’en", "Yax",
        "Sak’", "Keh", "Mak", "K’ank’in", "Muwan",
        "Pax", "K’ayab", "Kumk’u", "Wayeb’"
    )

    fun fromGregorian(date: LocalDate, correlation: Int): MayaDate {
        val jdn = gregorianJdn(date)
        val totalDays = jdn.toLong() - correlation.toLong()

        val longCount = longCount(totalDays)

        // 0.0.0.0.0 is conventionally 4 Ajaw 8 Kumk'u.
        val tzNumber = floorMod(totalDays + 3L, 13L).toInt() + 1
        val tzName = tzolkinNames[floorMod(totalDays + 19L, 20L).toInt()]

        val haabIndex = floorMod(totalDays + 348L, 365L).toInt()
        val (haabDay, haabMonth) =
            if (haabIndex < 360) {
                (haabIndex % 20) to haabMonths[haabIndex / 20]
            } else {
                (haabIndex - 360) to haabMonths[18]
            }

        return MayaDate(
            longCount = longCount,
            tzolkin = "$tzNumber $tzName",
            haab = "$haabDay $haabMonth"
        )
    }

    private fun longCount(totalDays: Long): String {
        if (totalDays < 0) {
            // Modern use is positive for ordinary correlations; keep negative input explicit
            // instead of silently wrapping it into a fake positive Long Count.
            return "−" + longCount(abs(totalDays))
        }

        var n = totalDays
        val baktun = n / 144000L
        n %= 144000L
        val katun = n / 7200L
        n %= 7200L
        val tun = n / 360L
        n %= 360L
        val uinal = n / 20L
        val kin = n % 20L
        return "$baktun.$katun.$tun.$uinal.$kin"
    }

    /**
     * Integer Julian Day Number for the civil Gregorian date.
     * With correlation 584283, 2012-12-21 maps to 13.0.0.0.0.
     */
    private fun gregorianJdn(date: LocalDate): Int {
        val a = (14 - date.monthValue) / 12
        val y = date.year + 4800 - a
        val m = date.monthValue + 12 * a - 3
        return date.dayOfMonth +
            (153 * m + 2) / 5 +
            365 * y +
            y / 4 -
            y / 100 +
            y / 400 -
            32045
    }

    private fun floorMod(a: Long, b: Long): Long {
        val r = a % b
        return if (r >= 0) r else r + b
    }
}
