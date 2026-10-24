package com.vauth.foxyvpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live usage reporting through a private GitHub gist.
 *
 * The gist holds two files:
 *   registry.json -> { "keys": [ {id, name, gb, exp, revoked, issued} ] }   (written by FoxyKeygen)
 *   usage.json    -> { "usages": { <id>: {used, status, updated} } }        (written by this app)
 *
 * The app refreshes its own entry only, so the distributor's FoxyKeygen can
 * show every key's consumption live. Reporting failures are logged and
 * retried on the next sync tick; enforcement stays fully local.
 */
object GistReporter {

    private const val TAG = "GistReporter"

    data class RegistryEntry(
        val id: String,
        val name: String,
        val gb: Long,
        val exp: Long,
        val revoked: Boolean,
    )

    data class ReportContext(
        val gistId: String,
        val token: String,
        val keyId: String,
    )

    private val inFlight = AtomicBoolean(false)

    fun contextFromKey(key: QuotaKey): ReportContext? {
        val payload = key.payload
        if (payload.gist.isBlank() || payload.gt.isBlank()) return null
        return ReportContext(payload.gist, payload.gt, payload.id)
    }

    /** Result of a combined revocation-check + usage-report round trip. */
    data class SyncResult(
        val revoked: Boolean,
        val reported: Boolean,
    )

    /**
     * One round trip that serves both directions of the dashboard:
     * reads the registry (revocation flag) and writes this device's usage.
     * Returns null when the gist could not be reached (caller retries later).
     */
    suspend fun sync(
        ctx: ReportContext,
        usedBytes: Long,
        limitBytes: Long,
        expired: Boolean,
    ): SyncResult? = withContext(Dispatchers.IO) {
        if (!inFlight.compareAndSet(false, true)) return@withContext null
        try {
            val gist = fetchGist(ctx.gistId, ctx.token)
            if (gist == null) {
                AppLogger.d(TAG, "gist sync: fetch failed (will retry on the next tick)")
                return@withContext null
            }
            var revoked = false
            gist.first.optJSONArray("keys")?.let { keys ->
                for (i in 0 until keys.length()) {
                    val entry = keys.optJSONObject(i) ?: continue
                    if (entry.optString("id") == ctx.keyId) {
                        revoked = entry.optBoolean("revoked", false)
                        break
                    }
                }
            }
            val status = when {
                revoked -> "revoked"
                expired -> "expired"
                limitBytes > 0 && usedBytes >= limitBytes -> "exhausted"
                else -> "active"
            }
            val reported = patchUsage(gist.second, ctx, usedBytes, status)
            if (!reported) AppLogger.d(TAG, "gist sync: usage patch failed (will retry)")
            SyncResult(revoked = revoked, reported = reported)
        } finally {
            inFlight.set(false)
        }
    }

    /** Returns true when the registry marks this key as revoked. */
    suspend fun isRevoked(ctx: ReportContext): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val pair = fetchGist(ctx.gistId, ctx.token) ?: return@runCatching false
            val keys = pair.first.optJSONArray("keys") ?: return@runCatching false
            for (i in 0 until keys.length()) {
                val entry = keys.optJSONObject(i) ?: continue
                if (entry.optString("id") == ctx.keyId) return@runCatching entry.optBoolean("revoked", false)
            }
            false
        }.getOrElse {
            AppLogger.d(TAG, "revocation check failed (treating as not revoked): ${it.message}")
            false
        }
    }

    /**
     * Reports cumulative usage. [statusOverride] forces a final state when the
     * key just became exhausted/expired; otherwise the status is derived from
     * the local limit.
     */
    suspend fun reportUsage(
        ctx: ReportContext,
        usedBytes: Long,
        limitBytes: Long,
        expired: Boolean,
        statusOverride: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inFlight.compareAndSet(false, true)) return@withContext false
        try {
            val gist = fetchGist(ctx.gistId, ctx.token) ?: return@withContext false
            val status = statusOverride ?: when {
                expired -> "expired"
                limitBytes > 0 && usedBytes >= limitBytes -> "exhausted"
                else -> "active"
            }
            patchUsage(gist.second, ctx, usedBytes, status)
        } finally {
            inFlight.set(false)
        }
    }

    /** Writes [usedBytes] into the usage map of [usageDoc] and patches the gist. */
    private fun patchUsage(usageDoc: JSONObject, ctx: ReportContext, usedBytes: Long, status: String): Boolean {
        val usage = usageDoc
        val usages = usage.optJSONObject("usages") ?: JSONObject().also { usage.put("usages", it) }
        usages.put(
            ctx.keyId,
            JSONObject()
                .put("used", usedBytes)
                .put("status", status)
                .put("updated", System.currentTimeMillis() / 1000L),
        )
        return patchGist(ctx.gistId, ctx.token, "usage.json", usage.toString())
    }

    private fun fetchGist(gistId: String, ghToken: String): Pair<JSONObject, JSONObject>? {
        val request = Request.Builder()
            .url("https://api.github.com/gists/$gistId")
            .header("Authorization", "Bearer $ghToken")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FoxyVPN-Key")
            .get()
            .build()
        ControlPlaneHttp.client.newCall(request).execute().use { resp ->
            if (resp.code != 200) return null
            val body = runCatching { JSONObject(resp.body?.string().orEmpty()) }.getOrNull() ?: return null
            val files = body.optJSONObject("files") ?: return null
            val registry = fileContent(files, "registry.json", ghToken)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val usage = fileContent(files, "usage.json", ghToken)?.let { runCatching { JSONObject(it) }.getOrNull() }
            return Pair(registry ?: JSONObject(), usage ?: JSONObject().put("usages", JSONObject()))
        }
    }

    private fun fileContent(files: JSONObject, name: String, ghToken: String): String? {
        val file = files.optJSONObject(name) ?: return null
        if (file.optString("type") != "file") return null
        return runCatching {
            val url = file.optString("raw_url")
            if (url.isNotBlank()) {
                val req = Request.Builder().url(url)
                    .header("Authorization", "Bearer $ghToken").header("User-Agent", "FoxyVPN-Key").get().build()
                ControlPlaneHttp.client.newCall(req).execute().use { r ->
                    if (r.code == 200) r.body?.string() else null
                }
            } else {
                file.optString("content").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun patchGist(gistId: String, ghToken: String, fileName: String, content: String): Boolean {
        val payload = JSONObject().put(
            "files",
            JSONObject().put(fileName, JSONObject().put("content", content)),
        )
        val request = Request.Builder()
            .url("https://api.github.com/gists/$gistId")
            .header("Authorization", "Bearer $ghToken")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FoxyVPN-Key")
            .patch(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        ControlPlaneHttp.client.newCall(request).execute().use { resp ->
            return resp.code in 200..299
        }
    }
}
