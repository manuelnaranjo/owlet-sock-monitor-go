package com.owletmonitor.tv

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu

class PhoneMainActivity : VitalsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        setContentView(R.layout.activity_phone_main)
        findViewById<View>(R.id.btn_menu).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(getString(R.string.btn_configure))
            popup.setOnMenuItemClickListener { showSettingsDialog(); true }
            popup.show()
        }
    }
}
