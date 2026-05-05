package com.iwatchme.cocosshell.download

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Streaming unzip. Mirrors the original `ZipMgr.kt`:
 *  - `BufferedInputStream` wrapped around `ZipInputStream`
 *  - 4MB read buffer (the original constant)
 *  - per-byte progress callback so the UI can show smooth movement
 *
 * Uses cumulative byte count over `zipFile.length()` for progress
 * estimation, which slightly under-reports because compressed-byte
 * accounting differs from uncompressed-byte streaming through
 * `ZipInputStream`. Good enough for the demo bar.
 */
class ZipExtractor {

    fun extract(zipFile: File, destDir: File, onProgress: (Float) -> Unit) {
        require(zipFile.exists()) { "zip not found: $zipFile" }
        destDir.mkdirs()

        val totalSize = zipFile.length().coerceAtLeast(1L)
        var bytesRead = 0L
        val buffer = ByteArray(BUF)

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Zip-slip protection: refuse entries that escape destDir.
                val canonicalDest = destDir.canonicalPath
                val canonicalOut = outFile.canonicalPath
                if (!canonicalOut.startsWith(canonicalDest + File.separator) &&
                    canonicalOut != canonicalDest
                ) {
                    throw SecurityException("zip entry escapes destDir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                        var n = zis.read(buffer)
                        while (n != -1) {
                            bos.write(buffer, 0, n)
                            bytesRead += n
                            onProgress((bytesRead.toFloat() / totalSize).coerceIn(0f, 1f))
                            n = zis.read(buffer)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        onProgress(1f)
    }

    companion object {
        const val BUF = 4 * 1024 * 1024
    }
}
