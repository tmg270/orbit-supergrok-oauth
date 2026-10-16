// SPDX-License-Identifier: MIT
// Sketch only. Not tested against a live SuperGrok account.
// Uses only java.net.HttpURLConnection. No OkHttp, no Kotlin.

package com.example.supergrok;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Java sketch of SuperGrok device-code OAuth.
 * Addresses: RFC 8628 error parsing, refresh-token preservation,
 * single-flight refresh that re-reads after the lock, 403 as no-entitlement,
 * timeouts, no token leak in toString.
 * Not wired into any Android app.
 */
public class SuperGrokAuth {
    public static final String CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828";
    public static final String DEVICE_CODE_URL = "https://auth.x.ai/oauth2/device/code";
    public static final String TOKEN_URL = "https://auth.x.ai/oauth2/token";
    public static final String SCOPE = "openid profile email offline_access grok-cli:access api:access";
    public static final String DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code";

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int DEFAULT_EXPIRES_IN = 1800;
    private static final ReentrantLock REFRESH_LOCK = new ReentrantLock();
    // Sketch-only stand-in for a store. A real integration re-reads SecureStore here.
    private static volatile Session lastKnownSession;

    public static class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final String verificationUriComplete;
        public final int expiresIn;
        public final int interval;

        public DeviceCode(String deviceCode, String userCode, String verificationUri,
                          String verificationUriComplete, int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    public static class Session {
        public final String accessToken;
        public final String refreshToken;
        public final long expiresAt;

        public Session(String accessToken, String refreshToken, long expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }

        @Override
        public String toString() {
            return "Session{expiresAt=" + expiresAt + ", accessToken=***}";
        }
    }

    public static class OAuthException extends Exception {
        public final String errorCode;
        public OAuthException(String message) { super(message); this.errorCode = null; }
        public OAuthException(String message, String errorCode) { super(message); this.errorCode = errorCode; }
    }

    public static DeviceCode requestDeviceCode() throws IOException, OAuthException {
        String body = "client_id=" + urlEncode(CLIENT_ID)
                + "&scope=" + urlEncode(SCOPE)
                + "&referrer=" + urlEncode("orbit-supergrok-sketch");
        JSONObject json = postForm(DEVICE_CODE_URL, body);
        String error = json.optString("error", null);
        if (error != null) {
            throw new OAuthException(json.optString("error_description", error), error);
        }
        String deviceCode = json.optString("device_code", null);
        String userCode = json.optString("user_code", null);
        String uri = json.optString("verification_uri", null);
        if (deviceCode == null || userCode == null || uri == null) {
            throw new OAuthException("Incomplete device code response");
        }
        if (!uri.startsWith("https://") || !uri.contains("x.ai")) {
            throw new OAuthException("Untrusted verification_uri");
        }
        String complete = json.optString("verification_uri_complete", null);
        int expires = json.optInt("expires_in", DEFAULT_EXPIRES_IN);
        int interval = Math.max(1, json.optInt("interval", 5));
        return new DeviceCode(deviceCode, userCode, uri, complete, expires, interval);
    }

    public static Session pollForToken(DeviceCode device) throws IOException, OAuthException, InterruptedException {
        long deadline = System.currentTimeMillis() + device.expiresIn * 1000L;
        int intervalSec = device.interval;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalSec * 1000L);
            if (System.currentTimeMillis() >= deadline) break;

            String body = "grant_type=" + urlEncode(DEVICE_CODE_GRANT)
                    + "&client_id=" + urlEncode(CLIENT_ID)
                    + "&device_code=" + urlEncode(device.deviceCode);
            try {
                JSONObject json = postForm(TOKEN_URL, body);
                String error = json.optString("error", null);
                if (error == null) {
                    Session s = toSession(json, null);
                    lastKnownSession = s;
                    return s;
                }
                switch (error) {
                    case "authorization_pending":
                        break;
                    case "slow_down":
                        intervalSec += 5;
                        break;
                    case "access_denied":
                    case "expired_token":
                        throw new OAuthException("Authorization " + error, error);
                    default:
                        throw new OAuthException(json.optString("error_description", error), error);
                }
            } catch (OAuthException e) {
                if ("authorization_pending".equals(e.errorCode) || "slow_down".equals(e.errorCode)) {
                    continue;
                }
                throw e;
            }
        }
        throw new OAuthException("Device authorization timed out");
    }

    public static Session refresh(Session callerSeen) throws IOException, OAuthException {
        REFRESH_LOCK.lock();
        try {
            // Re-read after the lock. A real store (SecureStore) would be read here.
            Session now = lastKnownSession != null ? lastKnownSession : callerSeen;
            if (now == null || now.refreshToken == null || now.refreshToken.isEmpty()) {
                throw new OAuthException("No refresh token");
            }
            // If another caller already refreshed, use that result.
            if (lastKnownSession != null && lastKnownSession.expiresAt > System.currentTimeMillis() + 60_000) {
                return lastKnownSession;
            }
            String body = "grant_type=refresh_token"
                    + "&client_id=" + urlEncode(CLIENT_ID)
                    + "&refresh_token=" + urlEncode(now.refreshToken);
            JSONObject json = postForm(TOKEN_URL, body);
            String error = json.optString("error", null);
            if (error != null) {
                if ("invalid_grant".equals(error)) {
                    lastKnownSession = null;
                    throw new OAuthException("Refresh rejected; re-authenticate", error);
                }
                throw new OAuthException(json.optString("error_description", error), error);
            }
            Session updated = toSession(json, now.refreshToken);
            lastKnownSession = updated;
            return updated;
        } finally {
            REFRESH_LOCK.unlock();
        }
    }

    private static Session toSession(JSONObject json, String fallbackRefresh) throws OAuthException {
        String access = json.optString("access_token", null);
        if (access == null || access.isEmpty()) {
            throw new OAuthException("Missing access_token");
        }
        String refresh = json.optString("refresh_token", null);
        if (refresh == null || refresh.isEmpty()) {
            refresh = fallbackRefresh != null ? fallbackRefresh : "";
        }
        int expiresIn = json.optInt("expires_in", DEFAULT_EXPIRES_IN);
        long skew = Math.max(0, expiresIn - 120) * 1000L;
        long expiresAt = System.currentTimeMillis() + skew;
        return new Session(access, refresh, expiresAt);
    }

    private static JSONObject postForm(String urlString, String formBody) throws IOException, OAuthException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "orbit-supergrok-sketch/0.1");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(formBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String text = readStream(stream);

            if (code == 403) {
                throw new OAuthException("403: no entitlement for this API surface on this tier", "forbidden");
            }
            if (code >= 500) {
                throw new OAuthException("Server error " + code, "server_error");
            }

            try {
                return new JSONObject(text);
            } catch (JSONException e) {
                throw new OAuthException("Invalid JSON response (status " + code + ")");
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
