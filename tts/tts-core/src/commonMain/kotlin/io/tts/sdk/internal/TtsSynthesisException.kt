package io.tts.sdk.internal

class TtsSynthesisException(
    val retCode: Int,
    override val message: String? = "TTS synthesis failed with retCode=$retCode"
) : Exception(message)
