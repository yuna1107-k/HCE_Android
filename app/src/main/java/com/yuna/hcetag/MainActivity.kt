package com.yuna.hcetag

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * NFCタッチ時に相手側で開かせる対象(Androidパッケージ / iOS用URL)の設定画面。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: TagSettings
    private lateinit var urlInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var nfcStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = TagSettings(this)
        urlInput = findViewById(R.id.input_url)
        packageInput = findViewById(R.id.input_package)
        nfcStatus = findViewById(R.id.text_nfc_status)

        packageInput.setText(settings.targetPackage.orEmpty())
        urlInput.setText(settings.targetUrl.orEmpty())

        findViewById<Button>(R.id.button_save).setOnClickListener { save() }
        setupAppList()
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
        settings.targetPackage = packageInput.text.toString().trim()
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

    /** この端末のランチャーアプリ一覧を表示し、タップでパッケージ名を入力欄へ反映する */
    private fun setupAppList() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(launcherIntent, 0)
            .map { AppEntry(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        val listView = findViewById<ListView>(R.id.list_apps)
        listView.adapter = object : ArrayAdapter<AppEntry>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, apps
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val entry = apps[position]
                view.findViewById<TextView>(android.R.id.text1).text = entry.label
                view.findViewById<TextView>(android.R.id.text2).text = entry.packageName
                return view
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            packageInput.setText(apps[position].packageName)
        }
    }

    private data class AppEntry(val label: String, val packageName: String)
}
