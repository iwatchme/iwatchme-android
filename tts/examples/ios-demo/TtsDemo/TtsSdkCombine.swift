import Combine
import TtsSdk

// MARK: - Preview Publisher (Flow<TtsEvent> → AnyPublisher)

/// 将 TtsEngineIos.preview() 的回调封装为 Combine Publisher。
/// 每次 subscribe 自动开始流式预览，cancel 时自动取消 Kotlin 侧协程。
struct TtsPreviewPublisher: Publisher {
    typealias Output = TtsEvent
    typealias Failure = TtsError

    let engine: TtsEngineIos
    let text: String
    let source: Int32
    let voice: TtsVoiceParams

    func receive<S: Subscriber>(subscriber: S) where S.Input == Output, S.Failure == Failure {
        let subscription = TtsPreviewSubscription(
            subscriber: subscriber,
            engine: engine,
            text: text,
            source: source,
            voice: voice
        )
        subscriber.receive(subscription: subscription)
    }
}

private final class TtsPreviewSubscription<S: Subscriber>: Subscription
    where S.Input == TtsEvent, S.Failure == TtsError
{
    private var subscriber: S?
    private var cancellable: Cancellable?

    init(subscriber: S, engine: TtsEngineIos, text: String, source: Int32, voice: TtsVoiceParams) {
        self.subscriber = subscriber
        self.cancellable = engine.preview(
            text: text,
            source: source,
            voice: voice
        ) { [weak self] event in
            guard let self, let sub = self.subscriber else { return }

            if event is TtsEvent.StreamEnd {
                sub.receive(completion: .finished)
                self.subscriber = nil
            } else if let error = event as? TtsEvent.Error {
                sub.receive(completion: .failure(
                    TtsError(retCode: Int(error.retCode), message: error.message)
                ))
                self.subscriber = nil
            } else {
                _ = sub.receive(event)
            }
        }
    }

    func request(_ demand: Subscribers.Demand) {}

    func cancel() {
        cancellable?.cancel()
        cancellable = nil
        subscriber = nil
    }
}

// MARK: - Generate Future (suspend → Future)

/// 将 TtsEngineIos.generate() 封装为 Combine Future。
func ttsGenerate(
    engine: TtsEngineIos,
    items: [TtsItem],
    voice: TtsVoiceParams,
    onProgress: ((Int32, Int32) -> Void)? = nil
) -> Future<[TtsGenerateResult], TtsError> {
    Future { promise in
        engine.generate(
            items: items,
            voice: voice,
            onProgress: onProgress,
            onComplete: { results in
                promise(.success(results as! [TtsGenerateResult]))
            },
            onError: { message in
                promise(.failure(TtsError(retCode: -1, message: message)))
            }
        )
    }
}

// MARK: - Error Type

struct TtsError: Error, LocalizedError {
    let retCode: Int
    let message: String?

    var errorDescription: String? {
        message ?? "TTS error (code: \(retCode))"
    }
}

// MARK: - Convenience Extensions

extension TtsEngineIos {

    /// Combine 版流式预览
    func previewPublisher(
        text: String,
        source: Int32,
        voice: TtsVoiceParams
    ) -> AnyPublisher<TtsEvent, TtsError> {
        TtsPreviewPublisher(
            engine: self,
            text: text,
            source: source,
            voice: voice
        ).eraseToAnyPublisher()
    }

    /// Combine 版批量合成
    func generatePublisher(
        items: [TtsItem],
        voice: TtsVoiceParams,
        onProgress: ((Int32, Int32) -> Void)? = nil
    ) -> AnyPublisher<[TtsGenerateResult], TtsError> {
        ttsGenerate(
            engine: self,
            items: items,
            voice: voice,
            onProgress: onProgress
        ).eraseToAnyPublisher()
    }
}
