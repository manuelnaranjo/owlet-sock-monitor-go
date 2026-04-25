package com.owletmonitor.tv

data class VitalsData(
    val oxygenPercent: Int?,
    val heartRateBpm: Int?,
    val fetchedAtMs: Long,
)
