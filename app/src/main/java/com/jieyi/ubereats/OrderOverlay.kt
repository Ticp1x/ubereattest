package com.jieyi.ubereats

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 屏幕顶部浮窗显示派单判断。
 * 派单弹窗占底部，普通 Toast 会被遮挡（OPPO/MIUI 后台 Toast 也可能被系统吞）。
 * 用 TYPE_APPLICATION_OVERLAY 画在所有 app 之上（需 SYSTEM_ALERT_WINDOW 权限，app 已有）。
 */
object OrderOverlay {

    private const val TAG = "OrderOverlay"
    private const val DISPLAY_MS = 5000L

    private val handler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentWm: WindowManager? = null
    private val dismissTask = Runnable { dismissInternal() }

    fun show(ctx: Context, msg: String, verdict: Verdict) {
        handler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                // 没权限只能降级用 Toast（大概率也看不见但不能静默丢）
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                return@post
            }
            dismissInternal()
            try {
                showInternal(ctx.applicationContext, msg, verdict)
                handler.removeCallbacks(dismissTask)
                handler.postDelayed(dismissTask, DISPLAY_MS)
            } catch (e: Throwable) {
                Log.w(TAG, "show failed: ${e.message}")
                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showInternal(ctx: Context, msg: String, verdict: Verdict) {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val bg = when (verdict) {
            Verdict.GO -> 0xEE1B5E20.toInt()   // 深绿 接
            Verdict.HOLD -> 0xEEF57C00.toInt() // 橙黄 看
            Verdict.NO -> 0xEEB71C1C.toInt()   // 深红 拒
        }

        val container = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = 24f
            }
            val pad = dp(ctx, 16)
            setPadding(pad, pad, pad, pad)
        }

        val text = TextView(ctx).apply {
            this.text = msg
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }
        container.addView(
            text,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val windowType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = dp(ctx, 12)
            y = dp(ctx, 60)  // 避开状态栏 + 打孔
            width = ctx.resources.displayMetrics.widthPixels - dp(ctx, 24)
        }

        wm.addView(container, lp)
        currentView = container
        currentWm = wm
    }

    private fun dismissInternal() {
        val v = currentView
        val w = currentWm
        currentView = null
        currentWm = null
        if (v != null && w != null) {
            try { w.removeView(v) } catch (_: Throwable) {}
        }
    }

    private fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
