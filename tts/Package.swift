// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "TtsSdk",
    platforms: [
        .iOS(.v14),
    ],
    products: [
        .library(name: "TtsCore", targets: ["TtsCore"]),
        .library(name: "TtsCloudflare", targets: ["TtsCloudflare"]),
    ],
    targets: [
        .binaryTarget(
            name: "TtsCore",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/tts-v0.0.2/TtsCore.xcframework.zip",
            checksum: "f4f007cc2a5ccb737bc575b533df5bff9b8511b78cb5df8901eaf80f237dcdc1"
        ),
        .binaryTarget(
            name: "TtsCloudflare",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/tts-v0.0.2/TtsCloudflare.xcframework.zip",
            checksum: "7855dfa8c6d75bd43daf68c9b48becf9c995b7b63f9f85c050636f6e381d78cb"
        ),
    ]
)
