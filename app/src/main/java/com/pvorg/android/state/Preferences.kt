package com.pvorg.android.state

import android.content.Context

/** The user's appearance preference; Dark is the default (spec §6.5). */
enum class Appearance(val title: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("pvorg", Context.MODE_PRIVATE)

    var appearance: Appearance
        get() = prefs.getString(KEY_APPEARANCE, null)?.let { runCatching { Appearance.valueOf(it) }.getOrNull() } ?: Appearance.DARK
        set(value) = prefs.edit().putString(KEY_APPEARANCE, value.name).apply()

    var libraryUri: String?
        get() = prefs.getString(KEY_LIBRARY, null)
        set(value) = prefs.edit().putString(KEY_LIBRARY, value).apply()

    var navigationExpanded: Boolean
        get() = prefs.getBoolean(KEY_NAV_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_NAV_EXPANDED, value).apply()

    /** Whether the navigation (drawer or rail) is hidden; shown is the default (spec §4.1). */
    var navigationHidden: Boolean
        get() = prefs.getBoolean(KEY_NAV_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_NAV_HIDDEN, value).apply()

    private companion object {
        const val KEY_APPEARANCE = "appearance"
        const val KEY_LIBRARY = "libraryUri"
        const val KEY_NAV_EXPANDED = "navigationExpanded"
        const val KEY_NAV_HIDDEN = "navigationHidden"
    }
}
