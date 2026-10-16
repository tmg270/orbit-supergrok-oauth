package com.example.supergrok

import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * SuperGrok / xAI device-code OAuth client.
 * Compatible with the public Grok CLI client used by Hermes, OpenClaw, etc.
 */
class SuperGrokOAuth(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object {
        const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
        const val DEVICE_CODE_URL = "https://auth.x.ai/oauth2/device/code"
        const val TOKEN_URL = "https://auth.x.ai/oauth2/token"
        const val SCOPE = "openid profile email offline_access grok-cli:access api:access"
        const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val API_BASE = "https://api.x.ai/v1"
    }

    @Serializable
    data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
        @SerialName("expires_in") val expiresIn: Int = 600,
        @SerialName("interval") val interval: Int = 5
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int = 3600,
        @SerialName("token_type") val tokenType: String = "Bearer"
    )

    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

    suspend fun requestDeviceCode(): DeviceCodeResponse {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPE)
            .add("referrer", "orbit-supergrok")
            .build()

        val request = Request.Builder()
            .url(DEVICE_CODE_URL)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Device code request failed: ${response.code} ${response.body?.string()}")
            }
            val text = response.body?.string() ?: throw IOException("Empty body")
            return json.decodeFromString(text)
        }
    }

    suspend fun pollForToken(device: DeviceCodeResponse): TokenResponse {
        val deadline = System.currentTimeMillis() + device.expiresIn * 1000L
        var intervalMs = device.interval * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)

            val body = FormBody.Builder()
                .add("grant_type", DEVICE_CODE_GRANT)
                .add("client_id", CLIENT_ID)
                .add("device_code", device.deviceCode)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val text = response.body?.string() ?: throw IOException("Empty token body")
                    return json.decodeFromString(text)
                }
                val errorText = response.body?.string() ?: ""
                if (errorText.contains("authorization_pending")) {
                    // continue
                } else if (errorText.contains("slow_down")) {
                    intervalMs += 5000
                } else {
                    throw IOException("Token poll failed: ${response.code} $errorText")
                }
            }
        }
        throw IOException("Device authorization timed out")
    }

    suspend fun refresh(refreshToken: String): TokenResponse {
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(TOKEN_URL)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Refresh failed: ${response.code} ${response.body?.string()}")
            }
            val text = response.body?.string() ?: throw IOException("Empty refresh body")
            return json.decodeFromString(text)
        }
    }

    fun toSession(tokens: TokenResponse): Session {
        val expiresAt = System.currentTimeMillis() + (tokens.expiresIn - 120) * 1000L
        return Session(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken ?: "",
            expiresAt = expiresAt
        )
    }
}
