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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class SafetyInterceptor : AccessibilityService() {

    companion object {
        private const val TAG = "SafetyInterceptor"
        const val ACTION_FORCE_STOP = "com.campbell.xgm.FORCE_STOP"
        const val EXTRA_PACKAGES = "packages_to_kill"

        @Volatile
        var instance: SafetyInterceptor? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val pendingPackages = Collections.synchronizedList(mutableListOf<String>())
    private val isProcessing = AtomicBoolean(false)
    private var onCompleteCallback: (() -> Unit)? = null

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
        this.onCompleteCallback = onComplete
        if (packages.isEmpty()) {
            onCompleteCallback?.invoke()
            onCompleteCallback = null
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
            onCompleteCallback?.invoke()
            onCompleteCallback = null
            return
        }

        val targetPkg = pendingPackages.first()
        Log.i(TAG, "Force-stopping: $targetPkg")

        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$targetPkg".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings for $targetPkg: ${e.message}")
            synchronized(pendingPackages) { if (pendingPackages.isNotEmpty()) pendingPackages.removeFirst() }
            handler.postDelayed({ processNextPackage() }, 300)
        }
    }

    private fun handleCurrentWindow(packageName: String) {
        handler.postDelayed({
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "rootInActiveWindow is null, skipping...")
                synchronized(pendingPackages) { if (pendingPackages.isNotEmpty()) pendingPackages.removeFirst() }
                handler.postDelayed({ processNextPackage() }, 300)
                return@postDelayed
            }

            // Try to find and click "Force Stop" button
            val forceStopNode = findNodeByText(rootNode, "Force stop")
                ?: findNodeByText(rootNode, "FORCE STOP")
                ?: findNodeByText(rootNode, "Force Stop")

            if (forceStopNode != null) {
                Log.i(TAG, "Found Force Stop button, clicking...")
                performClick(forceStopNode)

                // Handle confirmation dialog after a delay
                handler.postDelayed({
                    val confirmRoot = rootInActiveWindow
                    if (confirmRoot == null) {
                        Log.w(TAG, "rootInActiveWindow is null during confirmation, skipping...")
                        synchronized(pendingPackages) { if (pendingPackages.isNotEmpty()) pendingPackages.removeFirst() }
                        handler.postDelayed({ processNextPackage() }, 300)
                        return@postDelayed
                    }

                    val confirmNode = findNodeByText(confirmRoot, "OK")
                        ?: findNodeByText(confirmRoot, "FORCE STOP")
                        ?: findNodeByText(confirmRoot, "Ok")

                    if (confirmNode != null) {
                        Log.i(TAG, "Confirming force stop...")
                        performClick(confirmNode)
                    } else {
                        Log.w(TAG, "Confirm button not found, skipping...")
                    }

                    // Move to next package
                    synchronized(pendingPackages) { if (pendingPackages.isNotEmpty()) pendingPackages.removeFirst() }
                    handler.postDelayed({ processNextPackage() }, 500)
                }, 500)
            } else {
                // Button not found, might be a system app or different UI
                Log.w(TAG, "Force Stop button not found for $packageName, skipping...")
                synchronized(pendingPackages) { if (pendingPackages.isNotEmpty()) pendingPackages.removeFirst() }
                handler.postDelayed({ processNextPackage() }, 300)
            }
        }, 800)
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
        instance = null
        super.onDestroy()
    }
}
