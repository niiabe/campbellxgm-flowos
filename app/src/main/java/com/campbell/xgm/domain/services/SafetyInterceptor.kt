package com.campbell.xgm.domain.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SafetyInterceptor : AccessibilityService() {

    private var overlayManager: GhostFingerOverlay? = null

    companion object {
        private const val TAG = "SafetyInterceptor"
        const val ACTION_FORCE_STOP = "com.campbell.xgm.FORCE_STOP"
        const val EXTRA_PACKAGES = "packages_to_kill"
        private const val SHELL_TIMEOUT_MS = 3000L
        private const val TRANSITION_DELAY_MS = 1200L
        private const val TIMEOUT_MS = 6000L
        private const val CONFIRM_DELAY_MS = 500L
        private const val MAX_SCROLL_RETRIES = 2

        @Volatile
        var instance: SafetyInterceptor? = null
            private set

        private val FORCE_STOP_VIEW_IDS = listOf(
            "com.android.settings:id/force_stop_button",
            "com.android.settings:id/force_stop",
            "com.android.settings:id/force_stop_btn",
            "com.android.settings:id/force_stop_button_layout",
            "com.android.settings:id/force_stop_button_container",
            "com.android.settings:id/force_stop_button_right_icon",
            "com.android.settings:id/button_bar"
        )

        private val FORCE_STOP_TEXTS = listOf(
            "Force stop", "FORCE STOP", "Force Stop",
            "Forced stop", "Stop app", "Force-stop",
            "force stop", "Stop", "Halt",
            "强制停止", "강제 중지",
            "Éteindre", "Beenden", "Forzar cierre",
            "Forzado stop", "Forzare arresto",
            "Zakończ", "Завершить", "Zastavit"
        )

        private val CONFIRM_TEXTS = listOf(
            "OK", "FORCE STOP", "Ok", "Force stop",
            "Confirm", "确定", "확인", "Aceptar",
            "Bestätigen", "Conferma", "YES", "Yes"
        )

        private val packageQueue = mutableListOf<String>()
        private var isRunning = false
        private var totalAppsToClose = 0

        private var currentlyProcessingPackage: String? = null
        private var hasClickedMainButton = false
        private var hasClickedDialog = false
        private var isNavigating = false
        private var scrollRetries = 0

        private var onFinishedCallback: (() -> Unit)? = null

        fun startForceStop(packages: List<String>, onFinished: (() -> Unit)? = null) {
            onFinishedCallback = onFinished
            packageQueue.clear()
            packageQueue.addAll(packages)
            totalAppsToClose = packages.size
            isRunning = true
            scrollRetries = 0

            val svc = instance
            val useOverlay = svc?.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
                ?.getBoolean("ghost_finger_overlay", true) ?: true

            Handler(Looper.getMainLooper()).post {
                if (useOverlay) {
                    svc?.overlayManager?.showOverlay(totalAppsToClose)
                }
            }

            processNext()
        }

        fun stopForceStop() {
            packageQueue.clear()
            isRunning = false
            Handler(Looper.getMainLooper()).post {
                instance?.overlayManager?.hideOverlay()
            }
            onFinishedCallback?.invoke()
            onFinishedCallback = null

            instance?.let { svc ->
                val launchIntent = svc.packageManager.getLaunchIntentForPackage(svc.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    svc.startActivity(launchIntent)
                }
            }
        }

        private fun tryShellForceStop(pkg: String): Boolean {
            return try {
                val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", pkg))
                val exited = process.waitFor(SHELL_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (exited && process.exitValue() == 0) {
                    Log.i(TAG, "Shell force-stop succeeded for $pkg")
                    true
                } else {
                    Log.w(TAG, "Shell force-stop failed or timed out for $pkg")
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shell force-stop exception for $pkg: ${e.message}")
                false
            }
        }

        private fun processNext() {
            if (!isRunning) return

            val svc = instance
            if (svc == null) {
                stopForceStop()
                return
            }

            if (packageQueue.isEmpty()) {
                stopForceStop()
                return
            }

            val nextPackage = packageQueue.removeAt(0)

            hasClickedMainButton = false
            hasClickedDialog = false
            currentlyProcessingPackage = nextPackage
            isNavigating = true
            scrollRetries = 0
            val currentClosed = totalAppsToClose - packageQueue.size

            var userFriendlyName = nextPackage
            try {
                val pm = svc.packageManager
                val info = pm.getApplicationInfo(nextPackage, 0)
                userFriendlyName = pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {}

            val useOverlay = svc.getSharedPreferences("game_mode_prefs", Context.MODE_PRIVATE)
                .getBoolean("ghost_finger_overlay", true)
            Handler(Looper.getMainLooper()).post {
                if (useOverlay) {
                    svc.overlayManager?.updateProgress(currentClosed, totalAppsToClose, userFriendlyName)
                }
            }

            if (tryShellForceStop(nextPackage)) {
                Handler(Looper.getMainLooper()).postDelayed({ processNext() }, 100)
                return
            }

            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$nextPackage")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }
                svc.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start activity for $nextPackage: ${e.message}")
                processNext()
                return
            }

            Handler(Looper.getMainLooper()).postDelayed({
                isNavigating = false
                instance?.rootInActiveWindow?.let {
                    instance?.checkWindowContent(it)
                }
            }, TRANSITION_DELAY_MS)

            Handler(Looper.getMainLooper()).postDelayed({
                if (isRunning && currentlyProcessingPackage == nextPackage) {
                    Log.w(TAG, "Timeout for $nextPackage, skipping")
                    processNext()
                }
            }, TIMEOUT_MS)
        }

        private fun scrollDown(svc: SafetyInterceptor) {
            val path = Path().apply {
                moveTo(svc.resources.displayMetrics.widthPixels / 2f, svc.resources.displayMetrics.heightPixels * 0.7f)
                lineTo(svc.resources.displayMetrics.widthPixels / 2f, svc.resources.displayMetrics.heightPixels * 0.3f)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            svc.dispatchGesture(gesture, null, null)
        }
    }

    private val forceStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_FORCE_STOP) {
                val packages = intent.getStringArrayListExtra(EXTRA_PACKAGES)
                if (!packages.isNullOrEmpty()) {
                    startForceStop(packages)
                } else {
                    stopForceStop()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlayManager = GhostFingerOverlay(this)

        val filter = IntentFilter(ACTION_FORCE_STOP)
        androidx.core.content.ContextCompat.registerReceiver(
            this, forceStopReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.i(TAG, "SafetyInterceptor connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        overlayManager = null
        try {
            unregisterReceiver(forceStopReceiver)
        } catch (_: Exception) {}
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (isRunning) return true
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isRunning || event == null) return
        val rootNode = rootInActiveWindow ?: return
        checkWindowContent(rootNode)
    }

    private fun checkWindowContent(rootNode: AccessibilityNodeInfo) {
        val isDialog = findClickableNodeByText(rootNode, "Cancel") != null ||
                       findClickableNodeByText(rootNode, "OK") != null

        if (isDialog) {
            val confirmNode = findClickableNodeByText(rootNode, "OK")
                ?: findClickableNodeByText(rootNode, "Force stop")
                ?: findClickableNodeByText(rootNode, "YES")

            if (confirmNode != null && confirmNode.isEnabled) {
                if (!hasClickedDialog) {
                    hasClickedDialog = true
                    isNavigating = true

                    Handler(Looper.getMainLooper()).postDelayed({
                        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Handler(Looper.getMainLooper()).postDelayed({
                            processNext()
                        }, CONFIRM_DELAY_MS)
                    }, CONFIRM_DELAY_MS)
                }
            }
            return
        }

        val forceStopNode = findForceStopButton(rootNode)

        if (forceStopNode != null) {
            scrollRetries = 0
            if (!forceStopNode.isEnabled) {
                if (!isNavigating) {
                    isNavigating = true
                    processNext()
                }
            } else if (!hasClickedMainButton && !isNavigating) {
                hasClickedMainButton = true
                forceStopNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } else {
            if (!isNavigating && scrollRetries < MAX_SCROLL_RETRIES) {
                scrollRetries++
                val svc = instance ?: return
                scrollDown(svc)
                Handler(Looper.getMainLooper()).postDelayed({
                    instance?.rootInActiveWindow?.let { checkWindowContent(it) }
                }, 500)
            }
        }
    }

    private fun findForceStopButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (viewId in FORCE_STOP_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (nodes?.isNotEmpty() == true) {
                val node = nodes.first()
                if (node.isEnabled) return node
            }
        }

        for (text in FORCE_STOP_TEXTS) {
            val node = findClickableNodeByText(root, text)
            if (node != null) return node
        }

        return null
    }

    private fun findClickableNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in list) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString()
            if (nodeText != null && nodeText.contains(text, ignoreCase = true)) {
                if (node.isClickable) return node
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) return parent
                    parent = parent.parent
                }
            }
        }
        return null
    }

    override fun onInterrupt() {}
}
