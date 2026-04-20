package com.jieyi.ubereats

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val FILE = "uep_prefs"
    private const val K_PER_KM = "per_km"
    private const val K_PER_MIN = "per_min"
    private const val K_MIN_PAYOUT = "min_payout"
    private const val K_COST_PER_KM = "cost_per_km"

    fun load(ctx: Context): Thresholds {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Thresholds()
        return Thresholds(
            perKm = sp.getFloat(K_PER_KM, d.perKm.toFloat()).toDouble(),
            perMin = sp.getFloat(K_PER_MIN, d.perMin.toFloat()).toDouble(),
            minPayout = sp.getFloat(K_MIN_PAYOUT, d.minPayout.toFloat()).toDouble(),
            costPerKm = sp.getFloat(K_COST_PER_KM, d.costPerKm.toFloat()).toDouble()
        )
    }

    fun save(ctx: Context, t: Thresholds) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putFloat(K_PER_KM, t.perKm.toFloat())
            putFloat(K_PER_MIN, t.perMin.toFloat())
            putFloat(K_MIN_PAYOUT, t.minPayout.toFloat())
            putFloat(K_COST_PER_KM, t.costPerKm.toFloat())
        }
    }

    fun resetDefaults(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { clear() }
    }
}
