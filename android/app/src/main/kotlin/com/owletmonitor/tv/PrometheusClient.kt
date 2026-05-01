package com.owletmonitor.tv

import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PrometheusClient {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetchMetricNames(
        endpointUrl: String,
        userId: String,
        token: String,
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit,
    ) {
        Thread {
            try {
                val url = URL("$endpointUrl/api/v1/label/__name__/values")
                val conn = url.openConnection() as HttpURLConnection
                val credentials = Base64.encodeToString("$userId:$token".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", "Basic $credentials")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val data = JSONObject(body).getJSONArray("data")
                    val metrics = buildList { for (i in 0 until data.length()) add(data.getString(i)) }
                    mainHandler.post { onSuccess(metrics) }
                } else {
                    val msg = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    mainHandler.post { onError(msg) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                mainHandler.post { onError(e.message ?: "Unknown error") }
            }
        }.start()
    }

    fun fetchVitals(
        endpointUrl: String,
        userId: String,
        token: String,
        onSuccess: (VitalsData) -> Unit,
        onError: (String) -> Unit,
    ) {
        Thread {
            try {
                val oxygen      = fetchInstantMetric(endpointUrl, userId, token, "owlet_oxygen_level")
                val heartRate   = fetchInstantMetric(endpointUrl, userId, token, "owlet_heart_rate")
                val sockConn    = fetchInstantMetric(endpointUrl, userId, token, "owlet_sc")
                val skinTemp    = fetchInstantMetric(endpointUrl, userId, token, "owlet_st")
                val charging    = fetchInstantMetric(endpointUrl, userId, token, "owlet_charging_status")
                val sleepSeries = fetchSleepHistory(endpointUrl, userId, token)
                val sleepRaw    = sleepSeries.lastOrNull()?.second
                val stateInfo   = computeStateStart(sleepSeries)
                val data = VitalsData(
                    oxygenPercent    = oxygen?.toInt(),
                    heartRateBpm     = heartRate?.toInt(),
                    sleepStateRaw    = sleepRaw,
                    sockConnected    = sockConn?.let { it >= 2 },
                    skinTemp         = skinTemp?.toFloat(),
                    sleepStartedAtMs = if (stateInfo.isSleeping) stateInfo.startedAtMs else null,
                    awakeStartedAtMs = if (!stateInfo.isSleeping) stateInfo.startedAtMs else null,
                    isCharging       = (charging ?: 0.0) > 0,
                    fetchedAtMs      = System.currentTimeMillis(),
                )
                mainHandler.post { onSuccess(data) }
            } catch (e: Exception) {
                mainHandler.post { onError(e.message ?: "Unknown error") }
            }
        }.start()
    }

    // Synchronous — must be called from a background thread.
    private fun fetchInstantMetric(endpointUrl: String, userId: String, token: String, metric: String): Double? {
        return try {
            val url = URL("$endpointUrl/api/v1/query?query=${URLEncoder.encode(metric, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Basic ${credentials(userId, token)}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val result = JSONObject(body).getJSONObject("data").getJSONArray("result")
                if (result.length() > 0) result.getJSONObject(0).getJSONArray("value").getString(1).toDoubleOrNull()
                else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Synchronous — must be called from a background thread.
    private fun fetchSleepHistory(endpointUrl: String, userId: String, token: String): List<Pair<Long, Int>> {
        return try {
            val now   = System.currentTimeMillis() / 1000
            val start = now - 12 * 3600
            val url = URL("$endpointUrl/api/v1/query_range?query=owlet_ss&start=$start&end=$now&step=60")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Basic ${credentials(userId, token)}")
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) { conn.disconnect(); return emptyList() }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val result = JSONObject(body).getJSONObject("data").getJSONArray("result")
            if (result.length() == 0) return emptyList()
            val values = result.getJSONObject(0).getJSONArray("values")
            buildList {
                for (i in 0 until values.length()) {
                    val entry = values.getJSONArray(i)
                    val ts = (entry.getDouble(0) * 1000).toLong()
                    val v  = entry.getString(1).toIntOrNull() ?: 0
                    add(ts to v)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private data class StateInfo(val isSleeping: Boolean, val startedAtMs: Long?)

    // Two-phase algorithm:
    // Phase 1 — scan latest→oldest for the first run of 3 consecutive same-state samples to
    //            confirm the current state (handles noisy recent readings).
    // Phase 2 — walk further back from that confirmed point to find when a prior opposite-state
    //            run lasted >= 5 min, marking the true state transition.
    // Sleep threshold: owlet_ss > 2 → sleeping, <= 2 → awake.
    private fun computeStateStart(series: List<Pair<Long, Int>>): StateInfo {
        if (series.size < 3) return StateInfo(false, null)

        // Phase 1: find first run of 3 consecutive same-state samples from the end
        var runState    = series.last().second > 2
        var runCount    = 1
        var runStartIdx = series.lastIndex
        var confirmedState: Boolean? = null
        var confirmedStartIdx = -1

        for (i in series.lastIndex - 1 downTo 0) {
            val state = series[i].second > 2
            if (state == runState) {
                runCount++
                runStartIdx = i
                if (runCount >= 3) { confirmedState = runState; confirmedStartIdx = runStartIdx; break }
            } else {
                runState = state; runCount = 1; runStartIdx = i
            }
        }
        if (confirmedState == null) return StateInfo(false, null)

        val currentIsSleeping = confirmedState
        // Phase 2: walk backwards from confirmedStartIdx to find prior state transition
        var stateStartMs      = series[confirmedStartIdx].first
        var oppositeRunMs     = 0L
        var oppositeRunStartIdx = -1

        for (i in confirmedStartIdx - 1 downTo 0) {
            val (ts, v) = series[i]
            val nextTs  = series[i + 1].first
            if ((v > 2) != currentIsSleeping) {
                if (oppositeRunStartIdx == -1) oppositeRunStartIdx = i
                oppositeRunMs += (nextTs - ts)
                if (oppositeRunMs >= 5 * 60 * 1000L)
                    return StateInfo(currentIsSleeping, series[oppositeRunStartIdx + 1].first)
            } else {
                stateStartMs = ts; oppositeRunMs = 0L; oppositeRunStartIdx = -1
            }
        }
        return StateInfo(currentIsSleeping, stateStartMs)
    }

    private fun credentials(userId: String, token: String): String =
        Base64.encodeToString("$userId:$token".toByteArray(), Base64.NO_WRAP)
}
