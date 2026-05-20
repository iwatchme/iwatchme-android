package com.iwatchme.netopt.net

enum class EncodingType(
    val label: String,
    val accept: String,
    val acceptEncoding: String,
) {
    JSON("JSON", "application/json", "identity"),
    GZIP("Gzip", "application/json", "gzip"),
    BROTLI("Brotli", "application/json", "br"),
    PROTOBUF("Protobuf", "application/x-protobuf", "identity"),
}
