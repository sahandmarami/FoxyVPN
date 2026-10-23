package com.vauth.foxyvpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauth.foxyvpn.FoxyVpnApp
import com.vauth.foxyvpn.data.FxaApiError
import com.vauth.foxyvpn.data.FxaRefreshFailure
import com.vauth.foxyvpn.data.InvalidKeyException
import com.vauth.foxyvpn.data.UsageAccountant
import com.vauth.foxyvpn.ui.theme.LocalFoxyPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun KeyEntryScreen(
    app: FoxyVpnApp,
    onActivated: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val palette = LocalFoxyPalette.current
        var keyText by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(palette.gradientTop, palette.gradientBottom),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(palette.accent, palette.accentDeep),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(42.dp),
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "به FoxyVPN خوش آمدید",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای استفاده از اینترنت آزاد، کلیدی که برای شما ارسال شده را وارد کنید",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(26.dp))

                OutlinedTextField(
                    value = keyText,
                    onValueChange = {
                        keyText = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("کلید فعال‌سازی") },
                    placeholder = { Text("FV1.xxxxxxxx.xxxxxxxxxxxx") },
                    leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        focusedLeadingIconColor = palette.accent,
                        cursorColor = palette.accent,
                    ),
                    enabled = !busy,
                )

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        scope.launch {
                            val result = activateKey(app, keyText)
                            busy = false
                            result.fold(
                                onSuccess = { onActivated() },
                                onFailure = { e ->
                                    if (e is CancellationException) throw e
                                    error = e.message ?: "فعال‌سازی ناموفق بود"
                                },
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    enabled = !busy,
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("فعال‌سازی و ورود", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "کلید را از فروشندهٔ خود دریافت کنید",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun activateKey(app: FoxyVpnApp, rawKey: String): Result<Unit> {
    val key = try {
        com.vauth.foxyvpn.data.QuotaKeyCodec.parse(rawKey)
    } catch (e: InvalidKeyException) {
        return Result.failure(e)
    } catch (e: Exception) {
        return Result.failure(InvalidKeyException("کلید قابل خواندن نیست؛ آن را کامل کپی کنید"))
    }

    // the FxA session behind the key must come alive before we accept it
    app.authRepository.activateWithRefreshToken(key.payload.rt).getOrElse { e ->
        val message = when {
            e is FxaRefreshFailure && e.permanent ->
                "اعتبارنامهٔ این کلید توسط فایرفاکس پذیرفته نشد؛ از فروشندهٔ خود کلید سالم بگیرید"
            e is FxaApiError && e.errno == 101 || e is FxaApiError && e.errno == 102 ->
                "اعتبارنامهٔ این کلید باطل شده است؛ از فروشندهٔ خود کلید جدید بگیرید"
            else -> "اتصال به سرور نشد؛ اینترنت را بررسی و دوباره تلاش کنید"
        }
        return Result.failure(Exception(message))
    }

    app.activatedKeyStore.saveKey(key)
    UsageAccountant.attach(app.activatedKeyStore, key)
    return Result.success(Unit)
}
