package com.jieyi.ubereats

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val perKm = findViewById<EditText>(R.id.perKmInput)
        val perMin = findViewById<EditText>(R.id.perMinInput)
        val minPay = findViewById<EditText>(R.id.minPayoutInput)
        val costKm = findViewById<EditText>(R.id.costPerKmInput)

        fun fill(t: Thresholds) {
            perKm.setText("%.2f".format(t.perKm))
            perMin.setText("%.2f".format(t.perMin))
            minPay.setText("%.2f".format(t.minPayout))
            costKm.setText("%.2f".format(t.costPerKm))
        }

        fill(Prefs.load(this))

        findViewById<Button>(R.id.saveBtn).setOnClickListener {
            val t = Thresholds(
                perKm = perKm.text.toString().toDoubleOrNull() ?: 1.00,
                perMin = perMin.text.toString().toDoubleOrNull() ?: 0.40,
                minPayout = minPay.text.toString().toDoubleOrNull() ?: 4.50,
                costPerKm = costKm.text.toString().toDoubleOrNull() ?: 0.13
            )
            Prefs.save(this, t)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.resetSettingsBtn).setOnClickListener {
            Prefs.resetDefaults(this)
            fill(Prefs.load(this))
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
