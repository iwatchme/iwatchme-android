// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AiSdk",
    platforms: [
        .iOS(.v14),
    ],
    products: [
        .library(name: "AiCore", targets: ["AiCore"]),
        .library(name: "AiTts", targets: ["AiTts"]),
        .library(name: "AiTranslation", targets: ["AiTranslation"]),
        .library(name: "AiAsr", targets: ["AiAsr"]),
    ],
    targets: [
        .binaryTarget(
            name: "AiCore",
            path: "ai-core/build/XCFrameworks/release/AiCore.xcframework"
        ),
        .binaryTarget(
            name: "AiTts",
            path: "ai-tts/build/XCFrameworks/release/AiTts.xcframework"
        ),
        .binaryTarget(
            name: "AiTranslation",
            path: "ai-translation/build/XCFrameworks/release/AiTranslation.xcframework"
        ),
        .binaryTarget(
            name: "AiAsr",
            path: "ai-asr/build/XCFrameworks/release/AiAsr.xcframework"
        ),
    ]
)
