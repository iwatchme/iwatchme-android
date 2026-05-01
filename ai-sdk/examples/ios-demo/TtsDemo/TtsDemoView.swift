import SwiftUI
import Combine
import TtsSdk

// MARK: - ViewModel

@MainActor
final class TtsDemoViewModel: ObservableObject {

    // UI state
    @Published var inputText = "你好，这是一段 TTS 测试文本。"
    @Published var status = "就绪"
    @Published var pcmChunksReceived = 0
    @Published var generateResults: [TtsGenerateResult] = []
    @Published var progress: Float = 0
    @Published var isLoading = false

    private var engine: TtsEngineIos?
    private var cancellables = Set<AnyCancellable>()

    // -------------------------------------------------------
    // 初始化 TtsEngine
    // -------------------------------------------------------
    func setup() {
        // ⚠️ 替换为你的真实 ITtsSdk 实现
        // 这里用 Builder 演示结构，实际需要注入你的 SDK 实例
        let cacheDir = NSSearchPathForDirectoriesInDomains(
            .cachesDirectory, .userDomainMask, true
        ).first! + "/tts-cache"

        let kotlinEngine = TtsEngine.Builder()
            // .sdk(source: 1, sdk: YourRealTtsSdk())
            .cacheDirPath(path: cacheDir)
            .build()

        engine = TtsEngineIos(engine: kotlinEngine)
        status = "引擎已初始化"
    }

    // -------------------------------------------------------
    // 流式预览 (Combine)
    // -------------------------------------------------------
    func startPreview() {
        guard let engine else {
            status = "引擎未初始化"
            return
        }

        pcmChunksReceived = 0
        isLoading = true
        status = "预览中..."

        let voice = TtsVoiceParams(voiceType: "default")

        engine.previewPublisher(text: inputText, source: 1, voice: voice)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { [weak self] completion in
                    guard let self else { return }
                    self.isLoading = false
                    switch completion {
                    case .finished:
                        self.status = "预览完成，共收到 \(self.pcmChunksReceived) 个音频片段"
                    case .failure(let error):
                        self.status = "预览失败: \(error.localizedDescription)"
                    }
                },
                receiveValue: { [weak self] event in
                    if event is TtsEvent.AudioChunk {
                        self?.pcmChunksReceived += 1
                        // 这里可以把 pcm 数据喂给 AudioSink / AVAudioEngine
                    }
                }
            )
            .store(in: &cancellables)
    }

    // -------------------------------------------------------
    // 批量合成 (Combine)
    // -------------------------------------------------------
    func startGenerate() {
        guard let engine else {
            status = "引擎未初始化"
            return
        }

        isLoading = true
        progress = 0
        status = "合成中..."

        let items = inputText
            .components(separatedBy: "。")
            .filter { !$0.isEmpty }
            .map { TtsItem(text: $0, source: 1) }

        let voice = TtsVoiceParams(voiceType: "default")

        engine.generatePublisher(
            items: items,
            voice: voice,
            onProgress: { [weak self] completed, total in
                DispatchQueue.main.async {
                    self?.progress = Float(completed) / Float(total)
                }
            }
        )
        .receive(on: DispatchQueue.main)
        .sink(
            receiveCompletion: { [weak self] completion in
                self?.isLoading = false
                if case .failure(let error) = completion {
                    self?.status = "合成失败: \(error.localizedDescription)"
                }
            },
            receiveValue: { [weak self] results in
                self?.generateResults = results
                let ok = results.filter { $0.isSuccess }.count
                self?.status = "合成完成: \(ok)/\(results.count) 成功"
            }
        )
        .store(in: &cancellables)
    }

    // -------------------------------------------------------
    // 取消 & 清理
    // -------------------------------------------------------
    func cancelAll() {
        cancellables.removeAll()
        engine?.cancel()
        isLoading = false
        status = "已取消"
    }

    func tearDown() {
        cancellables.removeAll()
        engine?.close()
        engine = nil
    }
}

// MARK: - SwiftUI View

struct TtsDemoView: View {
    @StateObject private var vm = TtsDemoViewModel()

    var body: some View {
        NavigationView {
            Form {
                // 输入
                Section("文本输入") {
                    TextEditor(text: $vm.inputText)
                        .frame(minHeight: 80)
                }

                // 状态
                Section("状态") {
                    Text(vm.status)
                        .foregroundColor(.secondary)

                    if vm.isLoading {
                        ProgressView(value: vm.progress)
                    }
                }

                // 操作
                Section("操作") {
                    Button("流式预览") { vm.startPreview() }
                        .disabled(vm.isLoading)

                    Button("批量合成") { vm.startGenerate() }
                        .disabled(vm.isLoading)

                    if vm.isLoading {
                        Button("取消", role: .destructive) { vm.cancelAll() }
                    }
                }

                // 合成结果
                if !vm.generateResults.isEmpty {
                    Section("合成结果") {
                        ForEach(Array(vm.generateResults.enumerated()), id: \.offset) { _, result in
                            HStack {
                                Image(systemName: result.isSuccess ? "checkmark.circle.fill" : "xmark.circle.fill")
                                    .foregroundColor(result.isSuccess ? .green : .red)
                                VStack(alignment: .leading) {
                                    Text(result.text)
                                        .lineLimit(1)
                                    if let path = result.filePath {
                                        Text(path)
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("TTS Demo")
        }
        .onAppear { vm.setup() }
        .onDisappear { vm.tearDown() }
    }
}
