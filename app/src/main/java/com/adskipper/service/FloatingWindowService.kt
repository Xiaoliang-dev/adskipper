package com.adskipper.service

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.adskipper.R
import com.adskipper.data.*
import kotlinx.coroutines.*

class FloatingWindowService : Service() {

    companion object {
        private const val TAG = "FloatingWindow"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_window_channel"

        const val ACTION_SHOW_FLOATING = "com.adskipper.ACTION_SHOW_FLOATING"
        const val ACTION_HIDE_FLOATING = "com.adskipper.ACTION_HIDE_FLOATING"
        const val ACTION_START_RECORDING = "com.adskipper.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.adskipper.ACTION_STOP_RECORDING"
        const val EXTRA_SELECTED_PACKAGE = "selected_package"

        var isFloatingShown = false
            private set
        var isRecording = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var overlayView: View? = null
    private var recordingCallback: ((NodeInfo) -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var selectedPackage: String = ""

    data class NodeInfo(
        val text: String,
        val viewId: String,
        val className: String,
        val bounds: String,
        val contentDesc: String,
        val packageName: String
    )

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SHOW_FLOATING -> showFloatingWindow()
                ACTION_HIDE_FLOATING -> hideFloatingWindow()
                ACTION_START_RECORDING -> {
                    selectedPackage = intent.getStringExtra(EXTRA_SELECTED_PACKAGE) ?: ""
                    startRecordingMode()
                }
                ACTION_STOP_RECORDING -> stopRecordingMode()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW_FLOATING)
            addAction(ACTION_HIDE_FLOATING)
            addAction(ACTION_START_RECORDING)
            addAction(ACTION_STOP_RECORDING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_SHOW_FLOATING -> showFloatingWindow()
            ACTION_HIDE_FLOATING -> hideFloatingWindow()
            ACTION_START_RECORDING -> {
                selectedPackage = intent.getStringExtra(EXTRA_SELECTED_PACKAGE) ?: ""
                startRecordingMode()
            }
            ACTION_STOP_RECORDING -> stopRecordingMode()
            else -> showFloatingWindow()
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮窗在后台运行"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AdSkipper 悬浮窗")
            .setContentText(if (isRecording) "正在录制规则..." else "悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "关闭",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, FloatingWindowService::class.java).apply {
                        action = ACTION_HIDE_FLOATING
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun showFloatingWindow() {
        if (floatingView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.floating_window, null)

        // Setup drag
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    // If it's a tap (not drag), toggle recording
                    if (Math.abs(dx) < 10 && Math.abs(dy) < 10) {
                        handleTap()
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, params)
        floatingView = view
        isFloatingShown = true
    }

    private fun handleTap() {
        val view = floatingView ?: return
        if (isRecording) {
            showSnackbar("录制模式已激活，请在目标广告上点击")
        } else {
            // Toggle recording mode directly (no app selection needed)
            val targetPkg = getForegroundPackage()
            if (targetPkg != null) {
                selectedPackage = targetPkg
                startRecordingMode(targetPkg)
                showSnackbar("录制开始，已选择 $targetPkg")
            } else {
                showSnackbar("无法获取当前应用，请先打开目标应用")
            }
        }
    }

    private fun showSnackbar(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent("com.adskipper.ACTION_SNACKBAR").apply {
                putExtra("message", message)
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
    }

    private fun showAppSelectionDialog() {
        // Launch main activity with recording flag
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("start_recording", true)
        }
        startActivity(intent)
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        isFloatingShown = false
        isRecording = false
        stopSelf()
    }

    fun startRecordingMode(packageName: String = selectedPackage) {
        // Use current foreground package if not specified
        val targetPkg = if (packageName.isBlank()) getForegroundPackage() ?: "" else packageName
        if (targetPkg.isBlank()) {
            Log.w(TAG, "Cannot start recording: no target package")
            return
        }
        selectedPackage = targetPkg
        isRecording = true

        // Show overlay to capture taps
        showRecordingOverlay()

        // Update notification
        startForeground(NOTIFICATION_ID, createNotification())
    }

    fun stopRecordingMode() {
        isRecording = false
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }

        // Update notification
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun showRecordingOverlay() {
        if (overlayView != null) return

        val displaySize = Point()
        windowManager.defaultDisplay.getRealSize(displaySize)

        val params = WindowManager.LayoutParams(
            displaySize.x,
            displaySize.y,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val view = View(this).apply {
            setBackgroundColor(0x22000000) // Semi-transparent
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    captureNodeAt(event.rawX.toInt(), event.rawY.toInt())
                    true
                } else {
                    true // Consume all events to let underlying app receive nothing during recording
                }
            }
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    private fun captureNodeAt(x: Int, y: Int) {
        // Use the running AccessibilityService instance to get node at position
        val rootNode = AdSkipAccessibilityService.rootInActiveWindowStatic ?: run {
            Log.w(TAG, "Cannot get root node - accessibility service not running?")
            sendCaptureError("无障碍服务未运行")
            return
        }

        val node = findNodeAtPosition(rootNode, x, y)
        if (node != null) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)

            val nodeInfo = NodeInfo(
                text = node.text?.toString() ?: "",
                viewId = node.viewIdResourceName ?: "",
                className = node.className?.toString() ?: "",
                bounds = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                contentDesc = node.contentDescription?.toString() ?: "",
                packageName = selectedPackage
            )

            node.recycle()

            // Send result back to UI
            val intent = Intent("com.adskipper.ACTION_NODE_CAPTURED").apply {
                putExtra("node_text", nodeInfo.text)
                putExtra("node_id", nodeInfo.viewId)
                putExtra("node_class", nodeInfo.className)
                putExtra("node_bounds", nodeInfo.bounds)
                putExtra("node_desc", nodeInfo.contentDesc)
                putExtra("node_package", nodeInfo.packageName)
                setPackage(packageName)
            }
            sendBroadcast(intent)

            // Stop recording after capture
            stopRecordingMode()

            // Show toast to confirm capture
            showCaptureToast()
        }

        rootNode.recycle()
    }

    private fun showCaptureToast() {
        try {
            val toast = android.widget.Toast.makeText(
                this,
                "已捕获元素: ${if (selectedPackage.isNotEmpty()) selectedPackage else "当前应用"}",
                android.widget.Toast.LENGTH_SHORT
            )
            toast.show()
        } catch (_: Exception) {}
    }

    private fun sendCaptureError(message: String) {
        val intent = Intent("com.adskipper.ACTION_NODE_CAPTURED").apply {
            putExtra("error", message)
            setPackage(packageName)
        }
        sendBroadcast(intent)
        try {
            val toast = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT)
            toast.show()
        } catch (_: Exception) {}
    }

    private fun findNodeAtPosition(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.contains(x, y)) {
            // Check children first (more specific)
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findNodeAtPosition(child, x, y)
                child.recycle()
                if (result != null) {
                    return result
                }
            }
            return AccessibilityNodeInfo.obtain(node)
        }

        return null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            hideFloatingWindow()
        } catch (_: Exception) {}
        try {
            unregisterReceiver(commandReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    private fun getForegroundPackage(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager?.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                currentTime - 1000 * 60 * 5,
                currentTime
            )
            if (stats != null) {
                val recentStats = stats.sortedByDescending { it.lastTimeUsed }
                if (recentStats.isNotEmpty()) {
                    val topPackage = recentStats[0].packageName
                    if (topPackage != packageName) return topPackage
                }
            }
        }
        return selectedPackage.ifBlank { null }
    }
}