package com.owletmonitor.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

/**
 * MainActivity — appears in the Google TV launcher.
 *
 * Lets users open the system screensaver settings so they can select
 * "Owlet Monitor" as their active screensaver/dream.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOpenSettings = findViewById<Button>(R.id.btn_open_screensaver_settings)
        btnOpenSettings?.setOnClickListener {
            // Opens the system Display > Screen saver settings page where users
            // can choose and preview the Owlet Monitor dream.
            val intent = Intent(Settings.ACTION_DREAM_SETTINGS)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }

        // Show app version in the UI
        val versionView = findViewById<TextView>(R.id.tv_version)
        versionView?.text = getString(R.string.version_label)
    }
}
