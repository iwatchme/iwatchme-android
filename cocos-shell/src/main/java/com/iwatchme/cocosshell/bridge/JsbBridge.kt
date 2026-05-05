package com.iwatchme.cocosshell.bridge

import android.annotation.SuppressLint
import android.webkit.WebView
import com.iwatchme.cocosshell.util.MainThread

/**
 * The bridge surface — owns a [WebView], routes JS→Java calls through
 * a single [JsbRouter] entry, and exposes Java→JS as [JsbHost.evalJs].
 *
 * Constructed via [Builder]; never instantiated directly. The Builder
 * pattern matches the [com.iwatchme.voiceeval.VoiceEvalEngine.Builder]
 * style already used in this codebase, and keeps WebView mutation
 * (settings, JS interface registration) confined to one method.
 *
 * The page loaded into the WebView is expected to call:
 *
 *   `NativeBridge.callStaticMethod("ReadingJsb", "startRecording",
 *                                  JSON.stringify([text, id, type]))`
 *
 * which is the JSON-array-encoded analog of Cocos's
 * `jsb.reflection.callStaticMethod`. The page should also define
 * top-level functions matching the [JsbHost.callJsFunction] callback
 * names that handlers will invoke.
 */
class JsbBridge private constructor(
    private val webView: WebView,
    private val handlers: Map<String, JsbHandler>,
) : JsbHost {

    override fun evalJs(js: String) {
        MainThread.run { webView.evaluateJavascript(js, null) }
    }

    fun load(url: String) {
        MainThread.run { webView.loadUrl(url) }
    }

    fun detach() {
        handlers.values.forEach(JsbHandler::onDetach)
        MainThread.run { webView.removeJavascriptInterface(JS_NS) }
    }

    /**
     * Fluent configuration. Default settings match what a Cocos web shell
     * would expect: JS on, file:// access on (so the extracted `gameDir`
     * sub-resources resolve), DOM storage on. Mirrors the role
     * `Cocos2dxActivity.startRenderGame()` plays for the engine path.
     */
    class Builder(private val webView: WebView) {

        private val handlers = mutableMapOf<String, JsbHandler>()

        fun register(handler: JsbHandler): Builder = apply {
            require(handlers.put(handler.name, handler) == null) {
                "duplicate JsbHandler.name: ${handler.name}"
            }
        }

        @SuppressLint("SetJavaScriptEnabled")
        fun build(): JsbBridge {
            val s = webView.settings
            s.javaScriptEnabled = true
            s.allowFileAccess = true
            // Required so an HTML page loaded from filesDir can pull
            // sibling .js / .css files via relative URLs.
            @Suppress("DEPRECATION")
            s.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            s.allowUniversalAccessFromFileURLs = true
            s.domStorageEnabled = true

            webView.addJavascriptInterface(JsbRouter(handlers), JS_NS)
            return JsbBridge(webView, handlers.toMap())
        }
    }

    companion object {
        /** JS-side global where the router lives. Page code calls
         *  `NativeBridge.callStaticMethod(...)`. */
        const val JS_NS = "NativeBridge"
    }
}
