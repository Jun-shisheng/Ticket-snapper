package com.ticketsnap

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnOverlayPerm: MaterialButton
    private lateinit var btnAccessibilityPerm: MaterialButton
    private lateinit var switchBox1: SwitchMaterial
    private lateinit var switchBox2: SwitchMaterial
    private lateinit var cbRetryOnly: MaterialCheckBox
    private lateinit var btnToggle: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvInterval: TextView
    private lateinit var seekInterval: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PreferencesManager.init(this)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnOverlayPerm = findViewById(R.id.btnOverlayPerm)
        btnAccessibilityPerm = findViewById(R.id.btnAccessibilityPerm)
        switchBox1 = findViewById(R.id.switchBox1)
        switchBox2 = findViewById(R.id.switchBox2)
        cbRetryOnly = findViewById(R.id.cbRetryOnly)
        btnToggle = findViewById(R.id.btnToggle)
        tvStatus = findViewById(R.id.tvStatus)
        tvInterval = findViewById(R.id.tvInterval)
        seekInterval = findViewById(R.id.seekInterval)

        loadPreferences()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateRunningState()
    }

    private fun loadPreferences() {
        switchBox1.isChecked = PreferencesManager.box1Enabled
        switchBox2.isChecked = PreferencesManager.box2Enabled
        cbRetryOnly.isChecked = PreferencesManager.retryTextOnly
        seekInterval.progress = (PreferencesManager.clickIntervalMs - 10) / 10
        updateIntervalLabel()
    }

    private fun setupListeners() {
        btnOverlayPerm.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnAccessibilityPerm.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        switchBox1.setOnCheckedChangeListener { _, checked ->
            PreferencesManager.box1Enabled = checked
        }

        switchBox2.setOnCheckedChangeListener { _, checked ->
            PreferencesManager.box2Enabled = checked
        }

        cbRetryOnly.setOnCheckedChangeListener { _, checked ->
            PreferencesManager.retryTextOnly = checked
        }

        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = (progress + 1) * 10
                PreferencesManager.clickIntervalMs = ms
                updateIntervalLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnToggle.setOnClickListener {
            if (PreferencesManager.isRunning) {
                stopService()
            } else {
                if (!canStart()) return@setOnClickListener
                startService()
            }
        }
    }

    private fun canStart(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Snackbar.make(
                btnToggle, "请先授予悬浮窗权限", Snackbar.LENGTH_LONG
            ).setAction("去授权") {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ))
            }.show()
            return false
        }
        if (!isAccessibilityEnabled()) {
            Snackbar.make(
                btnToggle, "请先开启无障碍服务", Snackbar.LENGTH_LONG
            ).setAction("去开启") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.show()
            return false
        }
        return true
    }

    private fun startService() {
        PreferencesManager.isRunning = false
        val overlayIntent = Intent(this, FloatingOverlayService::class.java)
        ContextCompat.startForegroundService(this, overlayIntent)
        updateRunningState()
    }

    private fun stopService() {
        TapAccessibilityService.instance?.stopClicking()
        stopService(Intent(this, FloatingOverlayService::class.java))
        PreferencesManager.isRunning = false
        updateRunningState()
    }

    private fun updateRunningState() {
        if (TapAccessibilityService.instance?.isRunning() == true) {
            btnToggle.text = "关闭悬浮窗"
            btnToggle.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            tvStatus.text = "运行中 · 点绿色「启」开始连点"
            tvStatus.setTextColor(getColor(android.R.color.holo_green_light))
        } else if (isOverlayShowing()) {
            btnToggle.text = "关闭悬浮窗"
            btnToggle.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            tvStatus.text = "就绪 · 拖动三个浮控件到目标位置后点「启」"
            tvStatus.setTextColor(0xFF888888.toInt())
        } else {
            btnToggle.text = "开启连点"
            btnToggle.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            tvStatus.text = "就绪 - 拖动屏幕上两个方框到目标位置后开启"
            tvStatus.setTextColor(0xFF888888.toInt())
        }
    }

    private fun isOverlayShowing(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == FloatingOverlayService::class.java.name }
        } catch (_: Exception) {
            false
        }
    }

    private fun updatePermissionStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (overlayOk) "已授予" else "未授予"
        btnOverlayPerm.visibility = if (overlayOk) android.view.View.GONE
            else android.view.View.VISIBLE

        val accessibilityOk = isAccessibilityEnabled()
        tvAccessibilityStatus.text = if (accessibilityOk) "已开启" else "未开启"
        btnAccessibilityPerm.visibility = if (accessibilityOk) android.view.View.GONE
            else android.view.View.VISIBLE
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return services.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name == TapAccessibilityService::class.java.name
        }
    }

    private fun updateIntervalLabel() {
        tvInterval.text = "${PreferencesManager.clickIntervalMs}ms"
    }
}
