package com.iwatchme.cocosshell.util

import java.security.MessageDigest

/**
 * Hex MD5 of the UTF-8 bytes of [input]. Used as the URL→cache-sentinel
 * mapping that mirrors `StringUtils.getMD5Name(url)` from the original
 * Jiliguala project, where `success.txt` stores `md5(url)` to validate
 * that an extracted package corresponds to the URL the prefs say it does.
 */
internal object Md5 {
    fun hex(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX[v ushr 4])
                append(HEX[v and 0x0F])
            }
        }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
