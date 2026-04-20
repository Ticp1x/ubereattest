package com.jieyi.ubereats

enum class Stage(
    val label: String,
    val minutesMax: Int,
    val payoutMul: Double,
    val perKmMul: Double,
    val perMinMul: Double
) {
    NORMAL("正常", 6, 1.45, 1.10, 1.38),
    WARN("预警", 12, 1.11, 0.85, 1.13),
    HUNGRY("饥饿", 18, 0.78, 0.65, 0.88),
    STARVING("极饥饿", Int.MAX_VALUE, 0.0, 0.0, 0.0);

    fun applyTo(base: Thresholds): Thresholds = Thresholds(
        perKm = base.perKm * perKmMul,
        perMin = base.perMin * perMinMul,
        minPayout = base.minPayout * payoutMul,
        costPerKm = base.costPerKm
    )

    companion object {
        fun fromIdleMinutes(m: Int): Stage {
            if (m < NORMAL.minutesMax) return NORMAL
            if (m < WARN.minutesMax) return WARN
            if (m < HUNGRY.minutesMax) return HUNGRY
            return STARVING
        }
    }
}
