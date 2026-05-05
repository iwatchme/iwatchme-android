package com.iwatchme.cocosshell.download

import android.content.Context
import java.io.File

/**
 * Disk paths used by the download manager. Names mirror the original
 * Jiliguala layout (`StorageMgr.PATH_GAME` / `getCocosGameDir()`):
 *
 *  ```
 *  filesDir/rootfiles/
 *    ├── downloadfile/game/{name}.zip   ← temp download payload
 *    └── cocosgame/                     ← final extracted "game" dir
 *        └── success.txt                ← md5(url) integrity sentinel
 *  ```
 *
 * Kept as paths under `filesDir` rather than external storage because that
 * keeps the demo working without runtime storage permission and makes the
 * cache scoped to the app — same choice as the original.
 */
internal object StorageLayout {
    fun zipFile(context: Context, name: String): File =
        File(context.filesDir, "rootfiles/downloadfile/game/$name.zip")

    fun gameDir(context: Context): File =
        File(context.filesDir, "rootfiles/cocosgame")

    fun successSentinel(context: Context): File =
        File(gameDir(context), "success.txt")
}
