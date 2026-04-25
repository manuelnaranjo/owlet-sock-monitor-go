package com.owletmonitor.tv

import android.os.Handler
import android.os.Looper

// TODO: replace mock data with real fetch from Grafana Cloud Prometheus.
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
        val mock = VitalsData(
            oxygenPercent = 98,
            heartRateBpm = 125,
            fetchedAtMs = System.currentTimeMillis(),
        )
        latest = mock
        mainHandler.post {
            listeners.toList().forEach { it(mock) }
        }
    }
}
