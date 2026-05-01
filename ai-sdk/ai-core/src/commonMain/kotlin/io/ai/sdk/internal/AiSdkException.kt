package io.ai.sdk.internal

class AiSdkException(
    val retCode: Int,
    override val message: String? = "SDK operation failed with retCode=$retCode"
) : Exception(message)
