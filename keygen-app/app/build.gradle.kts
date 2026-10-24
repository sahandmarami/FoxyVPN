import java.util.Base64
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The Ed25519 quota-signing private key lives (lightly obfuscated) in
// signing/signing-key.sec so rebuilds never lose it; a -PFOXY_PRIVATE_KEY=
// <base64> override is still honoured for manual rotations.
val foxyPrivateKey: String = (project.findProperty("FOXY_PRIVATE_KEY") as String?)
    ?: run {
        val secFile = rootProject.file("signing/signing-key.sec")
        if (!secFile.exists()) {
            ""
        } else {
            runCatching {
                val data = Base64.getDecoder().decode(secFile.readText().trim().reversed())
                val key = "FoxyVPN-2026-signing".toByteArray(Charsets.UTF_8)
                val out = ByteArray(data.size)
                for (i in data.indices) out[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
                Base64.getEncoder().encodeToString(out)
            }.getOrDefault("")
        }
    }

android {
    namespace = "com.foxy.keygen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.foxy.keygen"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"
        buildConfigField("String", "FOXY_PRIVATE_KEY", "\"$foxyPrivateKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release-keystore.jks")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "foxyvpn"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "foxyvpn"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "foxyvpn"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
