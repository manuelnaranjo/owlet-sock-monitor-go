package com.owletmonitor.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppSettings.init(this)

        findViewById<Button>(R.id.btn_open_screensaver_settings)?.setOnClickListener {
            val intent = Intent(Settings.ACTION_DREAM_SETTINGS)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btn_configure)?.setOnClickListener {
            showSettingsDialog()
        }

        val versionView = findViewById<TextView>(R.id.tv_version)
        versionView?.text = getString(R.string.version_label)
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etUrl = view.findViewById<EditText>(R.id.et_prometheus_url)
        val etToken = view.findViewById<EditText>(R.id.et_auth_token)
        val etUserId = view.findViewById<EditText>(R.id.et_user_id)

        etUrl.setText(AppSettings.prometheusUrl)
        etToken.setText(AppSettings.authToken)
        etUserId.setText(AppSettings.userId)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = etUrl.text.toString().trim()
                val token = etToken.text.toString().trim()
                val userId = etUserId.text.toString().trim()
                AppSettings.save(url, token, userId)
                checkEndpoint(url, userId, token)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun checkEndpoint(url: String, userId: String, token: String) {
        Toast.makeText(this, R.string.checking_endpoint, Toast.LENGTH_SHORT).show()
        PrometheusClient.fetchMetricNames(url, userId, token,
            onSuccess = { metrics ->
                AlertDialog.Builder(this)
                    .setTitle("${getString(R.string.metrics_dialog_title)} (${metrics.size})")
                    .setItems(metrics.toTypedArray(), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            },
            onError = { msg ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.connection_error_title)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        )
    }
}