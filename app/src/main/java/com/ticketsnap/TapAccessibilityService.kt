package com.ticketsnap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class TapAccessibilityService : AccessibilityService() {

    private var running = false

    companion object {
        var instance: TapAccessibilityService? = null
            private set
        const val TAG = "TicketSnap"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        setServiceInfo(serviceInfo)
        if (PreferencesManager.isRunning) {
            running = true
        }
        Log.e(TAG, "Service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (running) {
                stopClicking()
                return true  // 仅在连点中才拦截
            }
        }
        return false  // 非连点状态放行，音量正常工作
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        // uiautomator dump 等系统操作会触发 interrupt，不应结束连点会话
    }

    override fun onDestroy() {
        running = false
        instance = null
        super.onDestroy()
    }

    fun startClicking() {
        if (running) return
        running = true
        PreferencesManager.isRunning = true
        Log.e(TAG, "Clicking STARTED — box1(${PreferencesManager.box1X},${PreferencesManager.box1Y}) box2(${PreferencesManager.box2X},${PreferencesManager.box2Y}) interval=${PreferencesManager.clickIntervalMs}")
        // 实际点击由电脑端 tap.py 通过 ADB 执行，App 只维护 is_running 状态
    }

    fun stopClicking() {
        if (!running) return
        Log.e(TAG, "Clicking STOPPED")
        running = false
        PreferencesManager.isRunning = false
    }

    fun isRunning(): Boolean = running
}
