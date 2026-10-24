package com.foxy.keygen.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.foxy.keygen.data.model.RuntimeAuth
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "AccountVault"

/**
 * Stores several Firefox accounts so the distributor can mint keys from any
 * of them — every key then consumes the quota of the account it was minted
 * from. The tokens live in an encrypted preference file; the account that is
 * currently "active" is mirrored into [TokenStore] because FxaAuthRepository,
 * GuardianClient and the mint flow all operate on the active session.
 */
class AccountVault(context: Context) {

    data class StoredAccount(
        val email: String,
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochSeconds: Long,
        val addedAtEpochSeconds: Long = 0L,
    )

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "foxykeygen_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    val accounts: List<StoredAccount>
        get() = runCatching {
            val arr = JSONArray(prefs.getString(KEY_ACCOUNTS, "[]").orEmpty())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                StoredAccount(
                    email = o.optString("email"),
                    accessToken = o.optString("access"),
                    refreshToken = o.optString("refresh").takeIf { it.isNotBlank() },
                    expiresAtEpochSeconds = o.optLong("expires"),
                    addedAtEpochSeconds = o.optLong("added"),
                )
            }
        }.getOrElse { emptyList() }

    var activeEmail: String
        get() = prefs.getString(KEY_ACTIVE, null).orEmpty()
        private set(value) { prefs.edit().putString(KEY_ACTIVE, value).apply() }

    fun activeAccount(): StoredAccount? =
        accounts.firstOrNull { it.email == activeEmail } ?: accounts.firstOrNull()

    val isEmpty: Boolean get() = accounts.isEmpty()

    /** Adds (or replaces) an account and makes it the active one. */
    fun upsert(account: StoredAccount) {
        val email = account.email.trim().lowercase()
        if (email.isBlank()) return
        val rest = accounts.filterNot { it.email == email }
        val updated = listOf(account.copy(email = email)) + rest
        persist(updated)
        activeEmail = email
    }

    /** Replaces the stored tokens of [email] (e.g. after a refresh grant). */
    fun updateTokens(email: String, auth: RuntimeAuth) {
        val email = email.trim().lowercase()
        val updated = accounts.map {
            if (it.email == email) it.copy(
                accessToken = auth.accessToken,
                refreshToken = auth.refreshToken ?: it.refreshToken,
                expiresAtEpochSeconds = auth.expiresAtEpochSeconds,
            ) else it
        }
        persist(updated)
    }

    fun setActive(email: String): Boolean {
        val email = email.trim().lowercase()
        if (accounts.none { it.email == email }) return false
        activeEmail = email
        return true
    }

    fun remove(email: String) {
        val email = email.trim().lowercase()
        val rest = accounts.filterNot { it.email == email }
        persist(rest)
        if (activeEmail == email) {
            activeEmail = rest.firstOrNull()?.email.orEmpty()
        }
    }

    /** Copies the tokens of the active account into the live [TokenStore]. */
    fun activateInto(tokenStore: TokenStore, email: String): Boolean {
        val account = accounts.firstOrNull { it.email == email.trim().lowercase() } ?: return false
        tokenStore.saveAuth(
            RuntimeAuth(
                accessToken = account.accessToken,
                refreshToken = account.refreshToken,
                expiresAtEpochSeconds = account.expiresAtEpochSeconds,
            ),
        )
        activeEmail = account.email
        return true
    }

    /** Persists the (possibly refreshed) live session back into the vault. */
    fun syncActiveFrom(tokenStore: TokenStore) {
        val auth = tokenStore.loadAuth() ?: return
        val email = activeEmail.ifBlank { return }
        updateTokens(email, auth)
    }

    /**
     * One-time migration from the previous single-session storage so users of
     * the previous release keep their signed-in account without re-logging in.
     */
    fun migrateLegacySession(tokenStore: TokenStore) {
        if (accounts.isNotEmpty()) return
        val auth = tokenStore.loadAuth() ?: return
        if (auth.refreshToken.isNullOrBlank()) return
        upsert(StoredAccount(email = "اکانت اصلی", accessToken = auth.accessToken, refreshToken = auth.refreshToken, expiresAtEpochSeconds = auth.expiresAtEpochSeconds, addedAtEpochSeconds = System.currentTimeMillis() / 1000))
        AppLogger.i(TAG, "migrated the legacy single session into the vault")
    }

    private fun persist(list: List<StoredAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("email", a.email)
                    .put("access", a.accessToken)
                    .put("refresh", a.refreshToken.orEmpty())
                    .put("expires", a.expiresAtEpochSeconds)
                    .put("added", a.addedAtEpochSeconds),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACTIVE = "active_email"
    }
}
