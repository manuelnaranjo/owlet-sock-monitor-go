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
                val sleepStart  = computeSleepStart(sleepSeries)
                val data = VitalsData(
                    oxygenPercent    = oxygen?.toInt(),
                    heartRateBpm     = heartRate?.toInt(),
                    sleepStateRaw    = sleepRaw,
                    sockConnected    = sockConn?.let { it >= 2 },
                    skinTemp         = skinTemp?.toFloat(),
                    sleepStartedAtMs = sleepStart,
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
            val start = now - 24 * 3600
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

    // Pure function — determines when the current sleep session started.
    // A sleep session ends only when the baby has been awake for >= 5 consecutive minutes.
    // Brief zeros (< 5 min) are treated as part of the ongoing sleep session.
    private fun computeSleepStart(series: List<Pair<Long, Int>>): Long? {
        if (series.isEmpty() || series.last().second == 0) return null

        var sleepSessionStartMs = series.last().first
        var zeroRunMs = 0L
        var zeroRunStartIdx = -1

        for (i in series.lastIndex - 1 downTo 0) {
            val (ts, v) = series[i]
            val nextTs  = series[i + 1].first

            if (v == 0) {
                if (zeroRunStartIdx == -1) zeroRunStartIdx = i
                zeroRunMs += (nextTs - ts)
                if (zeroRunMs >= 5 * 60 * 1000L) {
                    return series[zeroRunStartIdx + 1].first
                }
            } else {
                sleepSessionStartMs = ts
                zeroRunMs = 0L
                zeroRunStartIdx = -1
            }
        }
        return sleepSessionStartMs
    }

    private fun credentials(userId: String, token: String): String =
        Base64.encodeToString("$userId:$token".toByteArray(), Base64.NO_WRAP)
}
