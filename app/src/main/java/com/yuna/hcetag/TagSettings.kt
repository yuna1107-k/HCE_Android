package com.yuna.hcetag

import android.content.Context

/**
 * NFCタッチ時に相手側で開かせる対象の設定。
 * HCEサービスと設定UIの両方から参照される。
 */
class TagSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Android相手に開かせるアプリのパッケージ名(AARとして格納) */
    var targetPackage: String?
        get() = prefs.getString(KEY_PACKAGE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_PACKAGE, value?.trim()).apply()
        }

    /** iPhone相手に開かせるhttps URL(URIレコードとして格納) */
    var targetUrl: String?
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_URL, value?.trim()).apply()
        }

    companion object {
        private const val PREFS_NAME = "tag_settings"
        private const val KEY_PACKAGE = "target_package"
        private const val KEY_URL = "target_url"
    }
}
