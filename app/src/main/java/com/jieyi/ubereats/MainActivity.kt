package com.jieyi.ubereats

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var thresholdPreview: TextView
    private lateinit var idleText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateIdle()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        thresholdPreview = findViewById(R.id.thresholdPreview)
        idleText = findViewById(R.id.idleText)

        findViewById<Button>(R.id.startBtn).setOnClickListener { tryStart() }
        findViewById<Button>(R.id.stopBtn).setOnClickListener { stopFloating() }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.historyBtn).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        findViewById<Button>(R.id.resetTimerBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.btn_reset_timer)
                .setMessage("确定清空等单计时？（用于：刚上线，想让阶段从正常开始）")
                .setPositiveButton("确定") { _, _ ->
                    TimerState.reset(this)
                    TimerState.touchActivity(this)
                    Toast.makeText(this, "计时已重置", Toast.LENGTH_SHORT).show()
                    updateIdle()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateThresholdPreview()
        updateIdle()
        handler.postDelayed(ticker, 5_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun updateStatus() {
        val running = FloatingService.isRunning
        statusText.text = if (running) getString(R.string.status_running)
                          else getString(R.string.status_stopped)
    }

    private fun updateThresholdPreview() {
        val t = Prefs.load(this)
        thresholdPreview.text = buildString {
            append("基准 \$/km  ${"%.2f".format(t.perKm)}\n")
            append("基准 \$/min ${"%.2f".format(t.perMin)}\n")
            append("基准 单价  ${"%.2f".format(t.minPayout)}\n")
            append("每公里成本 ${"%.2f".format(t.costPerKm)}\n")
            append("\n阶段乘数：\n")
            for (s in Stage.values()) {
                if (s == Stage.STARVING) {
                    append("  ${s.label.padEnd(4)} (${s.minutesMax}+ 分钟)：全接\n")
                } else {
                    val eff = s.applyTo(t)
                    val maxM = if (s.minutesMax == Int.MAX_VALUE) "+" else "${s.minutesMax}"
                    append("  ${s.label.padEnd(4)} (<$maxM 分钟)：\$/km ${"%.2f".format(eff.perKm)} · \$${"%.2f".format(eff.minPayout)}/单\n")
                }
            }
        }
    }

    private fun updateIdle() {
        val busy = TimerState.isBusy(this)
        if (busy) {
            val rem = TimerState.busyRemainingSeconds(this)
            idleText.text = "状态：送单中 · 剩余 ${rem / 60}m ${rem % 60}s"
            return
        }
        val s = TimerState.idleSeconds(this)
        val stage = Stage.fromIdleMinutes((s / 60).toInt())
        idleText.text = "等单时长 ${s / 60}m ${s % 60}s · 当前阶段 ${stage.label}"
    }

    private fun tryStart() {
        if (!Settings.canDrawOverlays(this)) {
            askOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        statusText.postDelayed({ updateStatus() }, 400)
    }

    private fun stopFloating() {
        stopService(Intent(this, FloatingService::class.java))
        statusText.postDelayed({ updateStatus() }, 200)
    }

    private fun askOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.perm_overlay_title)
            .setMessage(R.string.perm_overlay_msg)
            .setPositiveButton(R.string.perm_go) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton(R.string.perm_cancel, null)
            .show()
    }
}
