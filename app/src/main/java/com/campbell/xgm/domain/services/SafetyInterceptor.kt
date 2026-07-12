package com.campbell.xgm.domain.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.net.toUri
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SafetyInterceptor : AccessibilityService() {

    companion object {
        private const val TAG = "SafetyInterceptor"
        const val ACTION_FORCE_STOP = "com.campbell.xgm.FORCE_STOP"
        const val EXTRA_PACKAGES = "packages_to_kill"

        @Volatile
        var instance: SafetyInterceptor? = null
            private set

        fun isRunning(): Boolean = instance != null

        // Resource IDs for Force Stop button across common Android versions/OEMs
        private val FORCE_STOP_IDS = intArrayOf(
            // Stock Android / AOSP
            android.R.id.button2, // Often used for negative/cancel buttons
        )

        // Text patterns to search for Force Stop button (handles OEM/locale variations)
        private val FORCE_STOP_TEXTS = listOf(
            "Force stop", "FORCE STOP", "Force Stop",
            "Forced stop", "Stop app", "Force-stop",
            "force stop", "强制停止", "강제 중지",
            "Éteindre", "Beenden", "Forzar cierre",
            "Forzado stop", "Forzare arresto"
        )

        // Text patterns for the confirmation dialog button
        private val CONFIRM_TEXTS = listOf(
            "OK", "FORCE STOP", "Ok", "Force stop",
            "Confirm", "确定", "확인", "Aceptar",
            "Bestätigen", "Conferma"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pendingPackages = CopyOnWriteArrayList<String>()
    private val isProcessing = AtomicBoolean(false)
    private val onCompleteCallback = AtomicReference<(() -> Unit)?>(null)

    private val forceStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_FORCE_STOP) {
                val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES)
                if (!packages.isNullOrEmpty()) {
                    forceStopApps(packages)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val filter = IntentFilter(ACTION_FORCE_STOP)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(forceStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(forceStopReceiver, filter)
        }
        Log.i(TAG, "SafetyInterceptor connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "Window changed to: $packageName")

        if (isProcessing.get() && pendingPackages.isNotEmpty()) {
            handleCurrentWindow(packageName)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    fun forceStopApps(packages: List<String>, onComplete: (() -> Unit)? = null) {
        onCompleteCallback.set(onComplete)
        if (packages.isEmpty()) {
            invokeCallback()
            return
        }
        pendingPackages.clear()
        pendingPackages.addAll(packages)
        isProcessing.set(true)
        processNextPackage()
    }

    private fun processNextPackage() {
        if (pendingPackages.isEmpty()) {
            isProcessing.set(false)
            Log.i(TAG, "All packages processed for force-stop")
            invokeCallback()
            return
        }

        val targetPkg = pendingPackages.removeAt(0)
        Log.i(TAG, "Force-stopping: $targetPkg")

        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$targetPkg".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings for $targetPkg: ${e.message}")
            handler.postDelayed({ processNextPackage() }, 300)
        }
    }

    private fun handleCurrentWindow(packageName: String) {
        handler.postDelayed({
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "rootInActiveWindow is null, skipping...")
                if (pendingPackages.isNotEmpty()) pendingPackages.removeAt(0)
                handler.postDelayed({ processNextPackage() }, 300)
                return@postDelayed
            }

            // Try to find Force Stop button using resource IDs first, then text search
            var forceStopNode = findNodeByResourceId(rootNode)
            if (forceStopNode == null) {
                for (text in FORCE_STOP_TEXTS) {
                    forceStopNode = findNodeByText(rootNode, text)
                    if (forceStopNode != null) break
                }
            }

            if (forceStopNode != null) {
                if (!forceStopNode.isEnabled) {
                    Log.i(TAG, "Force Stop already disabled for $packageName (app already stopped), skipping...")
                    if (pendingPackages.isNotEmpty()) pendingPackages.removeAt(0)
                    handler.postDelayed({ processNextPackage() }, 200)
                    return@postDelayed
                }
                Log.i(TAG, "Found Force Stop button, clicking...")
                performClick(forceStopNode)

                // Handle confirmation dialog after a delay
                handler.postDelayed({
                    val confirmRoot = rootInActiveWindow
                    if (confirmRoot == null) {
                        Log.w(TAG, "rootInActiveWindow is null during confirmation, skipping...")
                        if (pendingPackages.isNotEmpty()) pendingPackages.removeAt(0)
                        handler.postDelayed({ processNextPackage() }, 300)
                        return@postDelayed
                    }

                    var confirmNode: AccessibilityNodeInfo? = null
                    for (text in CONFIRM_TEXTS) {
                        confirmNode = findNodeByText(confirmRoot, text)
                        if (confirmNode != null) break
                    }

                    if (confirmNode != null) {
                        Log.i(TAG, "Confirming force stop...")
                        performClick(confirmNode)
                    } else {
                        Log.w(TAG, "Confirm button not found, skipping...")
                    }

                    if (pendingPackages.isNotEmpty()) pendingPackages.removeAt(0)
                    handler.postDelayed({ processNextPackage() }, 500)
                }, 500)
            } else {
                Log.w(TAG, "Force Stop button not found for $packageName, skipping...")
                if (pendingPackages.isNotEmpty()) pendingPackages.removeAt(0)
                handler.postDelayed({ processNextPackage() }, 300)
            }
        }, 800)
    }

    private fun invokeCallback() {
        onCompleteCallback.getAndSet(null)?.invoke()
    }

    private fun findNodeByResourceId(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Some Android versions expose the Force Stop button via known resource IDs
        val nodes = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/force_stop_button")
        return nodes?.firstOrNull()
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes?.firstOrNull()
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()

        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        try {
            handler.removeCallbacksAndMessages(null)
            unregisterReceiver(forceStopReceiver)
        } catch (_: Exception) {}
        invokeCallback()
        instance = null
        super.onDestroy()
    }
}
