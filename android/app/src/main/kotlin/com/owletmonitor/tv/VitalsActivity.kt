package com.owletmonitor.tv

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class VitalsActivity : Activity() {

    protected val handler = Handler(Looper.getMainLooper())

    private val vitalsListener: (VitalsData) -> Unit = { data -> updateDisplay(data) }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            VitalsRepository.fetchNow()
            handler.postDelayed(this, VitalsFormatter.REFRESH_INTERVAL_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
        handler.postDelayed(refreshRunnable, VitalsFormatter.REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        VitalsRepository.removeListener(vitalsListener)
    }

    protected fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etUrl    = view.findViewById<EditText>(R.id.et_prometheus_url)
        val etToken  = view.findViewById<EditText>(R.id.et_auth_token)
        val etUserId = view.findViewById<EditText>(R.id.et_user_id)
        etUrl.setText(AppSettings.prometheusUrl)
        etToken.setText(AppSettings.authToken)
        etUserId.setText(AppSettings.userId)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val url    = etUrl.text.toString().trim()
                val token  = etToken.text.toString().trim()
                val userId = etUserId.text.toString().trim()
                AppSettings.save(url, token, userId)
                checkEndpoint(url, userId, token)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkEndpoint(url: String, userId: String, token: String) {
        Toast.makeText(this, R.string.checking_endpoint, Toast.LENGTH_SHORT).show()
        PrometheusClient.fetchMetricNames(url, userId, token,
            onSuccess = { metrics ->
                AlertDialog.Builder(this)
                    .setTitle("${getString(R.string.metrics_dialog_title)} (${metrics.size})")
                    .setItems(metrics.toTypedArray(), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            },
            onError = { msg ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.connection_error_title)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        )
    }

    private fun updateDisplay(data: VitalsData) {
        val charging = data.isCharging
        findViewById<View>(R.id.tv_charging)?.visibility       = if (charging) View.VISIBLE else View.GONE
        findViewById<View>(R.id.container_metrics)?.visibility = if (charging) View.GONE else View.VISIBLE

        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.fetchedAtMs))
        findViewById<TextView>(R.id.tv_last_updated)?.text = getString(R.string.label_last_updated, time)

        if (charging) return

        findViewById<TextView>(R.id.tv_oxygen)?.text =
            VitalsFormatter.oxygenText(data.oxygenPercent)
        findViewById<TextView>(R.id.tv_heart_rate)?.text =
            VitalsFormatter.heartRateText(data.heartRateBpm)
        findViewById<TextView>(R.id.tv_sleep_state)?.text =
            data.sleepStateRaw?.let { VitalsFormatter.sleepStateText(this, it) } ?: "––"

        val tvDuration = findViewById<TextView>(R.id.tv_sleep_duration)
        val sleeping = data.sleepStartedAtMs != null
        tvDuration?.visibility = if (sleeping) View.VISIBLE else View.GONE
        if (sleeping) tvDuration?.text = VitalsFormatter.sleepDurationText(this, data.sleepStartedAtMs!!)

        val tvSock = findViewById<TextView>(R.id.tv_sock_connection)
        tvSock?.text = VitalsFormatter.sockText(this, data.sockConnected)
        tvSock?.setTextColor(if (data.sockConnected == false) 0xFFEF5350.toInt() else 0xFFA5D6A7.toInt())

        findViewById<TextView>(R.id.tv_skin_temp)?.text =
            VitalsFormatter.skinTempText(data.skinTemp)
        findViewById<TextView>(R.id.tv_status)?.text =
            if (data.oxygenPercent != null) getString(R.string.status_ok)
            else getString(R.string.status_waiting)
    }
}
