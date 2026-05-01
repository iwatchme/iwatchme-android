// swift-tools-version:5.9
import PackageDescription

// Local development Package.swift
// On release, CI auto-generates this file with remote binaryTarget URLs.
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
            path: "tts-core/build/XCFrameworks/release/TtsCore.xcframework"
        ),
        .binaryTarget(
            name: "TtsCloudflare",
            path: "tts-cloudflare/build/XCFrameworks/release/TtsCloudflare.xcframework"
        ),
    ]
)
