package com.owletmonitor.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OwletDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private var rootView: View? = null

    private var driftX: ObjectAnimator? = null
    private var driftY: ObjectAnimator? = null

    private val vitalsListener: (VitalsData) -> Unit = { data -> updateDisplay(data) }

    companion object {
        private const val REFRESH_INTERVAL_MS = 30_000L
        private const val DRIFT_DURATION_MS = 20_000L
        private const val DRIFT_RANGE_DP = 40f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isInteractive = false

        val view = LayoutInflater.from(this).inflate(R.layout.dream_layout, null)
        rootView = view
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        startDrift()
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
        scheduleRefresh()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        VitalsRepository.removeListener(vitalsListener)
        handler.removeCallbacksAndMessages(null)
        driftX?.cancel()
        driftY?.cancel()
    }

    private fun updateDisplay(data: VitalsData) {
        val view = rootView ?: return
        view.findViewById<TextView>(R.id.tv_oxygen)?.text =
            data.oxygenPercent?.let { "$it %" } ?: "–– %"
        view.findViewById<TextView>(R.id.tv_heart_rate)?.text =
            data.heartRateBpm?.let { "$it bpm" } ?: "–– bpm"
        view.findViewById<TextView>(R.id.tv_status)?.text =
            if (data.oxygenPercent != null) getString(R.string.status_ok)
            else getString(R.string.status_waiting)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(data.fetchedAtMs))
        view.findViewById<TextView>(R.id.tv_last_updated)?.text =
            getString(R.string.label_last_updated, time)
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
}
