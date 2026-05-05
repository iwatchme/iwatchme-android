package com.iwatchme.cocosshell.bridge

/**
 * One feature unit on the bridge. Plays the same role that classes like
 * `ReadingJsb`, `ShareJsb`, `LoginJsb` play in the original Jiliguala
 * project — each holds the `@JvmStatic` methods callable from JS for one
 * concern. Here we model it as an interface so the dispatch table in
 * [JsbRouter] stays uniform.
 *
 * Add a new feature = new `JsbHandler` impl + one `register(...)` call
 * on the [JsbBridge.Builder]. Bridge core never changes.
 */
interface JsbHandler {

    /** Class name surfaced to JS — e.g. `"ReadingJsb"`. */
    val name: String

    /**
     * Dispatch [method] with [args] decoded from the JSON array the JS
     * side sent. Args are raw strings; the handler is responsible for
     * decoding numeric/JSON payloads.
     */
    fun dispatch(method: String, args: List<String>)

    /** Hook for releasing resources when the bridge tears down. */
    fun onDetach() {}
}
