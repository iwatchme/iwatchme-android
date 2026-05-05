package com.iwatchme.cocosshell.service

/**
 * Wire protocol for the cross-process Messenger between the main process
 * (where [GameMessageService] lives and runs the download manager) and
 * the `:cocos_game` process (where the game Activity hosts the WebView).
 *
 * Same name and same role as the original Jiliguala `GameMessageService`
 * which served the same purpose — main-process-owned download
 * coordinator + game-process consumer. Constants are kept simple ints
 * because Messenger demands the protocol be parcelable + small.
 *
 * Message direction:
 *
 *  - REGISTER_CLIENT: game → service. After bind, game sets `replyTo` to
 *    its own incoming Messenger so the service can push progress back.
 *  - CHECK_DOWNLOAD: game → service. Carries `KEY_URL` in `data`; tells
 *    the service to start `ensureGamePackage(url)`.
 *  - INVALIDATE: game → service. Wipes the cache before next round.
 *  - PROGRESS: service → game. `data` carries `KEY_PHASE` + `KEY_PERCENT`.
 *  - RESULT: service → game. `data` carries `KEY_SUCCESS`, then either
 *    `KEY_GAME_DIR` + `KEY_CACHED` (success) or `KEY_ERROR` (failure).
 *  - DETACH: game → service. Tells the service to drop the client.
 */
object GameMessages {
    const val MSG_REGISTER_CLIENT = 1
    const val MSG_CHECK_DOWNLOAD = 2
    const val MSG_PROGRESS = 3
    const val MSG_RESULT = 4
    const val MSG_DETACH = 5
    const val MSG_INVALIDATE = 6

    const val KEY_URL = "url"
    const val KEY_PHASE = "phase"
    const val KEY_PERCENT = "percent"
    const val KEY_SUCCESS = "success"
    const val KEY_GAME_DIR = "gameDir"
    const val KEY_CACHED = "cached"
    const val KEY_ERROR = "error"

    const val PHASE_DOWNLOADING = "downloading"
    const val PHASE_UNZIPPING = "unzipping"
}
