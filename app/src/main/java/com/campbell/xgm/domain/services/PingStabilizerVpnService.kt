package com.campbell.xgm.domain.services

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class PingStabilizerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var keepaliveJob: Job? = null

    companion object {
        const val ACTION_START_VPN = "com.campbell.xgm.START_VPN"
        const val ACTION_STOP_VPN = "com.campbell.xgm.STOP_VPN"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        private const val KEEPALIVE_INTERVAL_MS = 3000L
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
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(targetPackage)
                .addDisallowedApplication(packageName)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            vpnInterface = builder.establish()
            Log.i("PingStabilizer", "Ping Stabilizer VPN established")

            startKeepaliveLoop()
        } catch (e: Exception) {
            Log.e("PingStabilizer", "Failed to establish VPN: ${e.message}")
            stopSelf()
        }
    }

    private fun stopVpn() {
        Log.i("PingStabilizer", "Stopping Ping Stabilizer VPN")
        try {
            keepaliveJob?.cancel()
            keepaliveJob = null
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("PingStabilizer", "Error closing VPN interface: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopVpn()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun startKeepaliveLoop() {
        keepaliveJob = serviceScope.launch {
            var socket: DatagramSocket? = null
            try {
                val address = InetAddress.getByName("8.8.8.8")
                val sendData = byteArrayOf(0x00) // Minimal keepalive payload
                socket = DatagramSocket()
                socket.soTimeout = 3000
                protect(socket)

                while (isActive) {
                    try {
                        val packet = DatagramPacket(sendData, sendData.size, address, 53)
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.e("PingStabilizer", "Keepalive failed: ${e.message}")
                    }
                    delay(KEEPALIVE_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e("PingStabilizer", "Keepalive loop error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }
}
