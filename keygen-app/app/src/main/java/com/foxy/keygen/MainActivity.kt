package com.foxy.keygen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxy.keygen.data.GistRegistry
import com.foxy.keygen.data.QuotaKeyCodec
import com.foxy.keygen.data.formatBytes
import com.foxy.keygen.ui.KeygenTheme
import com.foxy.keygen.ui.fieldColors
import com.foxy.keygen.ui.rememberKeygenAccent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as KeygenApp
        setContent {
            KeygenTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    KeygenRoot(app)
                }
            }
        }
    }
}

@Composable
fun KeygenRoot(app: KeygenApp) {
    var signedIn by remember { mutableStateOf(app.tokenStore.hasStoredSession()) }
    if (!signedIn) {
        LoginScreen(app) { signedIn = true }
    } else {
        HomeScreen(app) { signedIn = false }
    }
}

// ---------------- login ----------------

@Composable
private fun LoginScreen(app: KeygenApp, onSignedIn: () -> Unit) {
    val accent = rememberKeygenAccent()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needCode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8FB))
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.VpnKey, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("FoxyKeygen", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17222C))
        Text(
            "برای ساخت کلید، با حساب فایرفاکس خود وارد شوید",
            fontSize = 13.sp,
            color = Color(0xFF67737F),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email, onValueChange = { email = it; error = null },
            label = { Text("ایمیل فایرفاکس") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(accent), enabled = !busy,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it; error = null },
            label = { Text("رمز عبور") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(accent), enabled = !busy,
        )
        if (needCode) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it; error = null },
                label = { Text("کد تایید ارسال‌شده به ایمیل") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(accent), enabled = !busy,
            )
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (needCode) {
                            app.authRepository.submitTwoFactorCode(code).getOrThrow()
                        } else {
                            val needsTwoFactor = app.authRepository.startLogin(email, password).getOrThrow()
                            if (needsTwoFactor) {
                                needCode = true
                                busy = false
                                return@launch
                            }
                        }
                        onSignedIn()
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: Exception) {
                        error = e.message?.take(240) ?: "ورود ناموفق بود"
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            enabled = !busy,
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------- home ----------------

@Composable
private fun HomeScreen(app: KeygenApp, onSignOut: () -> Unit) {
    val accent = rememberKeygenAccent()
    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color(0xFFF6F8FB),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.VpnKey, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("FoxyKeygen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17222C))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSignOut) {
                    Icon(Icons.Filled.Logout, contentDescription = "خروج", tint = Color(0xFF67737F))
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            AccountQuotaCard(app)
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ساخت کلید") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("کلیدهای من") })
            }
            if (tab == 0) IssueTab(app) else KeysTab(app)
        }
    }
}

@Composable
private fun AccountQuotaCard(app: KeygenApp) {
    val accent = rememberKeygenAccent()
    var maxBytes by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableStateOf<Long?>(null) }
    var err by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val token = app.authRepository.currentAccessToken()
            if (token != null) {
                runCatching { app.guardianClient.fetchUserInfo(KeygenApp.GUARDIAN_ENDPOINT, token) }
                    .onSuccess {
                        maxBytes = it.maxBytes
                        remaining = it.quotaRemaining
                        err = false
                    }
                    .onFailure { err = true }
            }
            delay(60_000L)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("مجموع اکانت فایرفاکس شما", fontSize = 12.sp, color = Color(0xFF67737F))
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    err -> "دریافت اطلاعات ناموفق"
                    remaining != null && maxBytes != null -> "${formatBytes(remaining!!)} از ${formatBytes(maxBytes!!)} باقی مانده"
                    remaining != null -> "${formatBytes(remaining!!)} باقی مانده"
                    else -> "…"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17222C),
            )
            if (remaining != null && maxBytes != null && maxBytes!! > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (remaining!!.toFloat() / maxBytes!!.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = Color(0xFFEDF1F5),
                )
            }
        }
    }
}

// ---------------- issue tab ----------------

