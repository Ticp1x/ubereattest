package com.jieyi.ubereats

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        var isRunning: Boolean = false
            private set
        private const val CHANNEL_ID = "uep_floating"
        private const val NOTIF_ID = 1001

        const val ACTION_SET_VERDICT = "com.jieyi.ubereats.FLOATING_SET_VERDICT"
        const val EXTRA_VERDICT = "verdict"
        const val EXTRA_PRICE = "price"
        const val EXTRA_KM = "km"
        const val EXTRA_MIN = "min"
        const val EXTRA_PER_KM = "perKm"
        const val EXTRA_NET_PROFIT = "netProfit"

        private const val VERDICT_DISPLAY_MS = 8000L

        /** 外部路径（OCR/Accessibility）识别完一单 → 通知悬浮球变色。 */
        fun postVerdict(ctx: Context, price: Double, km: Double, minutes: Double, d: Decision) {
            if (!isRunning) return
            val i = Intent(ctx, FloatingService::class.java).apply {
                action = ACTION_SET_VERDICT
                putExtra(EXTRA_VERDICT, d.verdict.name)
                putExtra(EXTRA_PRICE, price)
                putExtra(EXTRA_KM, km)
                putExtra(EXTRA_MIN, minutes)
                putExtra(EXTRA_PER_KM, d.perKm)
                putExtra(EXTRA_NET_PROFIT, d.netProfit)
            }
            try { ctx.startService(i) } catch (_: Throwable) {}
        }
    }

    private val idleRestoreRunnable = Runnable { restoreIdleBubble() }

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private var currentRecordTimestamp: Long? = null
    private var currentRecordMinutes: Double = 0.0

    private val stageTicker = object : Runnable {
        override fun run() {
            updateStageBanner()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SET_VERDICT) {
            handleVerdict(intent)
        }
        return START_STICKY
    }

    private fun handleVerdict(intent: Intent) {
        val verdictName = intent.getStringExtra(EXTRA_VERDICT) ?: return
        val verdict = runCatching { Verdict.valueOf(verdictName) }.getOrNull() ?: return
        showVerdictOnBubble(verdict)
        handler.removeCallbacks(idleRestoreRunnable)
        handler.postDelayed(idleRestoreRunnable, VERDICT_DISPLAY_MS)
    }

    private fun showVerdictOnBubble(v: Verdict) {
        val view = bubbleView ?: return
        val bg = when (v) {
            Verdict.GO -> R.drawable.bubble_go
            Verdict.HOLD -> R.drawable.bubble_hold
            Verdict.NO -> R.drawable.bubble_no
        }
        val label = when (v) {
            Verdict.GO -> "接"
            Verdict.HOLD -> "看"
            Verdict.NO -> "拒"
        }
        view.setBackgroundResource(bg)
        view.findViewById<ImageView>(R.id.bubbleIcon)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.bubbleText)?.apply {
            text = label
            visibility = View.VISIBLE
        }
    }

    private fun restoreIdleBubble() {
        val view = bubbleView ?: return
        view.setBackgroundResource(R.drawable.bubble_idle)
        view.findViewById<ImageView>(R.id.bubbleIcon)?.visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.bubbleText)?.visibility = View.GONE
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundInternal()
        showBubble()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(stageTicker)
        handler.removeCallbacks(idleRestoreRunnable)
        removeView(bubbleView); bubbleView = null
        removeView(panelView); panelView = null
    }

    private fun startForegroundInternal() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_dollar)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    // ==========  BUBBLE  ==========

    private fun showBubble() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        val lp = baseParams().apply {
            width = dp(56); height = dp(56)
            gravity = Gravity.TOP or Gravity.START
            x = dp(12); y = dp(200)
        }
        wm.addView(view, lp)
        bubbleView = view
        bubbleParams = lp
        attachBubbleDrag(view, lp)
    }

    private fun attachBubbleDrag(view: View, lp: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f
        var downTime = 0L

        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchStartX = e.rawX; touchStartY = e.rawY
                    downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchStartX).toInt()
                    lp.y = startY + (e.rawY - touchStartY).toInt()
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(e.rawX - touchStartX)
                    val dy = abs(e.rawY - touchStartY)
                    val dt = System.currentTimeMillis() - downTime
                    if (dx < dp(8) && dy < dp(8) && dt < 400) {
                        onBubbleTap()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onBubbleTap() {
        if (panelView != null) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    // ==========  PANEL  ==========

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun showPanel() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        val lp = baseParams(focusable = true).apply {
            width = dp(320); height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            val b = bubbleParams
            x = (b?.x ?: dp(12)) + dp(60)
            y = (b?.y ?: dp(200))
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        wm.addView(view, lp)
        panelView = view
        panelParams = lp

        val payoutInput: EditText = view.findViewById(R.id.payoutInput)
        val kmInput: EditText = view.findViewById(R.id.kmInput)
        val minInput: EditText = view.findViewById(R.id.minInput)
        val calcBtn = view.findViewById<android.widget.Button>(R.id.calcBtn)
        val resetBtn = view.findViewById<android.widget.Button>(R.id.resetBtn)
        val closeBtn = view.findViewById<ImageView>(R.id.closeBtn)
        val resultBlock = view.findViewById<LinearLayout>(R.id.resultBlock)
        val verdictText = view.findViewById<TextView>(R.id.verdictText)
        val metricsText = view.findViewById<TextView>(R.id.metricsText)
        val reasonText = view.findViewById<TextView>(R.id.reasonText)
        val acceptBtn = view.findViewById<android.widget.Button>(R.id.acceptBtn)
        val rejectBtn = view.findViewById<android.widget.Button>(R.id.rejectBtn)

        updateStageBanner()
        handler.removeCallbacks(stageTicker)
        handler.postDelayed(stageTicker, 15_000)

        closeBtn.setOnClickListener { hidePanel() }

        resetBtn.setOnClickListener {
            payoutInput.setText("")
            kmInput.setText("")
            minInput.setText("")
            resultBlock.visibility = View.GONE
            currentRecordTimestamp = null
            payoutInput.requestFocus()
        }

        val onCalc = calc@{
            val p = payoutInput.text.toString().toDoubleOrNull()
            val k = kmInput.text.toString().toDoubleOrNull()
            val m = minInput.text.toString().toDoubleOrNull()
            if (p == null || k == null || m == null) {
                verdictText.text = "三项都要填"
                verdictText.setBackgroundResource(R.drawable.result_hold)
                metricsText.text = ""
                reasonText.text = ""
                resultBlock.visibility = View.VISIBLE
                return@calc
            }
            val base = Prefs.load(this)
            val idleSec = TimerState.idleSeconds(this)
            val stage = Stage.fromIdleMinutes((idleSec / 60).toInt())
            val d = Calculator.analyze(p, k, m, base, stage)

            verdictText.text = when (d.verdict) {
                Verdict.GO -> getString(R.string.result_go)
                Verdict.HOLD -> getString(R.string.result_hold)
                Verdict.NO -> getString(R.string.result_no)
            }
            verdictText.setBackgroundResource(when (d.verdict) {
                Verdict.GO -> R.drawable.result_go
                Verdict.HOLD -> R.drawable.result_hold
                Verdict.NO -> R.drawable.result_no
            })
            val eff = d.effectiveThresholds
            metricsText.text = buildString {
                append("\$/km   ${"%.2f".format(d.perKm)}  (阶段门槛 ${"%.2f".format(eff.perKm)})\n")
                append("\$/min  ${"%.2f".format(d.perMin)}  (阶段门槛 ${"%.2f".format(eff.perMin)})\n")
                append("净利润 \$${"%.2f".format(d.netProfit)}\n")
                append("折算时薪 \$${"%.1f".format(d.netHourly)}/h")
            }
            reasonText.text = d.reason

            val ts = System.currentTimeMillis()
            val record = OrderRecord(
                timestamp = ts,
                payout = p, km = k, minutes = m,
                perKm = d.perKm, perMin = d.perMin,
                netProfit = d.netProfit, netHourly = d.netHourly,
                verdict = d.verdict, choice = Choice.UNDECIDED,
                idleSecondsBefore = idleSec,
                stageLabel = d.stage.label
            )
            OrderHistory.append(this, record)
            currentRecordTimestamp = ts
            currentRecordMinutes = m

            resultBlock.visibility = View.VISIBLE
            hideKeyboard(view)
        }
        calcBtn.setOnClickListener { onCalc() }
        minInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                onCalc(); true
            } else false
        }

        acceptBtn.setOnClickListener {
            val ts = currentRecordTimestamp ?: return@setOnClickListener
            OrderHistory.updateChoice(this, ts, Choice.ACCEPTED)
            TimerState.markAccepted(this, currentRecordMinutes)
            currentRecordTimestamp = null
            android.widget.Toast.makeText(this, "已记录：接单", android.widget.Toast.LENGTH_SHORT).show()
            updateStageBanner()
            hidePanel()
        }

        rejectBtn.setOnClickListener {
            val ts = currentRecordTimestamp ?: return@setOnClickListener
            OrderHistory.updateChoice(this, ts, Choice.REJECTED)
            TimerState.markRejected(this)
            currentRecordTimestamp = null
            android.widget.Toast.makeText(this, "已记录：拒单", android.widget.Toast.LENGTH_SHORT).show()
            updateStageBanner()
            hidePanel()
        }

        payoutInput.requestFocus()
    }

    private fun hidePanel() {
        handler.removeCallbacks(stageTicker)
        removeView(panelView)
        panelView = null
        currentRecordTimestamp = null
    }

    private fun updateStageBanner() {
        val view = panelView ?: return
        val banner = view.findViewById<TextView>(R.id.stageBanner) ?: return
        val isBusy = TimerState.isBusy(this)
        if (isBusy) {
            val rem = TimerState.busyRemainingSeconds(this)
            val m = rem / 60; val s = rem % 60
            banner.text = "送单中 · 剩余 ${m}m ${s}s"
            return
        }
        val idleSec = TimerState.idleSeconds(this)
        val stage = Stage.fromIdleMinutes((idleSec / 60).toInt())
        val m = idleSec / 60; val s = idleSec % 60
        val base = Prefs.load(this)
        val eff = stage.applyTo(base)
        banner.text = if (stage == Stage.STARVING)
            "等单 ${m}m${s}s · 阶段 ${stage.label} · 接任何非负单"
        else
            "等单 ${m}m${s}s · 阶段 ${stage.label} · 门槛 \$${"%.2f".format(eff.perKm)}/km · \$${"%.2f".format(eff.minPayout)}/单"
    }

    // ==========  HELPERS  ==========

    @Suppress("DEPRECATION")
    private fun baseParams(focusable: Boolean = false): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun removeView(v: View?) {
        if (v == null) return
        try { wm.removeView(v) } catch (_: Exception) {}
    }

    private fun hideKeyboard(v: View) {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
    }

    private fun dp(v: Int): Int =
        (v * Resources.getSystem().displayMetrics.density).toInt()
}
