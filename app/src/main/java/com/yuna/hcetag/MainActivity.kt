package com.yuna.hcetag

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * NFCタッチ時に相手側(Android/iOS共通)で開かせるURLの設定画面。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: TagSettings
    private lateinit var urlInput: EditText
    private lateinit var nfcStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = TagSettings(this)
        urlInput = findViewById(R.id.input_url)
        nfcStatus = findViewById(R.id.text_nfc_status)

        urlInput.setText(settings.targetUrl.orEmpty())
        findViewById<Button>(R.id.button_save).setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        updateNfcStatus()
    }

    private fun save() {
        val url = urlInput.text.toString().trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            urlInput.error = getString(R.string.error_url_scheme)
            return
        }
        settings.targetUrl = url
        Toast.makeText(this, R.string.toast_saved, Toast.LENGTH_SHORT).show()
    }

    private fun updateNfcStatus() {
        val adapter = NfcAdapter.getDefaultAdapter(this)
        when {
            adapter == null -> {
                nfcStatus.text = getString(R.string.nfc_status_unsupported)
                nfcStatus.setOnClickListener(null)
            }
            !adapter.isEnabled -> {
                nfcStatus.text = getString(R.string.nfc_status_disabled)
                nfcStatus.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                }
            }
            else -> {
                nfcStatus.text = getString(R.string.nfc_status_ready)
                nfcStatus.setOnClickListener(null)
            }
        }
    }
}
