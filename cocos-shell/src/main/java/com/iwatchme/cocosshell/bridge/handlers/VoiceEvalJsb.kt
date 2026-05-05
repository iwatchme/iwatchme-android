package com.iwatchme.cocosshell.bridge.handlers

import android.Manifest
import androidx.annotation.RequiresPermission
import com.iwatchme.cocosshell.bridge.JsbHandler
import com.iwatchme.cocosshell.bridge.JsbHost
import com.iwatchme.voiceeval.VoiceEvalEngine
import com.iwatchme.voiceeval.api.EvalRequest
import com.iwatchme.voiceeval.api.EvalResult
import com.iwatchme.voiceeval.api.EvalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * 1:1 reproduction of the original Jiliguala `ReadingJsb` surface. Same
 * class name, same method names, same call-shape — JS code from the
 * original Cocos game would compile against this with a single rename
 * of `jsb.reflection.callStaticMethod` → `NativeBridge.callStaticMethod`
 * (plus the JSON-encoded args list).
 *
 * Result JSON shape matches `RecordTemplate.getJsbResult()`:
 *
 *   `{score, realScore, refText, Words[{char, score, realScore}],
 *     Integrity, Amplitude}`
 *
 * The `realScore`/`Integrity`/`Amplitude` fields are stubbed to a flat
 * value here because the real Tencent SOE response carries them
 * separately and we're mocking the scorer — but the keys exist so the JS
 * page renders cleanly.
 */
class VoiceEvalJsb(
    private val engine: VoiceEvalEngine,
    private val host: JsbHost,
    private val scope: CoroutineScope,
) : JsbHandler {

    override val name: String = "ReadingJsb"

    private var inFlight: Job? = null

    override fun dispatch(method: String, args: List<String>) {
        when (method) {
            "startRecording" -> {
                val text = args.getOrNull(0).orEmpty()
                val id = args.getOrNull(1)
                val type = args.getOrNull(2).orEmpty()
                startRecording(text, id, type)
            }
            "startRecordingWithPron" -> {
                // Original signature: (wordListJson, id, type). For the
                // demo we extract the concatenated text from the first
                // arg and feed the engine — pronunciation-level scoring
                // is not part of the demo's mock scorer.
                val wordListJson = args.getOrNull(0).orEmpty()
                val id = args.getOrNull(1)
                val type = args.getOrNull(2).orEmpty()
                startRecording(extractRefText(wordListJson), id, type)
            }
            "stopRecording" -> stopRecording()
            else -> Unit  // unknown methods are silently dropped, matching Cocos behavior
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(text: String, id: String?, type: String) {
        // One round at a time. Cancel any prior collection — the engine
        // itself rejects overlapping rounds via its phase machine, but
        // cancelling our collector unblocks the caller cleanly.
        inFlight?.cancel()
        val request = EvalRequest(
            id = id ?: "jsb-${System.currentTimeMillis()}",
            refText = text,
        )
        inFlight = scope.launch {
            engine.evaluate(request)
                .onEach(::observe)
                .collect()
            inFlight = null
        }
    }

    fun stopRecording() {
        engine.stop()
    }

    override fun onDetach() {
        inFlight?.cancel()
        engine.stop()
    }

    private fun observe(state: EvalState) {
        when (state) {
            is EvalState.Completed -> host.callJsFunction(
                "onRecordResult",
                state.result.toJsbJson(),
            )
            is EvalState.Failed -> host.callJsFunction(
                "onRecordError",
                state.error.message ?: state.error.javaClass.simpleName,
            )
            else -> Unit  // Idle / Preparing / Recording / SilenceHint / Scoring — UI feedback is JS-side concern
        }
    }

    private fun extractRefText(wordListJson: String): String {
        // Best-effort: original payload was `{wordlist:[{word, pron:[...]} ...]}`.
        return runCatching {
            val arr = JSONObject(wordListJson).optJSONArray("wordlist") ?: return ""
            buildString {
                for (i in 0 until arr.length()) {
                    if (i > 0) append(' ')
                    append(arr.getJSONObject(i).optString("word", ""))
                }
            }
        }.getOrDefault("")
    }
}

/**
 * Result envelope matching the original `RecordTemplate.getJsbResult()`.
 * Field names are PascalCase because that's what the JS code in ggr was
 * coded against — preserved verbatim so demo HTML can be ported across.
 */
internal fun EvalResult.toJsbJson(): String {
    val words = JSONArray()
    for (w in this.words) {
        words.put(
            JSONObject()
                .put("char", w.word)
                .put("score", w.score)
                .put("realScore", w.score),
        )
    }
    return JSONObject()
        .put("score", overallScore)
        .put("realScore", overallScore)
        .put("refText", request.refText)
        .put("Words", words)
        .put("Integrity", 100)
        .put("Amplitude", 0)
        .toString()
}
