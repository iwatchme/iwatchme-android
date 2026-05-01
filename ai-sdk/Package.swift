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
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/ai-sdk-v0.1.0/AiCore.xcframework.zip",
            checksum: "269f9ac6ab34fac2762d3cf54b7a0b941f90604b339f8d7da22c8b5f3a76c55c"
        ),
        .binaryTarget(
            name: "AiTts",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/ai-sdk-v0.1.0/AiTts.xcframework.zip",
            checksum: "3662893bf0a569c5ce8a24179a6b12be709a40d5237dfb118a5b93b526a8b367"
        ),
        .binaryTarget(
            name: "AiTranslation",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/ai-sdk-v0.1.0/AiTranslation.xcframework.zip",
            checksum: "cb711245838607dad1e2be9137b75cff5ad69ab848c387a6315f0200954cab3f"
        ),
        .binaryTarget(
            name: "AiAsr",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/ai-sdk-v0.1.0/AiAsr.xcframework.zip",
            checksum: "cb9a8cfbc9fd151f2e41a3c0879f9eb76693e5f53021be3b9023ef9459babf34"
        ),
    ]
)
