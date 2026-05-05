package com.iwatchme.cocosshell.bridge

/**
 * Java→JS surface exposed to handlers. Direct analog of the original
 * `Cocos2dxJavascriptJavaBridge.evalString(js)` call wrapped in
 * `Cocos2dxHelper.runOnGLThread { … }` — handlers don't need to know
 * about thread hops or how the JS evaluator is wired (Cocos JSB vs
 * WebView), they just push JS strings.
 */
interface JsbHost {

    /**
     * Evaluate a raw JS expression. Implementations must hop to the
     * thread the JS engine requires (UI thread for WebView, GL thread
     * for Cocos2d-x). Idempotent against threading: calling from any
     * thread is safe.
     */
    fun evalJs(js: String)

    /**
     * Convenience wrapper for `funcName('jsonArg')` — the most common
     * Native→JS call shape (single string-payload callback). Mirrors
     * the original `ReadingJsb.onRecordResult(result: String)` shape:
     *
     *   `evalString("onRecordResult('{...}')")`
     *
     * Single-quote and backslash characters in [jsonArg] are escaped
     * so a payload containing `it's` won't break the wrapping quotes.
     */
    fun callJsFunction(funcName: String, jsonArg: String) {
        val safe = jsonArg.replace("\\", "\\\\").replace("'", "\\'")
        evalJs("$funcName('$safe')")
    }
}
