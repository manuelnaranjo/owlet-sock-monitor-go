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
        val pane = Pane.Builder()
            .addRow(oxygenRow)
            .addRow(heartRateRow)
            .build()
        return PaneTemplate.Builder(pane)
            .setTitle(carContext.getString(R.string.app_name))
            .build()
    }
}
