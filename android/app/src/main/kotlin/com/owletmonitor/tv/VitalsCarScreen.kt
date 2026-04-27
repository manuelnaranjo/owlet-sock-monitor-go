package com.owletmonitor.tv

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class VitalsCarScreen(carContext: CarContext) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private val vitalsListener: (VitalsData) -> Unit = { invalidate() }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            VitalsRepository.fetchNow()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 5_000L
    }

    init {
        AppSettings.init(carContext)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
            }
            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacksAndMessages(null)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                VitalsRepository.removeListener(vitalsListener)
            }
        })
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
    }

    override fun onGetTemplate(): Template {
        val data = VitalsRepository.latest

        if (data?.isCharging == true) {
            val pane = Pane.Builder()
                .addRow(Row.Builder()
                    .setTitle(carContext.getString(R.string.status_charging))
                    .build())
                .build()
            return PaneTemplate.Builder(pane)
                .setTitle(carContext.getString(R.string.app_name))
                .build()
        }

        val oxygenRow = Row.Builder()
            .setTitle(carContext.getString(R.string.label_oxygen))
            .addText(data?.oxygenPercent?.let { "$it %" } ?: "–– %")
            .build()

        val heartRateRow = Row.Builder()
            .setTitle(carContext.getString(R.string.label_heart_rate))
            .addText(data?.heartRateBpm?.let { "$it bpm" } ?: "–– bpm")
            .build()

        val sleepStateStr = data?.sleepStateRaw?.let { sleepStateText(it) } ?: "––"
        val sleepDurStr   = data?.sleepStartedAtMs?.let { sleepDurationText(it) } ?: ""
        val sleepRow = Row.Builder()
            .setTitle(carContext.getString(R.string.label_sleep_state))
            .addText(sleepStateStr)
            .apply { if (sleepDurStr.isNotEmpty()) addText(sleepDurStr) }
            .build()

        val sockRow = Row.Builder()
            .setTitle(carContext.getString(R.string.label_sock_connection))
            .addText(data?.sockConnected?.let {
                if (it) carContext.getString(R.string.sock_connected)
                else    carContext.getString(R.string.sock_disconnected)
            } ?: "––")
            .build()

        val pane = Pane.Builder()
            .addRow(oxygenRow)
            .addRow(heartRateRow)
            .addRow(sleepRow)
            .addRow(sockRow)
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.app_name))
            .build()
    }

    private fun sleepStateText(raw: Int): String = when {
        raw >= 15 -> carContext.getString(R.string.sleep_deep)
        raw >= 8  -> carContext.getString(R.string.sleep_medium)
        raw >= 1  -> carContext.getString(R.string.sleep_light)
        else      -> carContext.getString(R.string.sleep_not_sleeping)
    }

    private fun sleepDurationText(startedAtMs: Long): String {
        val mins = ((System.currentTimeMillis() - startedAtMs) / 60_000).toInt()
        return if (mins < 60) carContext.getString(R.string.sleeping_for_minutes, mins)
               else carContext.getString(R.string.sleeping_for_hours, mins / 60, mins % 60)
    }
}
