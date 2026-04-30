package com.owletmonitor.tv

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button

class MainActivity : VitalsActivity() {

    private var driftX: ObjectAnimator? = null
    private var driftY: ObjectAnimator? = null

    companion object {
        private const val DRIFT_DURATION_MS = 20_000L
        private const val DRIFT_RANGE_DP    = 40f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dream_layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppSettings.init(this)
        findViewById<Button>(R.id.btn_configure_overlay)?.setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        startDrift()
    }

    override fun onStop() {
        super.onStop()
        driftX?.cancel()
        driftY?.cancel()
    }

    private fun startDrift() {
        val container = findViewById<View>(R.id.widget_container) ?: return
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
}
