package com.iwatchme.voiceeval.upload

import java.io.File

/**
 * 把录好的最终音频文件上传到设备外部的策略接口。
 *
 * 故意和 [com.iwatchme.voiceeval.scoring.VoiceScorer] 解耦：
 * 引擎把「打分」和「持久化」视作两件相互独立的事，
 * 任意一方失败都不会拖垮另一方 —— 慢吞吞的 CDN 不该阻塞用户查看分数。
 *
 * 生产环境的实现通常是七牛 Android SDK 的薄包装。
 *
 * Strategy for shipping the finalized audio file off the device.
 *
 * Independent of [com.iwatchme.voiceeval.scoring.VoiceScorer] on purpose:
 * the engine treats scoring and persistence as two unrelated concerns so
 * that one can fail without taking the other down — a slow CDN should
 * never block the user from seeing their score.
 *
 * Production drop-in would be a thin wrapper over the QiNiu Android SDK.
 */
interface AudioUploader {

    /**
     * 成功时返回可公开访问的 URL。
     * 失败时实现应抛异常，让引擎决定重试还是回退到「仅本地播放」。
     *
     * Returns a publicly-resolvable URL on success. Implementations should
     * throw on failure so the engine can decide whether to retry or fall
     * back to local-only playback.
     */
    suspend fun upload(file: File, key: String): String
}
