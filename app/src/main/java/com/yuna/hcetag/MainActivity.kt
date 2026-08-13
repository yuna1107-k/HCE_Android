package com.yuna.hcetag

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/**
 * NFCタッチ時に相手側(Android/iOS共通)で開かせるURLの設定画面。
 *
 * この画面が前面にある間、端末は「タグ専用モード」になる:
 * ポーリング(タグ読み取り)を無効化し、リッスン(HCE=タグとして振る舞う)のみ残す。
 * これによりAndroid同士をかざしたときに自端末が相手を読んでしまい、
 * タグ読み取りアプリの選択ダイアログが出る問題を防ぐ。
 * タグを読みたいときだけ「読み取りモード」スイッチをONにする。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: TagSettings
    private lateinit var urlInput: EditText
    private lateinit var nfcStatus: TextView
    private lateinit var readingSwitch: SwitchCompat
    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = TagSettings(this)
        urlInput = findViewById(R.id.input_url)
        nfcStatus = findViewById(R.id.text_nfc_status)
        readingSwitch = findViewById(R.id.switch_tag_reading)

        urlInput.setText(settings.targetUrl.orEmpty())
        findViewById<Button>(R.id.button_save).setOnClickListener { save() }
        readingSwitch.setOnCheckedChangeListener { _, _ -> applyTagMode() }
    }

    override fun onResume() {
        super.onResume()
        updateNfcStatus()
        applyTagMode()
    }

    override fun onPause() {
        super.onPause()
        val adapter = nfcAdapter?.takeIf { it.isEnabled } ?: return
        // 画面を離れたら通常のNFC動作に戻す
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { adapter.resetDiscoveryTechnology(this) }
        }
        runCatching { adapter.disableForegroundDispatch(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 前面ディスパッチで受けたタグイベントは無視する(この端末はタグ側として振る舞う)
        Log.d(TAG, "Absorbed tag event: ${intent.action}")
    }

    /** 読み取りモードスイッチの状態に応じて、タグ専用モード/通常動作を切り替える */
    private fun applyTagMode() {
        val adapter = nfcAdapter?.takeIf { it.isEnabled } ?: return
        if (readingSwitch.isChecked) {
            // 読み取りモード: 通常のAndroidのNFC動作(タグを読める)
            if (Build.VERSION.SDK_INT >= 35) {
                runCatching { adapter.resetDiscoveryTechnology(this) }
                    .onFailure { Log.w(TAG, "resetDiscoveryTechnology failed", it) }
            }
            runCatching { adapter.disableForegroundDispatch(this) }
            Log.d(TAG, "Reading mode ON (normal NFC behavior)")
        } else {
            // タグ専用モード: ポーリング停止 + リッスンをISO-DEP(A/B)のみに限定(API 35+)。
            // FeliCa(eSE)のリッスンを隠さないと、リーダー側から複合デバイスに見えて
            // プロトコル切替でISO-DEPの読み取りが中断され「空のタグ」になる。
            if (Build.VERSION.SDK_INT >= 35) {
                runCatching {
                    adapter.setDiscoveryTechnology(
                        this,
                        NfcAdapter.FLAG_READER_DISABLE,
                        NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_A or NfcAdapter.FLAG_LISTEN_NFC_PASSIVE_B
                    )
                    Log.d(TAG, "Polling disabled, listen limited to NFC-A/B")
                }.onFailure { Log.w(TAG, "setDiscoveryTechnology failed", it) }
            }
            // フォールバック(API 35未満や上記が効かない端末): 読んでしまったタグを吸収する
            enableTagDispatchSuppression(adapter)
        }
    }

    private fun enableTagDispatchSuppression(adapter: NfcAdapter) {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        Log.d(TAG, "Foreground dispatch enabled")
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

    companion object {
        private const val TAG = "HceTagMain"
    }
}
