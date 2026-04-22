package com.jieyi.ubereats

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * MVP：读取 Uber Driver 派单弹窗里的 CA$X.XX / X 分钟 / X.X 公里，
 * 丢进 Calculator 算 verdict，Toast 提示。
 *
 * 调试思路：
 *   - 手机接到 Uber 派单时观察 Toast 是否出现
 *   - Logcat 过滤 tag "UberReader" 可看原始文本 + 识别到的 package
 *   - 如果 Toast 没出现但派单弹窗有：查 logcat 里的 pkg 名，回报给我调整
 */
class UberOrderReaderService : AccessibilityService() {

    companion object {
        private const val TAG = "UberReader"
        private const val DEBOUNCE_MS = 300L
        // 调试开关：true 时对 com.ubercab.* 所有窗口事件都打 preview 日志，用于诊断派单弹窗读不到的问题
        private const val DEBUG_UBER = true
        private const val PREVIEW_LEN = 500

        private val PRICE_RE = Regex("""(?:CA)?\$\s*([0-9]+(?:\.[0-9]{1,2})?)""")
        private val MIN_RE = Regex("""([0-9]+)\s*(?:分钟|minutes?|min)\b""", RegexOption.IGNORE_CASE)
        private val KM_RE = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:公里|km|kilometers?)\b""", RegexOption.IGNORE_CASE)
        private val ACCEPT_MARKERS = listOf("接受", "Accept", "派送", "专属优惠")
        private val UBER_PKG_PREFIXES = listOf("com.ubercab")

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var lastFingerprint: String = ""
    private var lastEventMs: Long = 0L
    private var lastDebugFingerprint: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
        Toast.makeText(this, "UberEats 订单读取已启动", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "Accessibility service destroyed")
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val now = System.currentTimeMillis()
        if (now - lastEventMs < DEBOUNCE_MS) return
        lastEventMs = now

        val buf = StringBuilder()
        try {
            // 遍历所有 visible window（需 flagRetrieveInteractiveWindows flag）
            // 派单弹窗是独立 popup window，rootInActiveWindow 抓不到
            val allWindows = windows ?: emptyList()
            for (w in allWindows) {
                w.root?.let { collectText(it, buf) }
            }
            // fallback：windows API 返回空时回退到旧逻辑
            if (buf.isEmpty()) {
                rootInActiveWindow?.let { collectText(it, buf) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "collectText failed: ${e.message}")
            return
        }
        if (buf.isEmpty()) return
        val flat = buf.toString()
        val pkg = event.packageName?.toString() ?: "?"
        val isUber = UBER_PKG_PREFIXES.any { pkg.startsWith(it) }

        // DEBUG：对 Uber 全家桶所有事件都留脚印，文本去重避免刷屏
        if (DEBUG_UBER && isUber) {
            val preview = flat.take(PREVIEW_LEN).replace('\n', ' ').replace('\r', ' ')
            val dbgFp = "$pkg|${flat.length}|${preview.take(60)}"
            if (dbgFp != lastDebugFingerprint) {
                lastDebugFingerprint = dbgFp
                val typeName = AccessibilityEvent.eventTypeToString(type)
                Log.i(TAG, "[DBG] pkg=$pkg evt=$typeName len=${flat.length} text=$preview")
            }
        }

        // 只对"含价格 + 含接受标志"的画面出手，减少噪音
        val hasPrice = flat.contains("$")
        val hasMarker = ACCEPT_MARKERS.any { flat.contains(it) }
        if (!hasPrice || !hasMarker) return

        val priceM = PRICE_RE.find(flat)
        val kmM = KM_RE.find(flat)
        val minM = MIN_RE.find(flat)

        if (priceM == null || kmM == null || minM == null) {
            Log.d(TAG, "partial pkg=$pkg price=${priceM?.value} km=${kmM?.value} min=${minM?.value}")
            return
        }

        val price = priceM.groupValues[1].toDoubleOrNull() ?: return
        val km = kmM.groupValues[1].toDoubleOrNull() ?: return
        val minutes = minM.groupValues[1].toDoubleOrNull() ?: return

        val fp = "$pkg|$price|$km|$minutes"
        if (fp == lastFingerprint) return
        lastFingerprint = fp

        Log.i(TAG, "order pkg=$pkg price=$price km=$km min=$minutes")
        evaluateAndNotify(price, km, minutes)
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder) {
        if (node == null) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out)
        }
    }

    private fun evaluateAndNotify(price: Double, km: Double, minutes: Double) {
        val base = Prefs.load(this)
        val idleSec = TimerState.idleSeconds(this)
        val stage = Stage.fromIdleMinutes((idleSec / 60).toInt())
        val d = Calculator.analyze(price, km, minutes, base, stage)

        val tag = when (d.verdict) {
            Verdict.GO -> "接"
            Verdict.HOLD -> "看"
            Verdict.NO -> "拒"
        }
        val msg = "[$tag] \$${"%.2f".format(price)} · ${"%.1f".format(km)}km · ${minutes.toInt()}min " +
                "→ ${"%.2f".format(d.perKm)}/km · 净\$${"%.2f".format(d.netProfit)}"
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
