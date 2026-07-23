package com.rondesnfc.mobile

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rondesnfc.mobile.databinding.ActivityScanBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun ByteArray.toColonHex(): String = joinToString(":") { b -> "%02x".format(b) }

class ScanActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityScanBinding
    private lateinit var session: Session
    private lateinit var api: ApiClient
    private lateinit var queue: OfflineQueue
    private lateinit var history: ScanHistory
    private var nfcAdapter: NfcAdapter? = null
    private var connectivityManager: ConnectivityManager? = null

    /** Sync automatique des qu'une connexion revient, sans attendre un nouveau scan ou un redemarrage. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            flushQueue()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = Session(this)
        api = ApiClient(session)
        queue = OfflineQueue(this)
        history = ScanHistory(this)

        if (!session.isLoggedIn) {
            goToLogin()
            return
        }

        binding.guardLabel.text = "${session.guardName} (${session.role})"
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    api.logout()
                } catch (e: Exception) {
                    // On ignore l'erreur reseau a la deconnexion pour permettre
                    // de changer de compte meme hors-ligne
                }
                session.clear()
                goToLogin()
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            binding.statusText.text = "Cet appareil ne dispose pas du NFC"
        }

        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)

        refreshQueueLabel()
        renderHistory()
        flushQueue()
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V
        adapter.enableReaderMode(this, this, flags, null)
        binding.statusText.text = "Lecteur actif, approchez un patch..."
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager?.unregisterNetworkCallback(networkCallback)
    }

    /** Callback NFC : appele sur un thread binder, jamais sur le thread principal. */
    override fun onTagDiscovered(tag: Tag) {
        val tagUid = tag.id.toColonHex()
        val scannedAt = Instant.now().toString()
        runOnUiThread { handleScan(tagUid, scannedAt) }
    }

    private fun handleScan(tagUid: String, scannedAt: String) {
        binding.statusText.text = "Envoi..."
        val id = UUID.randomUUID().toString()
        history.add(id, tagUid, scannedAt, ScanHistory.STATUS_PENDING, "Envoi en cours...")
        renderHistory()

        lifecycleScope.launch {
            try {
                val result = api.scan(tagUid, scannedAt)
                val suffix = if (result.offlineSync) " (synchronise hors-ligne)" else ""
                binding.statusText.text = "OK : ${result.roomName} controlee$suffix"
                history.updateStatus(id, ScanHistory.STATUS_SYNCED, result.roomName)
            } catch (e: NetworkException) {
                queue.add(id, tagUid, scannedAt)
                refreshQueueLabel()
                binding.statusText.text = "Hors ligne : scan mis en file (${e.message})"
                history.updateStatus(id, ScanHistory.STATUS_PENDING, "Hors ligne, en attente de synchronisation")
            } catch (e: ApiException) {
                binding.statusText.text = "Refuse : ${e.message}"
                history.updateStatus(id, ScanHistory.STATUS_ERROR, "Refuse : ${e.message}")
            }
            renderHistory()
        }
    }

    private fun flushQueue() {
        val items = queue.readAll()
        if (items.length() == 0) return
        lifecycleScope.launch {
            val remaining = JSONArray()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                try {
                    val result = api.scan(item.getString("tagUid"), item.getString("scannedAt"))
                    history.updateStatus(item.optString("id"), ScanHistory.STATUS_SYNCED, result.roomName)
                } catch (e: Exception) {
                    remaining.put(item)
                }
            }
            queue.replaceAll(remaining)
            refreshQueueLabel()
            renderHistory()
        }
    }

    private fun refreshQueueLabel() {
        val n = queue.size()
        binding.queueLabel.text = if (n > 0) "$n scan(s) en attente de synchronisation" else ""
    }

    private fun renderHistory() {
        val container = binding.historyContainer
        container.removeAllViews()
        val items = history.readAll()
        if (items.length() == 0) {
            container.addView(TextView(this).apply {
                text = "Aucun badge pour le moment."
                setTextColor(ContextCompat.getColor(this@ScanActivity, R.color.text_muted))
            })
            return
        }
        for (i in 0 until items.length()) {
            container.addView(buildHistoryRow(items.getJSONObject(i)))
        }
    }

    private fun buildHistoryRow(item: JSONObject): LinearLayout {
        val status = item.optString("status", ScanHistory.STATUS_PENDING)
        val colorRes = when (status) {
            ScanHistory.STATUS_SYNCED -> R.color.vert
            ScanHistory.STATUS_ERROR -> R.color.rouge
            else -> R.color.orange
        }
        val color = ContextCompat.getColor(this, colorRes)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(ContextCompat.getColor(this@ScanActivity, R.color.history_row_bg))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10 }
        }

        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(TextView(this).apply {
            text = item.optString("tagUid")
            setTextColor(ContextCompat.getColor(this@ScanActivity, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(TextView(this).apply {
            text = item.optString("detail").ifBlank { statusLabel(status) }
            setTextColor(color)
            gravity = Gravity.END
        })

        row.addView(topRow)
        row.addView(TextView(this).apply {
            text = formatTimestamp(item.optString("scannedAt"))
            setTextColor(ContextCompat.getColor(this@ScanActivity, R.color.text_muted))
            textSize = 12f
        })

        return row
    }

    private fun statusLabel(status: String): String = when (status) {
        ScanHistory.STATUS_SYNCED -> "Synchronise"
        ScanHistory.STATUS_ERROR -> "Refuse"
        else -> "En attente"
    }

    private fun formatTimestamp(iso: String): String = try {
        DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.parse(iso))
    } catch (e: Exception) {
        iso
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
