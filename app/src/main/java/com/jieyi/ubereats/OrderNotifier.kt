package com.jieyi.ubereats

import android.content.Context

/**
 * 派单 3 要素 → Calculator 裁决 → 屏幕顶部浮窗显示判断。
 * 复用给 Accessibility 读单和截屏 OCR 两条路径。
 */
object OrderNotifier {

    fun evaluate(ctx: Context, price: Double, km: Double, minutes: Double): Decision {
        val base = Prefs.load(ctx)
        val idleSec = TimerState.idleSeconds(ctx)
        val stage = Stage.fromIdleMinutes((idleSec / 60).toInt())
        return Calculator.analyze(price, km, minutes, base, stage)
    }

    fun notify(ctx: Context, price: Double, km: Double, minutes: Double, d: Decision) {
        val tag = when (d.verdict) {
            Verdict.GO -> "接"
            Verdict.HOLD -> "看"
            Verdict.NO -> "拒"
        }
        val msg = "[$tag] \$${"%.2f".format(price)} · ${"%.1f".format(km)}km · ${minutes.toInt()}min\n" +
                "→ ${"%.2f".format(d.perKm)}/km · 净\$${"%.2f".format(d.netProfit)}"
        OrderOverlay.show(ctx, msg, d.verdict)
    }
}
