package com.rondesnfc.mobile

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rondesnfc.mobile.databinding.ActivityScanBinding
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant

fun ByteArray.toColonHex(): String = joinToString(":") { b -> "%02x".format(b) }

class ScanActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityScanBinding
    private lateinit var session: Session
    private lateinit var api: ApiClient
    private lateinit var queue: OfflineQueue
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = Session(this)
        api = ApiClient(session)
        queue = OfflineQueue(this)

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

        refreshQueueLabel()
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

    /** Callback NFC : appele sur un thread binder, jamais sur le thread principal. */
    override fun onTagDiscovered(tag: Tag) {
        val tagUid = tag.id.toColonHex()
        val scannedAt = Instant.now().toString()
        runOnUiThread { handleScan(tagUid, scannedAt) }
    }

    private fun handleScan(tagUid: String, scannedAt: String) {
        binding.statusText.text = "Envoi..."
        lifecycleScope.launch {
            try {
                val result = api.scan(tagUid, scannedAt)
                val suffix = if (result.offlineSync) " (synchronise hors-ligne)" else ""
                binding.statusText.text = "OK : ${result.roomName} controlee$suffix"
            } catch (e: NetworkException) {
                queue.add(tagUid, scannedAt)
                refreshQueueLabel()
                binding.statusText.text = "Hors ligne : scan mis en file (${e.message})"
            } catch (e: ApiException) {
                binding.statusText.text = "Refuse : ${e.message}"
            }
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
                    api.scan(item.getString("tagUid"), item.getString("scannedAt"))
                } catch (e: Exception) {
                    remaining.put(item)
                }
            }
            queue.replaceAll(remaining)
            refreshQueueLabel()
        }
    }

    private fun refreshQueueLabel() {
        val n = queue.size()
        binding.queueLabel.text = if (n > 0) "$n scan(s) en attente de synchronisation" else ""
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
