package com.vauth.foxyvpn.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Cumulative traffic accounting for the activated quota key.
 *
 * The native tunnel reports per-session totals that reset on reconnect, so
 * [observe] feeds monotonically increasing deltas into the persistent store.
 * Enforcement state (quota/time exhausted) is derived here and consumed by
 * FoxyVpnService before and during a session.
 */
object UsageAccountant {

    enum class BlockReason { NONE, QUOTA, EXPIRED, REVOKED }

    private var store: ActivatedKeyStore? = null
    private var key: QuotaKey? = null
    private val lastNativeTotal = AtomicLong(-1L)
    private val usedBytes = AtomicLong(0L)
    private val lastPersistAt = AtomicLong(0L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile var blockReason: BlockReason = BlockReason.NONE
        private set
    @Volatile private var reporterContext: GistReporter.ReportContext? = null

    fun attach(activatedKeyStore: ActivatedKeyStore, quotaKey: QuotaKey?) {
        store = activatedKeyStore
        key = quotaKey
        reporterContext = quotaKey?.let { GistReporter.contextFromKey(it) }
        val stored = activatedKeyStore.loadUsage()
        usedBytes.set(stored.usedBytes)
        lastNativeTotal.set(-1L)
        recomputeBlock(activatedKeyStore.nowMillis())
    }

    fun reset() {
        store = null
        key = null
        reporterContext = null
        lastNativeTotal.set(-1L)
        usedBytes.set(0L)
        blockReason = BlockReason.NONE
    }

    fun keyAttached(): Boolean = key != null

    fun reporterContext(): GistReporter.ReportContext? = reporterContext

    val used: Long get() = usedBytes.get()
    val limit: Long get() = key?.payload?.limitBytes ?: 0L
    val expiresAt: Long get() = key?.payload?.exp ?: 0L

    /** Feed the current native session total (tx + rx). */
    fun observe(nativeTotalBytes: Long) {
        val current = key ?: return
        if (blockReason != BlockReason.NONE) return
        val prev = lastNativeTotal.getAndSet(nativeTotalBytes)
        val delta = when {
            prev < 0 -> nativeTotalBytes
            nativeTotalBytes >= prev -> nativeTotalBytes - prev
            else -> nativeTotalBytes // native counters reset: this is a fresh session
        }
        if (delta <= 0L) return
        val total = usedBytes.addAndGet(delta)
        val now = System.currentTimeMillis()
        val s = store ?: return
        if (now - lastPersistAt.get() > PERSIST_INTERVAL_MS) {
            lastPersistAt.set(now)
            s.saveUsage(total, now)
        }
        recomputeBlock(s.nowMillis())
        if (blockReason != BlockReason.NONE) {
            s.saveUsage(total, now)
            pushFinalStatus()
        }
    }

    fun recomputeBlock(nowMillis: Long) {
        val current = key ?: return
        val limit = current.payload.limitBytes
        val exp = current.payload.exp
        blockReason = when {
            limit in 1..usedBytes.get() -> BlockReason.QUOTA
            exp in 1..(nowMillis / 1000L) -> BlockReason.EXPIRED
            else -> BlockReason.NONE
        }
    }

    fun markRevoked() {
        if (key == null) return
        blockReason = BlockReason.REVOKED
        store?.saveUsage(usedBytes.get())
        pushFinalStatus()
    }

    /** Best-effort live report for the distributor; never throws. */
    suspend fun reportNow(finalStatus: String? = null): Boolean {
        val ctx = reporterContext ?: return false
        val s = store ?: return false
        return GistReporter.reportUsage(
            ctx = ctx,
            usedBytes = usedBytes.get(),
            limitBytes = limit,
            expired = blockReason == BlockReason.EXPIRED,
            statusOverride = finalStatus,
        )
    }

    private fun pushFinalStatus() {
        val ctx = reporterContext ?: return
        val status = when (blockReason) {
            BlockReason.QUOTA -> "exhausted"
            BlockReason.EXPIRED -> "expired"
            BlockReason.REVOKED -> "revoked"
            else -> return
        }
        val used = usedBytes.get()
        val s = store
        scope.launch {
            runCatching {
                GistReporter.reportUsage(
                    ctx = ctx,
                    usedBytes = used,
                    limitBytes = limit,
                    expired = blockReason == BlockReason.EXPIRED,
                    statusOverride = status,
                )
            }
            s?.saveUsage(used)
        }
    }

    private const val PERSIST_INTERVAL_MS = 4_000L
}
