package com.iwatchme.cocosshell.util

import android.os.Handler
import android.os.Looper

/**
 * UI-thread hop. WebView APIs (`evaluateJavascript`, `loadUrl`,
 * `addJavascriptInterface`, `settings.*`) must be called on the thread
 * the WebView was created on — the main thread, in our case. This is the
 * direct analog of `Cocos2dxHelper.runOnGLThread { … }` used in the
 * original ggr project: same role (single allowed thread for engine
 * interaction), different thread.
 */
internal object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    inline fun run(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else handler.post { block() }
    }
}
