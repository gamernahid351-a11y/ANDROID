package com.primex.gateway.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.primex.gateway.model.GatewayConfig
import com.primex.gateway.model.UserSession

object PrefsManager {
    private const val PREF_NAME = "primex_prefs"
    private const val KEY_SESSION = "session"
    private const val KEY_CONFIG = "config"
    private const val KEY_SEEN_TRX = "seen_trx_ids"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveSession(session: UserSession?) {
        prefs.edit().putString(KEY_SESSION, if (session != null) gson.toJson(session) else null).apply()
    }

    fun getSession(): UserSession? {
        val json = prefs.getString(KEY_SESSION, null) ?: return null
        return try { gson.fromJson(json, UserSession::class.java) } catch (e: Exception) { null }
    }

    fun saveConfig(config: GatewayConfig) {
        prefs.edit().putString(KEY_CONFIG, gson.toJson(config)).apply()
    }

    fun getConfig(): GatewayConfig {
        val json = prefs.getString(KEY_CONFIG, null) ?: return GatewayConfig()
        return try { gson.fromJson(json, GatewayConfig::class.java) } catch (e: Exception) { GatewayConfig() }
    }

    fun markTrxSeen(trxId: String) {
        val set = getSeenTrxIds().toMutableSet()
        set.add(trxId)
        prefs.edit().putStringSet(KEY_SEEN_TRX, set).apply()
    }

    fun isTrxSeen(trxId: String): Boolean = getSeenTrxIds().contains(trxId)

    private fun getSeenTrxIds(): Set<String> = prefs.getStringSet(KEY_SEEN_TRX, emptySet()) ?: emptySet()

    fun clearSeenTrxIds() {
        prefs.edit().remove(KEY_SEEN_TRX).apply()
    }
}
