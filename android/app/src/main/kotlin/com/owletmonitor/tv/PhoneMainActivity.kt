package com.owletmonitor.tv

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneMainActivity : Activity() {

    private val vitalsListener: (VitalsData) -> Unit = { data -> updateDisplay(data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_phone_main)
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            VitalsRepository.fetchNow()
        }
    }

    override fun onResume() {
        super.onResume()
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
    }

    override fun onPause() {
        super.onPause()
        VitalsRepository.removeListener(vitalsListener)
    }

    private fun updateDisplay(data: VitalsData) {
        findViewById<TextView>(R.id.tv_oxygen).text =
            data.oxygenPercent?.let { "$it %" } ?: "–– %"
        findViewById<TextView>(R.id.tv_heart_rate).text =
            data.heartRateBpm?.let { "$it bpm" } ?: "–– bpm"
        findViewById<TextView>(R.id.tv_status).text =
            if (data.oxygenPercent != null) getString(R.string.status_ok)
            else getString(R.string.status_waiting)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.fetchedAtMs))
        findViewById<TextView>(R.id.tv_last_updated).text =
            getString(R.string.label_last_updated, time)
    }
}
