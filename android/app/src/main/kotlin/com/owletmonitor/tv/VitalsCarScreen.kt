package com.owletmonitor.tv

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class VitalsCarScreen(carContext: CarContext) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private val vitalsListener: (VitalsData) -> Unit = { invalidate() }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            VitalsRepository.fetchNow()
            handler.postDelayed(this, VitalsFormatter.REFRESH_INTERVAL_MS)
        }
    }

    init {
        AppSettings.init(carContext)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.postDelayed(refreshRunnable, VitalsFormatter.REFRESH_INTERVAL_MS)
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

        fun icon(resId: Int): CarIcon =
            CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()

        if (data?.isCharging == true) {
            return GridTemplate.Builder()
                .setTitle(carContext.getString(R.string.app_name))
                .setSingleList(
                    ItemList.Builder()
                        .addItem(GridItem.Builder()
                            .setTitle(carContext.getString(R.string.status_charging))
                            .setText("–")
                            .setImage(icon(R.drawable.ic_car_sock), GridItem.IMAGE_TYPE_ICON)
                            .build())
                        .build()
                )
                .build()
        }

        val sleepStateStr = data?.sleepStateRaw?.let { VitalsFormatter.sleepStateText(carContext, it) } ?: "––"
        val sleepDurStr   = data?.sleepStartedAtMs?.let { VitalsFormatter.sleepDurationText(carContext, it) } ?: ""
        val sleepText     = if (sleepDurStr.isNotEmpty()) "$sleepStateStr\n$sleepDurStr"
                            else sleepStateStr

        return GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setSingleList(
                ItemList.Builder()
                    .addItem(GridItem.Builder()
                        .setTitle(carContext.getString(R.string.label_oxygen))
                        .setText(VitalsFormatter.oxygenText(data?.oxygenPercent))
                        .setImage(icon(R.drawable.ic_car_oxygen), GridItem.IMAGE_TYPE_ICON)
                        .build())
                    .addItem(GridItem.Builder()
                        .setTitle(carContext.getString(R.string.label_heart_rate))
                        .setText(VitalsFormatter.heartRateText(data?.heartRateBpm))
                        .setImage(icon(R.drawable.ic_car_heart_rate), GridItem.IMAGE_TYPE_ICON)
                        .build())
                    .addItem(GridItem.Builder()
                        .setTitle(carContext.getString(R.string.label_sleep_state))
                        .setText(sleepText)
                        .setImage(icon(R.drawable.ic_car_sleep), GridItem.IMAGE_TYPE_ICON)
                        .build())
                    .addItem(GridItem.Builder()
                        .setTitle(carContext.getString(R.string.label_sock_connection))
                        .setText(VitalsFormatter.sockText(carContext, data?.sockConnected))
                        .setImage(icon(R.drawable.ic_car_sock), GridItem.IMAGE_TYPE_ICON)
                        .build())
                    .addItem(GridItem.Builder()
                        .setTitle(carContext.getString(R.string.label_skin_temp))
                        .setText(VitalsFormatter.skinTempText(data?.skinTemp))
                        .setImage(icon(R.drawable.ic_car_temp), GridItem.IMAGE_TYPE_ICON)
                        .build())
                    .build()
            )
            .build()
    }
}
