package com.jieyi.ubereats

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var thresholdPreview: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        thresholdPreview = findViewById(R.id.thresholdPreview)

        findViewById<Button>(R.id.startBtn).setOnClickListener { tryStart() }
        findViewById<Button>(R.id.stopBtn).setOnClickListener { stopFloating() }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateThresholdPreview()
    }

    private fun updateStatus() {
        val running = FloatingService.isRunning
        statusText.text = if (running) getString(R.string.status_running)
                          else getString(R.string.status_stopped)
    }

    private fun updateThresholdPreview() {
        val t = Prefs.load(this)
        thresholdPreview.text = buildString {
            append("\$/km  门槛  ${"%.2f".format(t.perKm)}\n")
            append("\$/min 门槛  ${"%.2f".format(t.perMin)}\n")
            append("单价  最低  ${"%.2f".format(t.minPayout)}\n")
            append("每公里成本  ${"%.2f".format(t.costPerKm)}")
        }
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
