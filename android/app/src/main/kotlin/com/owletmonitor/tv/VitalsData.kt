package com.owletmonitor.tv

data class VitalsData(
    val oxygenPercent: Int?,
    val heartRateBpm: Int?,
    val sleepStateRaw: Int?,      // raw owlet_ss at current time
    val sockConnected: Boolean?,  // true if owlet_sc >= 2
    val skinTemp: Float?,         // raw owlet_st
    val sleepStartedAtMs: Long?,  // null if not sleeping; derived from 24h history
    val isCharging: Boolean,      // true if owlet_charging_status > 0
    val fetchedAtMs: Long,
)
