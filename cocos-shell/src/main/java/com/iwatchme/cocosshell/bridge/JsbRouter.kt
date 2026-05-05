package com.iwatchme.cocosshell.bridge

import android.webkit.JavascriptInterface
import org.json.JSONArray

/**
 * Single `@JavascriptInterface` object exposed to the WebView. Mirrors
 * the shape of Cocos's `jsb.reflection.callStaticMethod(className,
 * methodName, sig, args...)` so JS code ports across with one rename of
 * the namespace global.
 *
 * **Threading:** `@JavascriptInterface` methods are invoked on a
 * WebView-internal binder thread, NOT the main thread — Android's
 * documented contract. Handlers must hop themselves if their
 * implementations require the main thread (or a coroutine scope).
 */
internal class JsbRouter(private val handlers: Map<String, JsbHandler>) {

    @JavascriptInterface
    fun callStaticMethod(className: String, method: String, argsJson: String) {
        val arr = try {
            JSONArray(argsJson)
        } catch (_: Throwable) {
            // Be lenient — JS side might pass an empty/missing args
            // payload. Handler will see an empty list and decide.
            JSONArray()
        }
        val args = List(arr.length()) { arr.optString(it) }
        handlers[className]?.dispatch(method, args)
    }
}
