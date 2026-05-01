package com.owletmonitor.tv

import android.content.Context

object VitalsFormatter {

    const val REFRESH_INTERVAL_MS = 5_000L

    fun oxygenText(oxygenPercent: Int?): String =
        oxygenPercent?.let { "$it %" } ?: "–– %"

    fun heartRateText(heartRateBpm: Int?): String =
        heartRateBpm?.let { "$it bpm" } ?: "–– bpm"

    fun skinTempText(skinTemp: Float?): String =
        skinTemp?.let { "%.1f °C".format(it) } ?: "–– °C"

    fun sockText(ctx: Context, sockConnected: Boolean?): String = when (sockConnected) {
        true  -> ctx.getString(R.string.sock_connected)
        false -> ctx.getString(R.string.sock_disconnected)
        null  -> "––"
    }

    fun sleepStateText(ctx: Context, raw: Int): String = when {
        raw >= 15 -> ctx.getString(R.string.sleep_deep)
        raw >= 8  -> ctx.getString(R.string.sleep_medium)
        raw >= 1  -> ctx.getString(R.string.sleep_light)
        else      -> ctx.getString(R.string.sleep_not_sleeping)
    }

    fun sleepDurationText(ctx: Context, startedAtMs: Long): String {
        val mins = ((System.currentTimeMillis() - startedAtMs) / 60_000).toInt()
        return if (mins < 60) ctx.getString(R.string.sleeping_for_minutes, mins)
               else ctx.getString(R.string.sleeping_for_hours, mins / 60, mins % 60)
    }
}