@Composable
private fun IssueTab(app: KeygenApp) {
    val accent = rememberKeygenAccent()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var ghToken by remember { mutableStateOf(app.gistRegistry.githubToken) }
    var ghStatus by remember { mutableStateOf(if (app.gistRegistry.isReady) "مخزن گزارش متصل است ✓" else "") }
    var ghBusy by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var gbText by remember { mutableStateOf("5") }
    var unlimited by remember { mutableStateOf(false) }
    var durationValue by remember { mutableStateOf("1") }
    var durationUnit by remember { mutableStateOf("ماه") }
    var noExpiry by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var issuedKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .imePadding(),
    ) {
        // GitHub setup
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("۱) اتصال گزارش زنده (گیت‌هاب)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF17222C))
                Spacer(Modifier.height(4.dp))
                Text(
                    "یک توکن GitHub با دسترسی Gist بسازید و اینجا وارد کنید تا مصرف کاربران به‌صورت زنده در تب «کلیدهای من» دیده شود.",
                    fontSize = 11.sp,
                    color = Color(0xFF67737F),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ghToken,
                    onValueChange = { ghToken = it; ghStatus = "" },
                    label = { Text("توکن GitHub (فقط دسترسی Gist کافی است)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(accent),
                )
                if (ghStatus.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(ghStatus, fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        ghBusy = true; ghStatus = ""
                        scope.launch {
                            try {
                                app.gistRegistry.githubToken = ghToken.trim()
                                if (!app.gistRegistry.validateToken()) throw Exception("توکن پذیرفته نشد")
                                if (app.gistRegistry.gistId.isBlank()) {
                                    app.gistRegistry.createRegistry()
                                }
                                ghStatus = "مخزن گزارش متصل است ✓ (${app.gistRegistry.gistId.take(8)}…)"
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                ghStatus = e.message ?: "خطا"
                            } finally {
                                ghBusy = false
                            }
                        }
                    },
                    enabled = !ghBusy && ghToken.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                ) {
                    Text(if (app.gistRegistry.isReady) "تست/بازسازی اتصال" else "اتصال و ساخت مخزن")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // issue form
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("۲) ساخت کلید جدید", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF17222C))
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null },
                    label = { Text("نام شخص") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp), colors = fieldColors(accent),
                )
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = gbText,
                        onValueChange = { gbText = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text("حجم (گیگابایت)") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        enabled = !unlimited,
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(accent),
                    )
                    Spacer(Modifier.width(10.dp))
                    FilterChip(
                        selected = unlimited,
                        onClick = { unlimited = !unlimited },
                        label = { Text("نامحدود") },
                    )
                }
                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = durationValue,
                        onValueChange = { durationValue = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("مدت") },
                        modifier = Modifier.weight(1f), singleLine = true,
                        enabled = !noExpiry,
                        shape = RoundedCornerShape(12.dp), colors = fieldColors(accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    listOf("ساعت", "روز", "ماه").forEach { unit ->
                        FilterChip(
                            selected = durationUnit == unit,
                            onClick = { durationUnit = unit; noExpiry = false },
                            label = { Text(unit) },
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = noExpiry,
                        onClick = { noExpiry = !noExpiry },
                        label = { Text("بی‌نهایت") },
                    )
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        busy = true; error = null; issuedKey = null
                        scope.launch {
                            try {
                                val refreshToken = app.tokenStore.loadAuth()?.refreshToken
                                    ?: throw Exception("نشست فایرفاکس یافت نشد؛ دوباره وارد شوید")
                                val gb = if (unlimited) 0L else gbText.toLongOrNull()?.coerceAtLeast(1L)
                                    ?: throw Exception("حجم را درست وارد کنید")
                                val unitSeconds = when (durationUnit) {
                                    "ساعت" -> 3600L
                                    "روز" -> 86400L
                                    else -> 30L * 86400L
                                }
                                val duration = unitSeconds * (durationValue.toLongOrNull()?.coerceAtLeast(1L) ?: 1L)
                                val exp = if (noExpiry) 0L else System.currentTimeMillis() / 1000L + duration

                                val key = QuotaKeyCodec.mint(
                                    name = name.trim().ifBlank { "کاربر" },
                                    gb = gb,
                                    expiresAtEpochSeconds = exp,
                                    refreshToken = refreshToken,
                                    gistId = app.gistRegistry.gistId,
                                    githubToken = app.gistRegistry.githubToken,
                                    privateKeyB64 = BuildConfig.FOXY_PRIVATE_KEY,
                                )
                                if (!QuotaKeyCodec.verifySelf(key)) throw Exception("خودآزمایی امضا ناموفق بود")
                                app.gistRegistry.addKey(
                                    GistRegistry.KeyRow(
                                        id = extractId(key), name = name.trim().ifBlank { "کاربر" },
                                        gb = gb, exp = exp,
                                        issuedAt = System.currentTimeMillis() / 1000L,
                                        revoked = false,
                                    ),
                                )
                                issuedKey = key
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                error = e.message ?: "ساخت کلید ناموفق بود"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    enabled = !busy,
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text("ساخت کلید", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                issuedKey?.let { key ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF7F1)),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("کلید آماده است — برای دوستتان بفرستید:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF128A56))
                            Spacer(Modifier.height(6.dp))
                            Text(key, fontSize = 10.sp, color = Color(0xFF37505F), maxLines = 6)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(key))
                                    Toast.makeText(app, "کلید کپی شد", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("کپی کلید")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private fun extractId(key: String): String = QuotaKeyCodec.randomId().let {
    // id is inside the payload; simplest is to regenerate consistency is not
    // critical for the registry, but we want the real one: decode payload
    runCatching {
        val payloadB64 = key.split(".")[1]
        var b64 = payloadB64
        while (b64.length % 4 != 0) b64 += "="
        val jsonText = String(
            android.util.Base64.decode(b64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP),
            Charsets.UTF_8,
        )
        org.json.JSONObject(jsonText).optString("id")
    }.getOrDefault(it)
}

// ---------------- keys tab ----------------

@Composable
private fun KeysTab(app: KeygenApp) {
    val accent = rememberKeygenAccent()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<GistRegistry.KeyRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            loading = true; err = null
            try {
                rows = app.gistRegistry.fetchRows()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                err = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(30_000L)
            runCatching { rows = app.gistRegistry.fetchRows() }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("مصرف زندهٔ کاربران", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF17222C))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "بازخوانی", tint = accent)
            }
        }
        if (loading && rows.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
            }
        }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (!loading && rows.isEmpty() && err == null) {
            Text(
                "هنوز کلیدی صادر نشده است",
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center,
                color = Color(0xFF67737F),
                fontSize = 13.sp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                KeyRowCard(
                    row = row,
                    busy = busyId == row.id,
                    onToggle = {
                        busyId = row.id
                        scope.launch {
                            try {
                                app.gistRegistry.setRevoked(row.id, !row.revoked)
                                refresh()
                            } catch (c: CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                busyId = null
                            }
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun KeyRowCard(row: GistRegistry.KeyRow, busy: Boolean, onToggle: () -> Unit) {
    val accent = rememberKeygenAccent()
    val statusColor = when {
        row.revoked -> Color(0xFFB72749)
        row.status == "exhausted" -> Color(0xFFB72749)
        row.status == "expired" -> Color(0xFFB57A00)
        else -> Color(0xFF128A56)
    }
    val statusText = when {
        row.revoked -> "باطل‌شده"
        row.status == "exhausted" -> "حجم تمام"
        row.status == "expired" -> "منقضی"
        row.status == "active" -> "فعال"
        else -> "بدون گزارش"
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF17222C))
                Spacer(Modifier.width(8.dp))
                Text("#${row.id}", fontSize = 10.sp, color = Color(0xFF97A1AC))
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.13f)) {
                    Text(statusText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(if (row.gb > 0) "${row.gb} گیگابایت" else "نامحدود")
                    if (row.exp > 0) append("  ·  اعتبار تا " + formatDate(row.exp))
                },
                fontSize = 11.sp,
                color = Color(0xFF67737F),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "مصرف: ${formatBytes(row.usedBytes)}" + (if (row.updatedAt > 0) "  ·  آخرین گزارش: " + formatTime(row.updatedAt) else ""),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF17222C),
            )
            if (row.gb > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (row.usedBytes.toFloat() / (row.gb * 1_000_000_000L).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (row.revoked) Color(0xFFB72749) else accent,
                    trackColor = Color(0xFFEDF1F5),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onToggle,
                enabled = !busy,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (row.revoked) "فعال‌سازی دوباره" else "باطل‌کردن کلید", fontSize = 12.sp)
            }
        }
    }
}

private fun formatDate(epochSeconds: Long): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale("fa"))
        .format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate())
}.getOrDefault("")

private fun formatTime(epochSeconds: Long): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        .format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))
}.getOrDefault("")
