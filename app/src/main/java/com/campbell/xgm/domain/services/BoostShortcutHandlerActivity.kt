package com.campbell.xgm.domain.services

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.campbell.xgm.domain.services.SpeedBoostManager

class BoostShortcutHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listName = intent?.getStringExtra("list_name")
        val boostManager = SpeedBoostManager(this)

        val appsToKill = if (listName.isNullOrEmpty()) {
            boostManager.getAppsToKill()
        } else {
            boostManager.getAppsToKillForList(listName)
        }

        if (appsToKill.isEmpty()) {
            Toast.makeText(this, "No background apps to kill", Toast.LENGTH_SHORT).show()
        } else {
            val accessibilityEnabled = com.campbell.xgm.util.PermissionUtils.isAccessibilityServiceEnabled(this)
            val ghostFingerEnabled = getSharedPreferences("game_mode_prefs", MODE_PRIVATE)
                .getBoolean("accessibility_force_stop", false)

            if (ghostFingerEnabled && accessibilityEnabled && SafetyInterceptor.instance != null) {
                SafetyInterceptor.startForceStop(appsToKill)
                boostManager.markAsClosed(appsToKill)
                Toast.makeText(this, "Force stopping ${appsToKill.size} apps...", Toast.LENGTH_SHORT).show()
            } else {
                boostManager.killApps(appsToKill)
                Toast.makeText(this, "Killed ${appsToKill.size} apps", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
