package com.jieyi.ubereats

import kotlin.math.max

enum class Verdict { GO, HOLD, NO }

data class Thresholds(
    val perKm: Double = 1.00,
    val perMin: Double = 0.40,
    val minPayout: Double = 4.50,
    val costPerKm: Double = 0.13
)

data class Decision(
    val verdict: Verdict,
    val perKm: Double,
    val perMin: Double,
    val netProfit: Double,
    val netHourly: Double,
    val reason: String
)

object Calculator {

    fun analyze(payout: Double, km: Double, minutes: Double, t: Thresholds): Decision {
        val safeKm = max(km, 0.01)
        val safeMin = max(minutes, 0.01)
        val perKm = payout / safeKm
        val perMin = payout / safeMin
        val cost = km * t.costPerKm
        val net = payout - cost
        val netHourly = (net / safeMin) * 60.0

        val reasons = mutableListOf<String>()

        if (payout < t.minPayout) {
            reasons += "总价 \$${"%.2f".format(payout)} < 门槛 \$${"%.2f".format(t.minPayout)}"
            return Decision(Verdict.NO, perKm, perMin, net, netHourly, reasons.joinToString("；"))
        }

        val perKmHardFloor = t.perKm * 0.70
        val perMinHardFloor = t.perMin * 0.70

        if (perKm < perKmHardFloor) reasons += "\$/km \$${"%.2f".format(perKm)} 严重低于 \$${"%.2f".format(t.perKm)}"
        if (perMin < perMinHardFloor) reasons += "\$/min \$${"%.2f".format(perMin)} 严重低于 \$${"%.2f".format(t.perMin)}"
        if (km >= 15.0 && perKm < t.perKm) reasons += "长途 ${"%.1f".format(km)}km + \$/km 不达标"

        if (reasons.isNotEmpty()) {
            return Decision(Verdict.NO, perKm, perMin, net, netHourly, reasons.joinToString("；"))
        }

        if (perKm >= t.perKm && perMin >= t.perMin) {
            return Decision(
                Verdict.GO, perKm, perMin, net, netHourly,
                "两项都过线，净\$${"%.2f".format(net)} · 时薪\$${"%.0f".format(netHourly)}/h"
            )
        }

        val lag = mutableListOf<String>()
        if (perKm < t.perKm) lag += "\$/km 偏低"
        if (perMin < t.perMin) lag += "\$/min 偏低"
        return Decision(
            Verdict.HOLD, perKm, perMin, net, netHourly,
            "${lag.joinToString("，")}；时薪\$${"%.0f".format(netHourly)}/h · Quest 冲刺可接"
        )
    }
}
