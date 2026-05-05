# 对外公开的 API —— 保留所有入口符号，避免下游使用者被 R8/ProGuard 混淆掉。
# Public API surface — keep all entry points reachable for downstream consumers.
-keep class com.iwatchme.voiceeval.VoiceEvalEngine { *; }
-keep class com.iwatchme.voiceeval.VoiceEvalEngine$Builder { *; }
-keep class com.iwatchme.voiceeval.api.** { *; }
