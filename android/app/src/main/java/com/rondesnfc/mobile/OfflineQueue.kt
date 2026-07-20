package com.rondesnfc.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * File d'attente locale des scans non envoyes (pas de reseau au moment du tap, cas du
 * sous-sol du musee). L'horodatage est capture au moment du scan, pas au moment de la
 * synchronisation - c'est ce qui fait foi cote serveur (fenetre bornee, cf. ScanService).
 */
class OfflineQueue(context: Context) {
    private val prefs = context.getSharedPreferences("rondes_offline_queue", Context.MODE_PRIVATE)

    fun add(tagUid: String, scannedAtIso: String) {
        val items = readAll()
        items.put(JSONObject().put("tagUid", tagUid).put("scannedAt", scannedAtIso))
        prefs.edit().putString("items", items.toString()).apply()
    }

    fun size(): Int = readAll().length()

    fun readAll(): JSONArray {
        val raw = prefs.getString("items", null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }

    fun replaceAll(items: JSONArray) {
        prefs.edit().putString("items", items.toString()).apply()
    }
}
