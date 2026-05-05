# Public API surface
-keep class com.iwatchme.cocosshell.bridge.JsbBridge { *; }
-keep class com.iwatchme.cocosshell.bridge.JsbBridge$Builder { *; }
-keep class com.iwatchme.cocosshell.bridge.JsbHost { *; }
-keep class com.iwatchme.cocosshell.bridge.JsbHandler { *; }
-keep class com.iwatchme.cocosshell.bridge.handlers.** { *; }
-keep class com.iwatchme.cocosshell.download.** { *; }
-keep class com.iwatchme.cocosshell.service.** { *; }

# @JavascriptInterface methods must survive R8 — JS engine looks them up reflectively.
-keepclassmembers class com.iwatchme.cocosshell.bridge.JsbRouter {
    @android.webkit.JavascriptInterface <methods>;
}
