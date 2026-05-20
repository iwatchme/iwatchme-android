package com.iwatchme.netopt.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * E11 — local-first notes. Persisted via SharedPreferences so a real Room
 * setup isn't needed for the demo. Each note carries a sync status:
 *  - PENDING  — created locally, not yet replicated to server
 *  - SYNCED   — server acknowledged with its own id
 */
data class LocalNote(
    val localId: String,
    val text: String,
    val status: String,            // PENDING | SYNCED
    val serverId: Long? = null,
    val updatedAt: Long,
)

class NoteStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("netopt_notes", Context.MODE_PRIVATE)

    companion object {
        private const val KEY = "notes_json"
    }

    fun all(): List<LocalNote> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { idx ->
            val o = arr.getJSONObject(idx)
            LocalNote(
                localId = o.getString("localId"),
                text = o.getString("text"),
                status = o.getString("status"),
                serverId = if (o.has("serverId") && !o.isNull("serverId")) o.getLong("serverId") else null,
                updatedAt = o.getLong("updatedAt"),
            )
        }
    }

    fun add(text: String): LocalNote {
        val note = LocalNote(
            localId = UUID.randomUUID().toString(),
            text = text,
            status = "PENDING",
            updatedAt = System.currentTimeMillis(),
        )
        write(all() + note)
        return note
    }

    fun markSynced(localId: String, serverId: Long) {
        write(all().map {
            if (it.localId == localId) it.copy(status = "SYNCED", serverId = serverId) else it
        })
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun write(notes: List<LocalNote>) {
        val arr = JSONArray()
        notes.forEach { n ->
            val o = JSONObject()
            o.put("localId", n.localId)
            o.put("text", n.text)
            o.put("status", n.status)
            n.serverId?.let { o.put("serverId", it) }
            o.put("updatedAt", n.updatedAt)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
