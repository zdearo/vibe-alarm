package com.zdearo.vibealarm

import android.content.Context

/** Cópia local da lista enviada pelo celular, mais o "pular este". */
object AlarmStore {
    private fun prefs(c: Context) = c.getSharedPreferences("vibealarm", Context.MODE_PRIVATE)

    fun alarms(c: Context): List<Alarm> =
        try {
            Alarm.listFromJson(prefs(c).getString("alarms", "[]")!!)
        } catch (e: Exception) {
            emptyList()
        }

    fun save(c: Context, json: String) {
        prefs(c).edit().putString("alarms", json).putLong("syncedAt", System.currentTimeMillis()).apply()
    }

    fun syncedAt(c: Context): Long = prefs(c).getLong("syncedAt", 0)

    fun skipUntil(c: Context): Long = prefs(c).getLong("skipUntil", 0)

    fun setSkipUntil(c: Context, t: Long) = prefs(c).edit().putLong("skipUntil", t).apply()
}
