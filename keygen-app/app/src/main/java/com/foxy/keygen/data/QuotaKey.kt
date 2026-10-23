package com.foxy.keygen.data

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** Same wire format as the FoxyVPN client expects — see QuotaKey.kt there. */
@Serializable
data class QuotaKeyPayload(
    val v: Int = 1,
    val name: String = "",
    val gb: Long = 0L,
    val exp: Long = 0L,
    val id: String = "",
    val rt: String = "",
    val gist: String = "",
    val gt: String = "",
) {
    val isUnlimited: Boolean get() = gb <= 0L
}

object QuotaKeyCodec {

    /** Matches the public key embedded in the FoxyVPN client. */
    const val PUB_B64 = "8b705IT0MNKJEXm+zwlKjlGiFazvWIkIoIYLR12cAvE="

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun b64urlEncode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun mint(
        name: String,
        gb: Long,
        expiresAtEpochSeconds: Long,
        refreshToken: String,
        gistId: String,
        githubToken: String,
        privateKeyB64: String,
    ): String {
        require(privateKeyB64.isNotBlank()) { "کلید امضا در این نسخه تعبیه نشده است" }
        val payload = QuotaKeyPayload(
            v = 1,
            name = name,
            gb = gb,
            exp = expiresAtEpochSeconds,
            id = randomId(),
            rt = refreshToken,
            gist = gistId,
            gt = githubToken,
        )
        val payloadJson = json.encodeToString(QuotaKeyPayload.serializer(), payload)
        val payloadB64 = b64urlEncode(payloadJson.toByteArray(Charsets.UTF_8))
        val priv = Base64.decode(privateKeyB64, Base64.NO_WRAP)
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(priv, 0))
        signer.update(payloadB64.toByteArray(Charsets.US_ASCII), 0, payloadB64.length)
        val sig = b64urlEncode(signer.generateSignature())
        return "FV1.$payloadB64.$sig"
    }

    /** Verifies a minted key against the embedded public key (self-check). */
    fun verifySelf(key: String): Boolean = runCatching {
        val parts = key.split(".")
        if (parts.size != 3 || parts[0] != "FV1") return false
        val pub = Base64.decode(PUB_B64, Base64.NO_WRAP)
        val sig = runCatching {
            var b64 = parts[2]
            while (b64.length % 4 != 0) b64 += "="
            Base64.decode(b64, Base64.URL_SAFE or Base64.NO_WRAP)
        }.getOrNull() ?: return false
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(pub, 0))
        verifier.update(parts[1].toByteArray(Charsets.US_ASCII), 0, parts[1].length)
        verifier.verifySignature(sig)
    }.getOrDefault(false)

    fun randomId(): String {
        val bytes = ByteArray(4)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
