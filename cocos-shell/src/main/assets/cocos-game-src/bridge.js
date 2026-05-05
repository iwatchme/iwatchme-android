// JS-side mirror of Cocos's `jsb.reflection.callStaticMethod` surface.
// Native (Android) installs a single `NativeBridge` global via
// WebView.addJavascriptInterface, which exposes one method:
//
//   NativeBridge.callStaticMethod(className, method, argsJson)
//
// The shim below adapts the variadic Cocos API to that single entry, so
// page code that originally called
//
//   jsb.reflection.callStaticMethod("ReadingJsb","startRecording",
//                                   "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
//                                   text, id, type)
//
// can be ported with a one-line rename of the namespace.
(function () {
  function call(className, method) {
    var args = Array.prototype.slice.call(arguments, 2).map(function (a) {
      return a == null ? "" : String(a);
    });
    if (window.NativeBridge && typeof window.NativeBridge.callStaticMethod === "function") {
      window.NativeBridge.callStaticMethod(className, method, JSON.stringify(args));
    } else {
      console.warn("NativeBridge missing — running in a regular browser?");
    }
  }

  window.jsb = window.jsb || {};
  window.jsb.reflection = window.jsb.reflection || {};
  window.jsb.reflection.callStaticMethod = call;

  // Native→JS callback site. Native invokes:
  //   evaluateJavascript("onRecordResult('{\"score\":85,...}')")
  // We dispatch a CustomEvent so multiple subscribers can listen.
  window.onRecordResult = function (jsonStr) {
    try {
      var detail = JSON.parse(jsonStr);
      document.dispatchEvent(new CustomEvent("record-result", { detail: detail }));
    } catch (e) {
      console.error("onRecordResult: bad JSON", e, jsonStr);
    }
  };

  window.onRecordError = function (msg) {
    document.dispatchEvent(new CustomEvent("record-error", { detail: msg }));
  };
})();
