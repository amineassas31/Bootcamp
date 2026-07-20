package com.rondesnfc.mobile

import android.content.Context
import android.content.SharedPreferences

/** Stocke le jeton de session et l'URL du serveur choisie au login (pas de valeur en dur : le serveur tourne sur le PC de demo, IP variable). */
class Session(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rondes_session", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "http://192.168.1.100:8080") ?: ""
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var guardName: String?
        get() = prefs.getString("guard_name", null)
        set(value) = prefs.edit().putString("guard_name", value).apply()

    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit().putString("role", value).apply()

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    fun clear() {
        prefs.edit().remove("token").remove("guard_name").remove("role").apply()
    }
}
