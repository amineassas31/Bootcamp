package com.rondesnfc.mobile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

/** Reponse serveur en erreur (400/401/403/404/409...) : le serveur a bien recu la requete et l'a refusee. */
class ApiException(val statusCode: Int, message: String) : Exception(message)

/** Pas de reponse du tout (reseau coupe, mauvaise IP, timeout) : candidat a la mise en file hors-ligne. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class LoginResult(val token: String, val guardName: String, val role: String)
data class ScanResult(val roomName: String, val guardName: String, val offlineSync: Boolean)

class ApiClient(private val session: Session) {

    suspend fun login(badge: String, pin: String): LoginResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("badge", badge).put("pin", pin)
        val response = request("POST", "/api/auth/login", body, authenticated = false)
        LoginResult(
            token = response.getString("token"),
            guardName = response.getString("guardName"),
            role = response.getString("role"),
        )
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        request("POST", "/api/auth/logout", JSONObject(), authenticated = true)
    }

    suspend fun scan(tagUid: String, scannedAtIso: String): ScanResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("tagUid", tagUid).put("scannedAt", scannedAtIso)
        val response = request("POST", "/api/scan", body, authenticated = true)
        ScanResult(
            roomName = response.getString("roomName"),
            guardName = response.getString("guardName"),
            offlineSync = response.optBoolean("offlineSync", false),
        )
    }

    private fun request(method: String, path: String, body: JSONObject, authenticated: Boolean): JSONObject {
        val url = URL(session.baseUrl + path)
        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw NetworkException("Impossible de joindre le serveur (${session.baseUrl})", e)
        }

        return try {
            connection.requestMethod = method
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (authenticated) {
                val token = session.token
                    ?: throw ApiException(401, "Non connecte")
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                it.write(body.toString())
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                val message = try { JSONObject(text).optString("message", "Erreur $code") } catch (e: Exception) { "Erreur $code" }
                throw ApiException(code, message)
            }
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: ApiException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw NetworkException("Delai depasse en joignant le serveur", e)
        } catch (e: IOException) {
            throw NetworkException("Erreur reseau : ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
}
