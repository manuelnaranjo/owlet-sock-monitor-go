package com.owletmonitor.tv

import android.content.Context
import android.content.SharedPreferences

object AppSettings {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized)
            prefs = context.getSharedPreferences("owlet_settings", Context.MODE_PRIVATE)
    }

    var prometheusUrl: String
        get() = prefs.getString("prometheus_url", "") ?: ""
        set(v) { prefs.edit().putString("prometheus_url", v).apply() }

    var authToken: String
        get() = prefs.getString("auth_token", "") ?: ""
        set(v) { prefs.edit().putString("auth_token", v).apply() }

    var userId: String
        get() = prefs.getString("user_id", "") ?: ""
        set(v) { prefs.edit().putString("user_id", v).apply() }

    fun save(url: String, token: String, userId: String) {
        prefs.edit()
            .putString("prometheus_url", url)
            .putString("auth_token", token)
            .putString("user_id", userId)
            .apply()
    }
}
