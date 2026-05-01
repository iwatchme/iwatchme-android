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
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/tts-v0.0.1/TtsCore.xcframework.zip",
            checksum: "4bc53b76170db55409fdc1413386d94a9a42a6a92286d9a044e91aa323c2437f"
        ),
        .binaryTarget(
            name: "TtsCloudflare",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/tts-v0.0.1/TtsCloudflare.xcframework.zip",
            checksum: "bf8cca17fc967bb24b00c102e5180532699b9fc44aeea9434b675f5bf421fd0c"
        ),
    ]
)
