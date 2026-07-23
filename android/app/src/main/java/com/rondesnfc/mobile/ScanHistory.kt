package com.rondesnfc.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historique local des derniers badges (synchronises, en attente ou refuses), affiche sur
 * l'ecran de scan pour que le gardien voie son activite meme sans reseau. Contrairement a
 * [OfflineQueue] (qui ne garde que ce qui reste a renvoyer), cet historique garde une trace
 * de tous les scans recents quel que soit leur statut.
 */
class ScanHistory(context: Context) {
    private val prefs = context.getSharedPreferences("rondes_scan_history", Context.MODE_PRIVATE)

    fun add(id: String, tagUid: String, scannedAtIso: String, status: String, detail: String) {
        val items = readAll()
        val updated = JSONArray()
        updated.put(
            JSONObject()
                .put("id", id)
                .put("tagUid", tagUid)
                .put("scannedAt", scannedAtIso)
                .put("status", status)
                .put("detail", detail)
        )
        for (i in 0 until items.length()) updated.put(items.getJSONObject(i))
        while (updated.length() > MAX_ENTRIES) updated.remove(updated.length() - 1)
        prefs.edit().putString("items", updated.toString()).apply()
    }

    fun updateStatus(id: String, status: String, detail: String) {
        if (id.isBlank()) return
        val items = readAll()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            if (item.optString("id") == id) {
                item.put("status", status).put("detail", detail)
                break
            }
        }
        prefs.edit().putString("items", items.toString()).apply()
    }

    fun readAll(): JSONArray {
        val raw = prefs.getString("items", null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_ERROR = "ERROR"
        private const val MAX_ENTRIES = 20
    }
}
