package com.jondude1.kankaforandroid.data

import android.content.Context

/** Connection settings, kept in the app's private storage. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kanka", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString("url", "").orEmpty()
        set(v) = sp.edit().putString("url", v).apply()
    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(v) = sp.edit().putString("token", v).apply()
    var campaignId: Long
        get() = sp.getLong("campaign", 0L)
        set(v) = sp.edit().putLong("campaign", v).apply()
    var campaignName: String
        get() = sp.getString("campaignName", "").orEmpty()
        set(v) = sp.edit().putString("campaignName", v).apply()
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    val loggedIn get() = url.isNotBlank() && token.isNotBlank()
    fun logout() { sp.edit().clear().apply() }
}
