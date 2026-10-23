package com.vauth.foxyvpn.data

import android.content.Context
import org.json.JSONObject

/**
 * Persists the activated quota key and the cumulative usage counter.
 *
 * Usage is written to two independent places (two SharedPreferences files);
 * on load the larger value wins, so deleting or editing only one of them does
 * not reset the counter. A monotonic wall-clock floor is also stored to blunt
 * simple clock-rollback tricks against time-limited keys.
 */
class ActivatedKeyStore(context: Context) {

    data class UsageState(
        val usedBytes: Long,
        val lastSeenClock: Long,
    )

    private val mainPrefs = context.getSharedPreferences("foxyvpn_key", Context.MODE_PRIVATE)
    private val mirrorPrefs = context.getSharedPreferences("foxyvpn_key_guard", Context.MODE_PRIVATE)

    var activatedKey: QuotaKey? = null
        private set

    fun saveKey(key: QuotaKey) {
        val obj = JSONObject()
            .put("raw", key.raw)
            .put("payload", key.payload.id.let { QuotaKeyJson.encode(key) })
        val text = obj.toString()
        mainPrefs.edit().putString(KEY_BLOB, text).apply()
        mirrorPrefs.edit().putString(KEY_BLOB, text).apply()
        activatedKey = key
    }

    fun loadKey(): QuotaKey? {
        activatedKey = loadFrom(mainPrefs) ?: loadFrom(mirrorPrefs)
        return activatedKey
    }

    private fun loadFrom(prefs: android.content.SharedPreferences): QuotaKey? {
        val text = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            val obj = JSONObject(text)
            val raw = obj.getString("raw")
            QuotaKeyCodec.parse(raw)
        }.onFailure { AppLogger.w(TAG, "stored key unreadable", it) }.getOrNull()
    }

    fun clear() {
        mainPrefs.edit().clear().apply()
        mirrorPrefs.edit().clear().apply()
        activatedKey = null
    }

    // ---------- usage ----------

    fun loadUsage(): UsageState {
        val a = readUsage(mainPrefs)
        val b = readUsage(mirrorPrefs)
        val best = if (a.usedBytes >= b.usedBytes) a else b
        val clockFloor = maxOf(a.lastSeenClock, b.lastSeenClock)
        return UsageState(best.usedBytes, clockFloor)
    }

    private fun readUsage(prefs: android.content.SharedPreferences): UsageState = UsageState(
        usedBytes = prefs.getLong(KEY_USED, 0L).coerceAtLeast(0L),
        lastSeenClock = prefs.getLong(KEY_LAST_SEEN, 0L),
    )

    fun saveUsage(usedBytes: Long, nowMillis: Long = System.currentTimeMillis()) {
        val current = loadUsage()
        val used = maxOf(usedBytes, current.usedBytes)
        val clock = maxOf(nowMillis, current.lastSeenClock)
        writeUsage(mainPrefs, used, clock)
        writeUsage(mirrorPrefs, used, clock)
    }

    private fun writeUsage(prefs: android.content.SharedPreferences, used: Long, clock: Long) {
        prefs.edit().putLong(KEY_USED, used).putLong(KEY_LAST_SEEN, clock).apply()
    }

    /** Effective "now", resistant to backwards clock changes. */
    fun nowMillis(): Long = maxOf(System.currentTimeMillis(), loadUsage().lastSeenClock)

    companion object {
        private const val TAG = "ActivatedKeyStore"
        private const val KEY_BLOB = "key_blob"
        private const val KEY_USED = "used_bytes"
        private const val KEY_LAST_SEEN = "last_seen_clock"
    }
}

private object QuotaKeyJson {
    fun encode(key: QuotaKey): String {
        val p = key.payload
        return org.json.JSONObject()
            .put("v", p.v)
            .put("name", p.name)
            .put("gb", p.gb)
            .put("exp", p.exp)
            .put("id", p.id)
            .put("rt", "") // never persist the refresh token outside the encrypted TokenStore
            .put("gist", p.gist)
            .put("gt", "")
            .toString()
    }
}
