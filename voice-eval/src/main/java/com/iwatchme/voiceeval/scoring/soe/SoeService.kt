package com.iwatchme.voiceeval.scoring.soe

/**
 * SOE 协议的传输层抽象。
 *
 * 生产实现是一个 HTTPS 客户端：把请求字段签名后 POST 到 `soe.tencentcloudapi.com`；
 * 仓库自带的 [MockSoeService] 是同进程内的纯本地仿真，不发任何真实网络请求。
 *
 * 把 mock 换成生产实现时，调用方
 * [com.iwatchme.voiceeval.scoring.MockVoiceScorer] 的代码完全无需修改 ——
 * 这正是把协议层抽象出来的核心目的。
 *
 * Transport-layer abstraction for the SOE protocol.
 *
 * Production implementations would sign and POST to `soe.tencentcloudapi.com` over
 * HTTPS; the in-repo [MockSoeService] is an in-process simulation that never touches
 * the network.
 *
 * Swapping the mock for a real client requires zero changes in the caller
 * [com.iwatchme.voiceeval.scoring.MockVoiceScorer] — that's the whole point of
 * isolating the protocol layer.
 */
interface SoeService {

    /**
     * 发送一片音频（或纯查询请求）。
     *
     * 协议级约束：
     *  - [SoeTransmitRequest.seqId] 必须从 1 起步、单调连续递增。
     *  - 中间片返回的 [SoeResponse] 状态恒为 [SoeStatus.Evaluating]，
     *    数值字段无意义。
     *  - [SoeTransmitRequest.isEnd] = 1 之后服务端开始对齐 + 打分；
     *    若返回的状态仍是 Evaluating，调用方应再发 [SoeTransmitRequest.isQuery] = 1
     *    的请求轮询，直到状态变为 [SoeStatus.Finished]。
     *
     * Send one audio fragment (or a query-only request).
     *
     * Wire-protocol constraints:
     *  - [SoeTransmitRequest.seqId] must start at 1 and increase monotonically.
     *  - Intermediate frames always return [SoeStatus.Evaluating] with the
     *    numeric fields meaningless.
     *  - After [SoeTransmitRequest.isEnd] = 1 the server starts the final
     *    alignment + scoring; if the response is still `Evaluating`, the caller
     *    should poll with [SoeTransmitRequest.isQuery] = 1 until [SoeStatus.Finished].
     */
    suspend fun transmit(request: SoeTransmitRequest): SoeResponse
}
