package com.iwatchme.netopt.data

import com.iwatchme.netopt.net.EncodingType
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import org.brotli.dec.BrotliInputStream
import org.json.JSONArray

data class FeedItemView(
    val id: Long,
    val version: Long,
    val title: String,
    val content: String,
    val updatedAt: Long,
)

object FeedDecoder {
    fun decode(type: EncodingType, raw: ByteArray): List<FeedItemView> = when (type) {
        EncodingType.JSON -> decodeJson(raw)
        EncodingType.GZIP -> decodeJson(GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() })
        EncodingType.BROTLI -> decodeJson(BrotliInputStream(ByteArrayInputStream(raw)).use { it.readBytes() })
        EncodingType.PROTOBUF -> ProtoFeedReader.decodeList(raw)
    }

    private fun decodeJson(bytes: ByteArray): List<FeedItemView> {
        val arr = JSONArray(String(bytes, Charsets.UTF_8))
        val list = ArrayList<FeedItemView>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                FeedItemView(
                    id = o.optLong("id"),
                    version = o.optLong("version"),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    updatedAt = o.optLong("updatedAt"),
                )
            )
        }
        return list
    }
}

/**
 * Minimal proto3 reader tailored to feed.proto:
 *
 *   message FeedItemList { repeated FeedItem items = 1; }
 *   message FeedItem {
 *     int64  id         = 1;
 *     int64  version    = 2;
 *     string title      = 3;
 *     string content    = 4;
 *     int64  updated_at = 5;
 *   }
 *
 * We only handle wire types 0 (varint) and 2 (length-delimited), which is all
 * feed.proto uses. Replace with a generated codec the moment Wire's Gradle
 * plugin catches up to Gradle 8.13.
 */
private object ProtoFeedReader {
    private const val WIRE_VARINT = 0
    private const val WIRE_LEN = 2

    fun decodeList(bytes: ByteArray): List<FeedItemView> {
        val r = Reader(bytes)
        val items = ArrayList<FeedItemView>()
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val type = tag and 0x7
            if (field == 1 && type == WIRE_LEN) {
                val len = r.readVarint().toInt()
                items.add(decodeItem(r.readSubArray(len)))
            } else {
                r.skipField(type)
            }
        }
        return items
    }

    private fun decodeItem(bytes: ByteArray): FeedItemView {
        val r = Reader(bytes)
        var id = 0L; var version = 0L; var updatedAt = 0L
        var title = ""; var content = ""
        while (!r.eof()) {
            val tag = r.readVarint().toInt()
            val field = tag ushr 3
            val type = tag and 0x7
            when (field) {
                1 -> if (type == WIRE_VARINT) id = r.readVarint()
                2 -> if (type == WIRE_VARINT) version = r.readVarint()
                3 -> if (type == WIRE_LEN) title = String(r.readLenDelimited(), Charsets.UTF_8)
                4 -> if (type == WIRE_LEN) content = String(r.readLenDelimited(), Charsets.UTF_8)
                5 -> if (type == WIRE_VARINT) updatedAt = r.readVarint()
                else -> r.skipField(type)
            }
        }
        return FeedItemView(id, version, title, content, updatedAt)
    }

    private class Reader(private val buf: ByteArray) {
        private var pos = 0
        fun eof(): Boolean = pos >= buf.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift >= 64) error("varint too long")
            }
        }

        fun readLenDelimited(): ByteArray {
            val n = readVarint().toInt()
            return readSubArray(n)
        }

        fun readSubArray(n: Int): ByteArray {
            val out = buf.copyOfRange(pos, pos + n)
            pos += n
            return out
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                WIRE_VARINT -> readVarint()
                WIRE_LEN -> { val n = readVarint().toInt(); pos += n }
                1 -> pos += 8       // fixed64
                5 -> pos += 4       // fixed32
                else -> error("unsupported wire type $wireType")
            }
        }
    }
}
