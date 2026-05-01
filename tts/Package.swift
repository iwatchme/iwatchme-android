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
            checksum: "8a106afceec593e59c18a317cde284f64cc286041bdc77edd0e2c5ecf2261dab"
        ),
        .binaryTarget(
            name: "TtsCloudflare",
            url: "https://github.com/iwatchme/iwatchme-android/releases/download/tts-v0.0.1/TtsCloudflare.xcframework.zip",
            checksum: "79e846d769965e5f3cef54105aef845cef9898a4d34922201d85228cf6cdb20d"
        ),
    ]
)
