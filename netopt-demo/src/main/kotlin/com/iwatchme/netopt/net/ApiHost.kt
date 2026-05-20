package com.iwatchme.netopt.net

import android.os.Build

/**
 * Pick the base URL the Android client should use to reach the Spring Boot
 * service running on the developer's Mac.
 *
 *  - Emulator: 10.0.2.2 is the host loopback alias.
 *  - Real device on the same Wi-Fi: replace with the Mac's LAN IP (e.g. 192.168.1.42).
 *  - Remote / 4G: point to the frp tunnel domain (Step 8).
 *
 * Step 2 keeps it deliberately simple — cleartext :8080, no Caddy in front.
 */
object ApiHost {
    val baseUrl: String = run {
        val onEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
                Build.MODEL.contains("sdk", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true)
        // HTTPS via Caddy on the host Mac (port 4443 to skip sudo for <1024).
        // mkcert root CA is bundled in res/raw and trusted by network_security_config.
        if (onEmulator) "https://10.0.2.2:4443" else "https://192.168.1.1:4443"
    }
}
