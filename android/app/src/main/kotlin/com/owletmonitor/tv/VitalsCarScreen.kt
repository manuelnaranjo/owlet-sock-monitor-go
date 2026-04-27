package com.owletmonitor.tv

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class VitalsCarScreen(carContext: CarContext) : Screen(carContext) {

    private val vitalsListener: (VitalsData) -> Unit = { invalidate() }

    init {
        AppSettings.init(carContext)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                VitalsRepository.removeListener(vitalsListener)
            }
        })
        VitalsRepository.addListener(vitalsListener)
        VitalsRepository.fetchNow()
    }

    override fun onGetTemplate(): Template {
        val data = VitalsRepository.latest

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
