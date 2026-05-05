package com.iwatchme.cocosshell.download

import android.content.Context
import com.iwatchme.cocosshell.util.Md5
import java.io.File

/**
 * URL-keyed cache for the extracted package. This is the version-control /
 * hot-update hook in the original ggr project: when the server returns a
 * new package URL, we know to re-run the download/unzip pipeline; when the
 * URL hasn't changed, we serve the on-disk copy unchanged.
 *
 * Two layers of evidence are required for a hit:
 *
 *  1. SharedPreferences[`PREFS_KEY_LAST_BASE_PACKAGE_URL`] equals the
 *     requested URL.
 *  2. `success.txt` exists in the extracted dir and its content equals
 *     `md5(url)` — this catches the case where unzip got interrupted
 *     mid-extraction and we have a partial dir on disk.
 *
 * Anything else falls back to "miss", which the manager handles by wiping
 * `gameDir` before re-downloading. Mirrors `UnZipObject.completeUnzip`'s
 * post-condition in the original (writes md5 to `success.txt`) plus
 * `CocosBaseDownloadMgr.startDownloadBasePackageUrl`'s pre-check.
 */
class DownloadCache(private val context: Context) {

    private val prefs by lazy {
        context.getSharedPreferences("cocos_shell_download", Context.MODE_PRIVATE)
    }

    fun hit(url: String): File? {
        val lastUrl = prefs.getString(PREFS_KEY_LAST_BASE_PACKAGE_URL, null) ?: return null
        if (lastUrl != url) return null

        val gameDir = StorageLayout.gameDir(context)
        val sentinel = StorageLayout.successSentinel(context)
        if (!gameDir.exists() || !sentinel.exists()) return null

        val expected = Md5.hex(url)
        val actual = runCatching { sentinel.readText().trim() }.getOrNull()
        return if (actual == expected) gameDir else null
    }

    fun markSuccess(url: String) {
        StorageLayout.successSentinel(context).writeText(Md5.hex(url))
        prefs.edit().putString(PREFS_KEY_LAST_BASE_PACKAGE_URL, url).apply()
    }

    fun invalidate() {
        StorageLayout.gameDir(context).deleteRecursively()
        prefs.edit().remove(PREFS_KEY_LAST_BASE_PACKAGE_URL).apply()
    }

    companion object {
        const val PREFS_KEY_LAST_BASE_PACKAGE_URL = "PREFS_KEY_LAST_BASE_PACKAGE_URL"
    }
}
