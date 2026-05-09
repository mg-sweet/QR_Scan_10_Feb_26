package com.sweet.qr_scan_10_feb_26.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    // 1. Scanner Settings
    var isBeepEnabled: Boolean
        get() = prefs.getBoolean("beep_enabled", true)
        set(value) = prefs.edit().putBoolean("beep_enabled", value).apply()

    var isVibrateEnabled: Boolean
        get() = prefs.getBoolean("vibrate_enabled", true)
        set(value) = prefs.edit().putBoolean("vibrate_enabled", value).apply()

    var warnDuplicateScan: Boolean
        get() = prefs.getBoolean("warn_duplicate", false)
        set(value) = prefs.edit().putBoolean("warn_duplicate", value).apply()

    // 2. Export & Data Settings
    var exportMethod: Int // 0 = Merge, 1 = Separate
        get() = prefs.getInt("export_method", 0)
        set(value) = prefs.edit().putInt("export_method", value).apply()

    var autoEmptyTrashDays: Int // 0 = Never, 7 = 7 Days, 30 = 30 Days
        get() = prefs.getInt("auto_empty_trash", 30)
        set(value) = prefs.edit().putInt("auto_empty_trash", value).apply()

    // 3. Appearance Settings
    var themeMode: Int // 0 = System, 1 = Light, 2 = Dark
        get() = prefs.getInt("theme_mode", 0)
        set(value) = prefs.edit().putInt("theme_mode", value).apply()

    var use24HourFormat: Boolean
        get() = prefs.getBoolean("time_format_24", false)
        set(value) = prefs.edit().putBoolean("time_format_24", value).apply()
}