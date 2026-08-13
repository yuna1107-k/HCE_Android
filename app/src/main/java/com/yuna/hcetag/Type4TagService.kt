package com.yuna.hcetag

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import kotlin.math.min

/**
 * NFC Forum Type 4 Tag をHCEで模擬するサービス。
 *
 * リーダー(素のAndroid / iPhoneのバックグラウンドタグ読み取り)は
 * NDEF Tag Application(AID D2760000850101)をSELECTし、
 * CCファイル(E103)→ NDEFファイル(E104)の順に読み取る。
 *
 * NDEFメッセージはURIレコード(https URL)1件のみ。
 * Androidはブラウザ(App Links検証済みならそのアプリ)で、
 * iPhoneは通知経由でSafari / Universal Link対応アプリで開く。
 * レコードを複数にするとAndroid側でアプリ選択が出うるため単一に保つこと。
 */
class Type4TagService : HostApduService() {

    private var selectedFile = FILE_NONE
    private var ndefFile = byteArrayOf(0x00, 0x00)

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) return SW_WRONG_LENGTH

        val ins = commandApdu[1]
        val p1 = commandApdu[2].toInt() and 0xFF
        val p2 = commandApdu[3].toInt() and 0xFF

        return when {
            ins == INS_SELECT && p1 == 0x04 -> selectByName(commandApdu)
            ins == INS_SELECT && p1 == 0x00 -> selectByFileId(commandApdu)
            ins == INS_READ_BINARY -> readBinary(p1, p2, commandApdu)
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    /** SELECT by name: NDEF Tag Application の選択 */
    private fun selectByName(apdu: ByteArray): ByteArray {
        if (apdu.size < 5 + NDEF_APP_AID.size) return SW_FILE_NOT_FOUND
        val lc = apdu[4].toInt() and 0xFF
        val aid = apdu.copyOfRange(5, min(5 + lc, apdu.size))
        return if (aid.contentEquals(NDEF_APP_AID)) {
            // タッチのたびに最新の設定でNDEFを組み立てる
            ndefFile = buildNdefFile()
            selectedFile = FILE_NONE
            Log.d(TAG, "NDEF application selected (ndef file ${ndefFile.size} bytes)")
            SW_OK
        } else {
            SW_FILE_NOT_FOUND
        }
    }

    /** SELECT by file ID: CCファイル(E103)または NDEFファイル(E104)の選択 */
    private fun selectByFileId(apdu: ByteArray): ByteArray {
        if (apdu.size < 7 || (apdu[4].toInt() and 0xFF) != 2) return SW_WRONG_LENGTH
        val fileId = ((apdu[5].toInt() and 0xFF) shl 8) or (apdu[6].toInt() and 0xFF)
        return when (fileId) {
            FILE_CC, FILE_NDEF -> {
                selectedFile = fileId
                Log.d(TAG, "File selected: %04X".format(fileId))
                SW_OK
            }
            else -> {
                selectedFile = FILE_NONE
                SW_FILE_NOT_FOUND
            }
        }
    }

    /** READ BINARY: 選択中ファイルの offset から Le バイトを返す */
    private fun readBinary(p1: Int, p2: Int, apdu: ByteArray): ByteArray {
        if (p1 and 0x80 != 0) return SW_INCORRECT_P1P2
        val file = when (selectedFile) {
            FILE_CC -> CC_FILE
            FILE_NDEF -> ndefFile
            else -> return SW_CONDITIONS_NOT_SATISFIED
        }
        val offset = (p1 shl 8) or p2
        if (offset >= file.size) return SW_INCORRECT_P1P2
        val le = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF).let { if (it == 0) 256 else it } else 256
        val end = min(offset + le, file.size)
        Log.d(TAG, "READ BINARY file=%04X offset=%d le=%d -> %d bytes".format(selectedFile, offset, le, end - offset))
        return file.copyOfRange(offset, end) + SW_OK
    }

    /** 設定からNDEFファイル(NLEN 2バイト + NDEFメッセージ)を組み立てる */
    private fun buildNdefFile(): ByteArray {
        val url = TagSettings(this).targetUrl
        if (url == null) {
            Log.w(TAG, "No target URL configured; serving empty NDEF")
            return byteArrayOf(0x00, 0x00)
        }
        val message = NdefMessage(arrayOf(NdefRecord.createUri(url))).toByteArray()
        if (message.size > MAX_NDEF_SIZE - 2) {
            Log.w(TAG, "NDEF message too large (${message.size} bytes); serving empty NDEF")
            return byteArrayOf(0x00, 0x00)
        }
        return byteArrayOf((message.size shr 8).toByte(), message.size.toByte()) + message
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = FILE_NONE
        Log.d(TAG, "Deactivated: $reason")
    }

    companion object {
        private const val TAG = "Type4TagService"

        private const val INS_SELECT = 0xA4.toByte()
        private const val INS_READ_BINARY = 0xB0.toByte()

        private const val FILE_NONE = 0
        private const val FILE_CC = 0xE103
        private const val FILE_NDEF = 0xE104

        private const val MAX_NDEF_SIZE = 1024

        /** NDEF Tag Application AID(apduservice.xmlのaid-filterと一致させること) */
        private val NDEF_APP_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )

        /** Capability Container(読み取り自由・書き込み禁止のNDEFファイルを宣言) */
        private val CC_FILE = byteArrayOf(
            0x00, 0x0F,                    // CCLEN = 15
            0x20,                          // mapping version 2.0
            0x00, 0xF6.toByte(),           // MLe(R-APDU最大データ長)
            0x00, 0xF6.toByte(),           // MLc(C-APDU最大データ長)
            0x04, 0x06,                    // NDEF File Control TLV
            0xE1.toByte(), 0x04,           //   file ID = E104
            (MAX_NDEF_SIZE shr 8).toByte(), (MAX_NDEF_SIZE and 0xFF).toByte(),
            0x00,                          //   読み取り: 制限なし
            0xFF.toByte()                  //   書き込み: 禁止
        )

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_WRONG_LENGTH = byteArrayOf(0x67, 0x00)
        private val SW_CONDITIONS_NOT_SATISFIED = byteArrayOf(0x69, 0x85.toByte())
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
        private val SW_INCORRECT_P1P2 = byteArrayOf(0x6A, 0x86.toByte())
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    }
}
