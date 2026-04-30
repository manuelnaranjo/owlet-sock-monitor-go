package com.owletmonitor.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null
    private var driftX: ObjectAnimator? = null
    private var driftY: ObjectAnimator? = null

    private val vitalsListener: (VitalsData) -> Unit = { data -> updateDisplay(data) }

    companion object {
        private const val REFRESH_INTERVAL_MS = 5_000L
        private const val DRIFT_DURATION_MS = 20_000L
        private const val DRIFT_RANGE_DP = 40f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dream_layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppSettings.init(this)

        rootView = findViewById(android.R.id.content)

        findViewById<Button>(R.id.btn_configure_overlay)?.setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        startDrift()
    }

    override fun onResume() {
        super.onResume()
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
        scheduleRefresh()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        VitalsRepository.removeListener(vitalsListener)
    }

    override fun onStop() {
        super.onStop()
        driftX?.cancel()
        driftY?.cancel()
    }

    private fun updateDisplay(data: VitalsData) {
        val view = rootView ?: return

        val charging = data.isCharging
        view.findViewById<View>(R.id.tv_charging)?.visibility       = if (charging) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.container_metrics)?.visibility = if (charging) View.GONE else View.VISIBLE

        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.fetchedAtMs))
        view.findViewById<TextView>(R.id.tv_last_updated)?.text =
            getString(R.string.label_last_updated, time)

        if (charging) return

        view.findViewById<TextView>(R.id.tv_oxygen)?.text =
            data.oxygenPercent?.let { "$it %" } ?: "–– %"
        view.findViewById<TextView>(R.id.tv_heart_rate)?.text =
            data.heartRateBpm?.let { "$it bpm" } ?: "–– bpm"

        view.findViewById<TextView>(R.id.tv_sleep_state)?.text =
            data.sleepStateRaw?.let { sleepStateText(it) } ?: "––"

        val tvDuration = view.findViewById<TextView>(R.id.tv_sleep_duration)
        val sleeping = data.sleepStartedAtMs != null
        tvDuration?.visibility = if (sleeping) View.VISIBLE else View.GONE
        if (sleeping) tvDuration?.text = sleepDurationText(data.sleepStartedAtMs!!)

        val tvSock = view.findViewById<TextView>(R.id.tv_sock_connection)
        tvSock?.text = when (data.sockConnected) {
            true  -> getString(R.string.sock_connected)
            false -> getString(R.string.sock_disconnected)
            null  -> "––"
        }
        tvSock?.setTextColor(
            if (data.sockConnected == false) 0xFFEF5350.toInt() else 0xFFA5D6A7.toInt()
        )

        view.findViewById<TextView>(R.id.tv_skin_temp)?.text =
            data.skinTemp?.let { "%.1f°".format(it) } ?: "––"

        view.findViewById<TextView>(R.id.tv_status)?.text =
            if (data.oxygenPercent != null) getString(R.string.status_ok)
            else getString(R.string.status_waiting)
    }

    private fun sleepStateText(raw: Int): String = when {
        raw >= 15 -> getString(R.string.sleep_deep)
        raw >= 8  -> getString(R.string.sleep_medium)
        raw >= 1  -> getString(R.string.sleep_light)
        else      -> getString(R.string.sleep_not_sleeping)
    }

    private fun sleepDurationText(startedAtMs: Long): String {
        val mins = ((System.currentTimeMillis() - startedAtMs) / 60_000).toInt()
        return if (mins < 60) getString(R.string.sleeping_for_minutes, mins)
               else getString(R.string.sleeping_for_hours, mins / 60, mins % 60)
    }

    private fun startDrift() {
        val container = rootView?.findViewById<View>(R.id.widget_container) ?: return
        val density = resources.displayMetrics.density
        val range = DRIFT_RANGE_DP * density

        driftX = ObjectAnimator.ofFloat(container, View.TRANSLATION_X, -range, range).apply {
            duration = DRIFT_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        driftY = ObjectAnimator.ofFloat(container, View.TRANSLATION_Y, -range / 2f, range / 2f).apply {
            duration = (DRIFT_DURATION_MS * 1.3).toLong()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            VitalsRepository.fetchNow()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private fun scheduleRefresh() {
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etUrl = view.findViewById<EditText>(R.id.et_prometheus_url)
        val etToken = view.findViewById<EditText>(R.id.et_auth_token)
        val etUserId = view.findViewById<EditText>(R.id.et_user_id)

        etUrl.setText(AppSettings.prometheusUrl)
        etToken.setText(AppSettings.authToken)
        etUserId.setText(AppSettings.userId)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = etUrl.text.toString().trim()
                val token = etToken.text.toString().trim()
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
}
