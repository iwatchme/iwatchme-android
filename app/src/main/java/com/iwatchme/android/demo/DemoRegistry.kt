package com.iwatchme.android.demo

import com.iwatchme.android.demo.asr.AsrDemoScreen
import com.iwatchme.android.demo.cocosshell.CocosShellDemoScreen
import com.iwatchme.android.demo.crash.CrashLibDemoScreen
import com.iwatchme.android.demo.player.PlayerDemoScreen
import com.iwatchme.android.demo.renderengine.RenderEngineDemoScreen
import com.iwatchme.android.demo.thread.ThreadDetectorScreen
import com.iwatchme.android.demo.translation.TranslationDemoScreen
import com.iwatchme.android.demo.tts.TtsDemoScreen
import com.iwatchme.android.demo.voiceeval.VoiceEvalDemoScreen
import com.iwatchme.startuplab.ui.StartupInspectorScreen

object DemoRegistry {

    private val registeredDemos: List<DemoEntry> = listOf(
        DemoEntry(
            route = "demo/thread-detector",
            title = "Thread Inspector",
            description = "View all detected Thread, ThreadPoolExecutor, and Executors.newXxx() creation events, including coroutine dispatcher threads.",
            content = {
                ThreadDetectorScreen()
            },
        ),
        DemoEntry(
            route = "demo/startup-inspector",
            title = "Startup Inspector",
            description = "Inspect provider cost, phase breakdown, timeline, and mode comparison for the current cold start.",
            content = {
                StartupInspectorScreen()
            },
        ),
        DemoEntry(
            route = "demo/crashlib",
            title = "CrashLib Demo",
            description = "Initialize CrashLib manually and trigger a native crash for verification.",
            content = {
                CrashLibDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/player",
            title = "Player",
            description = "Multi-scope Dagger 2 video player with ExoPlayer (Media3), demonstrating PageScope / BizScope / MediaScope architecture.",
            content = {
                PlayerDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/render-engine",
            title = "Render Engine",
            description = "Video playback powered by FFmpeg + MediaCodec hardware decoding with OpenGL ES render tree.",
            content = {
                RenderEngineDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/tts",
            title = "TTS Demo",
            description = "Cloudflare Aura-2 text-to-speech with 39 voices, text input, and MP3 playback.",
            content = {
                TtsDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/translation",
            title = "Translation Demo",
            description = "Cloudflare M2M100 multilingual translation with 28 language pairs.",
            content = {
                TranslationDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/asr",
            title = "ASR Demo",
            description = "Cloudflare Whisper automatic speech recognition from audio files.",
            content = {
                AsrDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/voice-eval",
            title = "Voice Evaluation",
            description = "Speech-scoring pipeline: real AudioRecord + WAV encoder + 4KB stream slicer + mockable scorer/uploader, modeled on the original Tencent SOE production flow.",
            content = {
                VoiceEvalDemoScreen()
            },
        ),
        DemoEntry(
            route = "demo/cocos-shell",
            title = "Cocos Bridge & Downloader",
            description = "Faithful reproduction of the Jiliguala Cocos container: :cocos_game process + Messenger IPC + URL-keyed package cache + WebView-hosted JS↔Native bridge driving the voice-eval engine.",
            content = {
                CocosShellDemoScreen()
            },
        ),
    )

    init {
        val duplicatedRoutes = registeredDemos
            .groupBy { it.route }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatedRoutes.isEmpty()) {
            "Duplicate demo routes found: ${duplicatedRoutes.joinToString()}"
        }
    }

    val demos: List<DemoEntry>
        get() = registeredDemos
}
