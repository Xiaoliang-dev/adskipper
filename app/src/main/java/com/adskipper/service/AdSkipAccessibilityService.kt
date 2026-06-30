package com.adskipper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.adskipper.data.RuleEntity
import com.adskipper.data.RuleManager
import com.adskipper.util.SettingsManager
import kotlinx.coroutines.*
import java.util.regex.PatternSyntaxException

class AdSkipAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdSkipService"
        const val ACTION_SERVICE_STATUS = "com.adskipper.ACTION_SERVICE_STATUS"
        const val EXTRA_SERVICE_RUNNING = "service_running"
        const val ACTION_STOP_SERVICE = "com.adskipper.ACTION_STOP_SERVICE"
        const val ACTION_START_SERVICE = "com.adskipper.ACTION_START_SERVICE"

        var isRunning: Boolean = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var ruleManager: RuleManager
    private lateinit var settingsManager: SettingsManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastSkippedPackage: String? = null
    private var lastSkipTime: Long = 0
    private val skipCooldownMs = 500 // Prevent double-skipping

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STOP_SERVICE -> {
                    Log.d(TAG, "Received stop command")
                    disableSelf()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ruleManager = RuleManager.getInstance(this)
        settingsManager = SettingsManager(this)

        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_SERVICE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        isRunning = true
        broadcastStatus()

        serviceScope.launch {
            settingsManager.setServiceEnabled(true)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName?.toString() ?: return
                val rootNode = rootInActiveWindow ?: return

                serviceScope.launch {
                    try {
                        val rules = ruleManager.getRulesForPackage(packageName)
                        if (rules.isNotEmpty()) {
                            checkAndSkip(rootNode, rules)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing rules", e)
                    } finally {
                        rootNode.recycle()
                    }
                }
            }
        }
    }

    private suspend fun checkAndSkip(rootNode: AccessibilityNodeInfo, rules: List<RuleEntity>) {
        for (rule in rules) {
            if (!rule.enabled) continue

            val matchedNode = findMatchingNode(rootNode, rule)
            if (matchedNode != null) {
                val now = System.currentTimeMillis()
                if (lastSkippedPackage == rule.packageName && now - lastSkipTime < skipCooldownMs) {
                    matchedNode.recycle()
                    continue
                }

                if (rule.delayMs > 0) {
                    delay(rule.delayMs)
                }

                performSkipAction(matchedNode, rule)

                lastSkippedPackage = rule.packageName
                lastSkipTime = System.currentTimeMillis()

                ruleManager.recordRuleTrigger(rule.id)

                handler.post {
                    serviceScope.launch {
                        val settings = settingsManager.settings
                        if (settings.showToast) {
                            Toast.makeText(this@AdSkipAccessibilityService, "已跳过广告", Toast.LENGTH_SHORT).show()
                        }
                        if (settings.vibrate) {
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            vibrator?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    it.vibrate(100)
                                }
                            }
                        }
                    }
                }

                matchedNode.recycle()
                return
            }
        }
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo, rule: RuleEntity): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText("")
        // Actually, let's traverse the tree properly
        return findNodeRecursive(root, rule)
    }

    private fun findNodeRecursive(node: AccessibilityNodeInfo, rule: RuleEntity): AccessibilityNodeInfo? {
        if (matchesRule(node, rule)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeRecursive(child, rule)
            child.recycle()
            if (result != null) {
                return result
            }
        }

        return null
    }

    private fun matchesRule(node: AccessibilityNodeInfo, rule: RuleEntity): Boolean {
        // Check text condition
        if (rule.useText && rule.targetText.isNotBlank()) {
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
            if (!matches(nodeText, rule.targetText, rule.textMatchType)) {
                return false
            }
        }

        // Check ID condition
        if (rule.useId && rule.targetId.isNotBlank()) {
            val viewId = node.viewIdResourceName ?: ""
            if (!matches(viewId, rule.targetId, rule.idMatchType)) {
                return false
            }
        }

        // Check class name condition
        if (rule.useClassName && rule.targetClassName.isNotBlank()) {
            val className = node.className?.toString() ?: ""
            if (!matches(className, rule.targetClassName, rule.classMatchType)) {
                return false
            }
        }

        // Check bounds condition
        if (rule.useBounds && rule.targetBounds.isNotBlank()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val boundsStr = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
            if (boundsStr != rule.targetBounds) {
                return false
            }
        }

        // Check content description
        if (rule.useContentDesc && rule.targetContentDesc.isNotBlank()) {
            val contentDesc = node.contentDescription?.toString() ?: ""
            if (!matches(contentDesc, rule.targetContentDesc, rule.contentDescMatchType)) {
                return false
            }
        }

        // Check after text (sibling text)
        if (rule.useAfterText && rule.targetAfterText.isNotBlank()) {
            // This requires checking parent's children
            val parent = node.parent ?: return false
            var foundCurrent = false
            var matchFound = false
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling == node) {
                    foundCurrent = true
                    sibling.recycle()
                    continue
                }
                if (foundCurrent) {
                    val siblingText = sibling.text?.toString() ?: ""
                    if (matches(siblingText, rule.targetAfterText, rule.afterTextMatchType)) {
                        matchFound = true
                        sibling.recycle()
                        break
                    }
                }
                sibling.recycle()
            }
            parent.recycle()
            if (!matchFound) return false
        }

        return true
    }

    private fun matches(text: String, pattern: String, matchType: String): Boolean {
        return when (matchType) {
            "exact" -> text == pattern
            "contains" -> text.contains(pattern, ignoreCase = true)
            "starts_with" -> text.startsWith(pattern, ignoreCase = true)
            "ends_with" -> text.endsWith(pattern, ignoreCase = true)
            "regex" -> {
                try {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(text)
                } catch (e: PatternSyntaxException) {
                    false
                }
            }
            else -> text.contains(pattern, ignoreCase = true)
        }
    }

    private fun performSkipAction(node: AccessibilityNodeInfo, rule: RuleEntity) {
        val targetNode = if (rule.clickParent) {
            val parent = node.parent
            node.recycle()
            parent
        } else {
            node
        } ?: return

        when (rule.actionType) {
            "click" -> {
                if (targetNode.isClickable) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    clickParentIfPossible(targetNode)
                }
            }
            "click_parent" -> {
                val parent = targetNode.parent
                parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent?.recycle()
            }
            "swipe_left" -> performSwipe(targetNode, SwipeDirection.LEFT)
            "swipe_right" -> performSwipe(targetNode, SwipeDirection.RIGHT)
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "custom_click" -> {
                if (rule.customClickX >= 0 && rule.customClickY >= 0) {
                    performClick(rule.customClickX.toFloat(), rule.customClickY.toFloat())
                } else {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            else -> targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        if (targetNode != node) {
            targetNode.recycle()
        }
    }

    private fun clickParentIfPossible(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) {
                current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                current.recycle()
                return
            }
            val parent = current.parent
            current.recycle()
            current = parent
            depth++
        }
        current?.recycle()
    }

    private enum class SwipeDirection { LEFT, RIGHT }

    private fun performSwipe(node: AccessibilityNodeInfo, direction: SwipeDirection) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        val path = Path()
        path.moveTo(centerX, centerY)
        when (direction) {
            SwipeDirection.LEFT -> path.lineTo(centerX - rect.width(), centerY)
            SwipeDirection.RIGHT -> path.lineTo(centerX + rect.width(), centerY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun performClick(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service unbound")
        isRunning = false
        broadcastStatus()

        serviceScope.launch {
            settingsManager.setServiceEnabled(false)
        }

        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        broadcastStatus()
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        serviceScope.cancel()
    }

    private fun broadcastStatus() {
        val intent = Intent(ACTION_SERVICE_STATUS).apply {
            putExtra(EXTRA_SERVICE_RUNNING, isRunning)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}