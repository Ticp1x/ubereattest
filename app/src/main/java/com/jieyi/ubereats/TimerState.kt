package com.jieyi.ubereats

import android.content.Context
import androidx.core.content.edit

/**
 * 跟踪司机"等单"时间。
 *
 * 业务规则：
 *   - 上次接单 → 用户"忙"，忙到 busyUntil = acceptedAt + deliveryMinutes*60s
 *   - busyUntil 之后 → "等单"状态开始计时
 *   - 下次接单 → busyUntil 重置为 now + newMinutes*60s
 *
 * 用户从未使用过，或长时间未用，则回退到"等单从 app 启动时开始"
 */
object TimerState {
    private const val FILE = "uep_timer"
    private const val K_BUSY_UNTIL = "busy_until_ms"
    private const val K_LAST_ACTIVITY = "last_activity_ms"

    fun markAccepted(ctx: Context, deliveryMinutes: Double, nowMs: Long = System.currentTimeMillis()) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putLong(K_BUSY_UNTIL, nowMs + (deliveryMinutes * 60_000).toLong())
            putLong(K_LAST_ACTIVITY, nowMs)
        }
    }

    fun markRejected(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putLong(K_LAST_ACTIVITY, nowMs)
        }
    }

    fun idleSeconds(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val busyUntil = sp.getLong(K_BUSY_UNTIL, 0L)
        val lastActivity = sp.getLong(K_LAST_ACTIVITY, 0L)
        val reference = maxOf(busyUntil, lastActivity)
        if (reference == 0L) return 0L
        if (nowMs < busyUntil) return 0L
        return (nowMs - reference) / 1000L
    }

    fun isBusy(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val busyUntil = sp.getLong(K_BUSY_UNTIL, 0L)
        return nowMs < busyUntil
    }

    fun busyRemainingSeconds(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val busyUntil = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getLong(K_BUSY_UNTIL, 0L)
        return maxOf(0L, (busyUntil - nowMs) / 1000L)
    }

    fun reset(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { clear() }
    }

    fun touchActivity(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putLong(K_LAST_ACTIVITY, nowMs)
        }
    }
}
