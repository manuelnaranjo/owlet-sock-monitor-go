package com.owletmonitor.tv

import android.os.Handler
import android.os.Looper

object VitalsRepository {

    @Volatile var latest: VitalsData? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(VitalsData) -> Unit>()

    fun addListener(listener: (VitalsData) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (VitalsData) -> Unit) {
        listeners.remove(listener)
    }

    fun fetchNow() {
        val url   = AppSettings.prometheusUrl
        val token = AppSettings.authToken
        val uid   = AppSettings.userId
        if (url.isBlank()) {
            emit(VitalsData(null, null, null, null, null, null, null, false, System.currentTimeMillis()))
            return
        }
        PrometheusClient.fetchVitals(url, uid, token,
            onSuccess = { data -> emit(data) },
            onError   = { _    -> emit(VitalsData(null, null, null, null, null, null, null, false, System.currentTimeMillis())) },
        )
    }

    private fun emit(data: VitalsData) {
        latest = data
        mainHandler.post { listeners.toList().forEach { it(data) } }
    }
}
