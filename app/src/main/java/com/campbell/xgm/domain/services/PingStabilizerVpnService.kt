package com.campbell.xgm.domain.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class PingStabilizerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_START_VPN = "com.campbell.xgm.START_VPN"
        const val ACTION_STOP_VPN = "com.campbell.xgm.STOP_VPN"
        const val EXTRA_TARGET_PACKAGE = "target_package"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_VPN) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_VPN) {
            val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
            if (targetPackage != null) {
                startVpn(targetPackage)
            } else {
                Log.e("PingStabilizer", "Target package is null, cannot start VPN")
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startVpn(targetPackage: String) {
        if (vpnInterface != null) {
            Log.i("PingStabilizer", "VPN is already running")
            return
        }

        Log.i("PingStabilizer", "Starting Ping Stabilizer VPN. Bypassing: $targetPackage")
        try {
            val builder = Builder()
                .setSession("CampbellXGM Ping Stabilizer")
                .setMtu(1500)
                // Use a dummy address - this is a per-app VPN for keepalive only
                .addAddress("10.0.0.2", 32)
                // Only route specific traffic needed for keepalive
                .addRoute("0.0.0.0", 0)
                
                // Exclude the game and our own app - they use real network
                .addDisallowedApplication(targetPackage)
                .addDisallowedApplication(packageName)
                // Also exclude system apps to prevent breaking phone functionality
                .addDisallowedApplication("com.android.settings")
                .addDisallowedApplication("com.android.systemui")
                .addDisallowedApplication("com.google.android.gms")
                .addDisallowedApplication("com.android.phone")
                .addDisallowedApplication("com.android.server.telecom")

            // Allow bypass for critical system traffic
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            Log.i("PingStabilizer", "Ping Stabilizer VPN established")
        } catch (e: Exception) {
            Log.e("PingStabilizer", "Failed to establish VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.i("PingStabilizer", "Stopping Ping Stabilizer VPN")
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("PingStabilizer", "Error closing VPN interface: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
