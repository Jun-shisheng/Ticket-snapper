package com.ticketsnap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var box1View: FrameLayout? = null
    private var box2View: FrameLayout? = null
    private var toggleView: FrameLayout? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var stateRefreshRunnable: Runnable? = null
    private var stopReceiver: BroadcastReceiver? = null

    companion object {
        const val ACTION_STOP = "com.ticketsnap.STOP_SERVICE"
        const val BOX_SIZE_DP = 72
        const val TOGGLE_SIZE_DP = 56
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "overlay_channel"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val size = Point()
        windowManager?.defaultDisplay?.getRealSize(size)
        screenWidth = size.x
        screenHeight = size.y

        createNotificationChannel()
        registerStopReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        showBoxes()
        startStateRefreshing()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopStateRefreshing()
        unregisterStopReceiver()
        removeAllViews()
        super.onDestroy()
    }

    private fun registerStopReceiver() {
        stopReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                stopEverything()
            }
        }
        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP),
            Context.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterStopReceiver() {
        stopReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        stopReceiver = null
    }

    private fun stopEverything() {
        TapAccessibilityService.instance?.stopClicking()
        PreferencesManager.isRunning = false
        stopSelf()
    }

    private fun showBoxes() {
        removeAllViews()

        val boxSizePx = (BOX_SIZE_DP * resources.displayMetrics.density).toInt()
        val toggleSizePx = (TOGGLE_SIZE_DP * resources.displayMetrics.density).toInt()

        box1View = createBox(boxSizePx, 1).also { box ->
            val x = if (PreferencesManager.box1X < 0) screenWidth - boxSizePx - 48
            else PreferencesManager.box1X.toInt()
            val y = if (PreferencesManager.box1Y < 0) screenHeight - boxSizePx - 200
            else PreferencesManager.box1Y.toInt()
            addViewToWindow(box, x, y, boxSizePx, boxSizePx, hasFocus = false)
            PreferencesManager.box1X = x.toFloat()
            PreferencesManager.box1Y = y.toFloat()
        }

        box2View = createBox(boxSizePx, 2).also { box ->
            val x = if (PreferencesManager.box2X < 0) screenWidth / 2 - boxSizePx / 2
            else PreferencesManager.box2X.toInt()
            val y = if (PreferencesManager.box2Y < 0) screenHeight / 2 - boxSizePx / 2
            else PreferencesManager.box2Y.toInt()
            addViewToWindow(box, x, y, boxSizePx, boxSizePx, hasFocus = false)
            PreferencesManager.box2X = x.toFloat()
            PreferencesManager.box2Y = y.toFloat()
        }

        toggleView = createToggle(toggleSizePx).also { btn ->
            val x = if (PreferencesManager.toggleX < 0) 48
            else PreferencesManager.toggleX.toInt()
            val y = if (PreferencesManager.toggleY < 0) screenHeight / 5
            else PreferencesManager.toggleY.toInt()
            addViewToWindow(btn, x, y, toggleSizePx, toggleSizePx, hasFocus = true)
            PreferencesManager.toggleX = x.toFloat()
            PreferencesManager.toggleY = y.toFloat()
        }
    }

    private fun createBox(size: Int, boxId: Int): FrameLayout {
        return FrameLayout(this).apply {
            background = ContextCompat.getDrawable(
                this@FloatingOverlayService,
                if (boxId == 1) R.drawable.box_bg_orange else R.drawable.box_bg_blue
            )

            val label = TextView(this@FloatingOverlayService).apply {
                text = if (boxId == 1) "抢" else "试"
                textSize = 22f
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
            }
            addView(label, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            setOnTouchListener(DragTouchListener(boxId))
        }
    }

    private fun createToggle(size: Int): FrameLayout {
        val label = TextView(this@FloatingOverlayService).apply {
            text = if (PreferencesManager.isRunning) "停" else "启"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundColor(
                if (PreferencesManager.isRunning) 0xCCDD2C00.toInt()
                else 0xCC2E7D32.toInt()
            )
        }

        return FrameLayout(this@FloatingOverlayService).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(if (PreferencesManager.isRunning) 0xCCDD2C00.toInt() else 0xCC2E7D32.toInt())
                setStroke((2 * resources.displayMetrics.density).toInt(), 0xCCFFFFFF.toInt())
            }

            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            addView(label, lp)

            this.tag = label
            setOnTouchListener(DragTouchListener(0))
        }
    }

    private fun addViewToWindow(view: View, x: Int, y: Int, w: Int, h: Int, hasFocus: Boolean) {
        val flags = if (hasFocus) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            w,
            h,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        windowManager?.addView(view, params)
    }

    private fun removeView(view: View?) {
        view?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
    }

    private fun removeAllViews() {
        removeView(box1View)
        removeView(box2View)
        removeView(toggleView)
        box1View = null
        box2View = null
        toggleView = null
    }

    private fun startStateRefreshing() {
        stateRefreshRunnable = object : Runnable {
            override fun run() {
                refreshToggleState()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(stateRefreshRunnable!!)
    }

    private fun stopStateRefreshing() {
        stateRefreshRunnable?.let { handler.removeCallbacks(it) }
        stateRefreshRunnable = null
    }

    private fun refreshToggleState() {
        val running = PreferencesManager.isRunning
        setBoxPassthrough(running)
        val label = toggleView?.tag as? TextView ?: return
        label.text = if (running) "停" else "启"
        val color = if (running) 0xCCDD2C00.toInt() else 0xCC2E7D32.toInt()
        label.setBackgroundColor(color)
        (toggleView?.background as? android.graphics.drawable.GradientDrawable)?.apply {
            setColor(color)
        }
    }

    /** 连点中让定位框不拦截触摸，ADB 点击才能穿透到下层抢票按钮 */
    private fun setBoxPassthrough(passthrough: Boolean) {
        for (view in listOf(box1View, box2View)) {
            val v = view ?: continue
            val params = v.layoutParams as? WindowManager.LayoutParams ?: continue
            params.flags = if (passthrough) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            windowManager?.updateViewLayout(v, params)
        }
    }

    private fun buildNotification(): Notification {
        val appIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("抢票连点器运行中")
            .setContentText("下拉通知栏点击「紧急停止」可随时暂停")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(appIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "紧急停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "抢票连点器悬浮窗通知"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private inner class DragTouchListener(
        private val viewId: Int
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var isDragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = v.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - touchStartX
                    val deltaY = event.rawY - touchStartY
                    if (kotlin.math.abs(deltaX) > 8 || kotlin.math.abs(deltaY) > 8) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val params = v.layoutParams as WindowManager.LayoutParams
                        params.x = (initialX + deltaX).toInt()
                        params.y = (initialY + deltaY).toInt()
                        windowManager?.updateViewLayout(v, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val params = v.layoutParams as WindowManager.LayoutParams
                    when (viewId) {
                        1 -> {
                            PreferencesManager.box1X = params.x.toFloat()
                            PreferencesManager.box1Y = params.y.toFloat()
                        }
                        2 -> {
                            PreferencesManager.box2X = params.x.toFloat()
                            PreferencesManager.box2Y = params.y.toFloat()
                        }
                        0 -> {
                            PreferencesManager.toggleX = params.x.toFloat()
                            PreferencesManager.toggleY = params.y.toFloat()
                            if (!isDragging) {
                                if (PreferencesManager.isRunning) {
                                    TapAccessibilityService.instance?.stopClicking()
                                } else {
                                    TapAccessibilityService.instance?.startClicking()
                                }
                            }
                        }
                    }
                    return true
                }
            }
            return false
        }
    }
}
