package com.ticketsnap

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREFS_NAME = "ticket_snap_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var box1X: Float
        get() = prefs.getFloat("box1_x", -1f)
        set(v) = prefs.edit().putFloat("box1_x", v).apply()

    var box1Y: Float
        get() = prefs.getFloat("box1_y", -1f)
        set(v) = prefs.edit().putFloat("box1_y", v).apply()

    var box2X: Float
        get() = prefs.getFloat("box2_x", -1f)
        set(v) = prefs.edit().putFloat("box2_x", v).apply()

    var box2Y: Float
        get() = prefs.getFloat("box2_y", -1f)
        set(v) = prefs.edit().putFloat("box2_y", v).apply()

    var toggleX: Float
        get() = prefs.getFloat("toggle_x", -1f)
        set(v) = prefs.edit().putFloat("toggle_x", v).apply()

    var toggleY: Float
        get() = prefs.getFloat("toggle_y", -1f)
        set(v) = prefs.edit().putFloat("toggle_y", v).apply()

    var box1Enabled: Boolean
        get() = prefs.getBoolean("box1_enabled", true)
        set(v) = prefs.edit().putBoolean("box1_enabled", v).apply()

    var box2Enabled: Boolean
        get() = prefs.getBoolean("box2_enabled", true)
        set(v) = prefs.edit().putBoolean("box2_enabled", v).apply()

    var retryTextOnly: Boolean
        get() = prefs.getBoolean("retry_text_only", true)
        set(v) = prefs.edit().putBoolean("retry_text_only", v).apply()

    var clickIntervalMs: Int
        get() = prefs.getInt("click_interval", 100)
        set(v) = prefs.edit().putInt("click_interval", v).apply()

    var isRunning: Boolean
        get() = prefs.getBoolean("is_running", false)
        set(v) {
            prefs.edit().putBoolean("is_running", v).commit()
        }
}
