package com.g22.offline_blockchain_payments.data.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Gestor de sesión del usuario.
 * Guarda address, public_key y session_token en memoria (SharedPreferences).
 */
object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREFS_NAME = "session_prefs"
    private const val KEY_ADDRESS = "address"
    private const val KEY_PUBLIC_KEY = "public_key"
    private const val KEY_SESSION_TOKEN = "session_token"

    /**
     * Guarda los datos de sesión del usuario.
     */
    fun saveSession(
        context: Context,
        address: String,
        publicKey: String,
        sessionToken: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_PUBLIC_KEY, publicKey)
            .putString(KEY_SESSION_TOKEN, sessionToken)
            .apply()
        Log.d(TAG, "✅ Sesión guardada: address=$address")
    }

    /**
     * Obtiene la dirección del usuario.
     */
    fun getAddress(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ADDRESS, null)
    }

    /**
     * Obtiene la clave pública del usuario.
     */
    fun getPublicKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PUBLIC_KEY, null)
    }

    /**
     * Obtiene el token de sesión.
     */
    fun getSessionToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SESSION_TOKEN, null)
    }

    /**
     * Verifica si hay una sesión activa.
     */
    fun hasSession(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_ADDRESS) &&
                prefs.contains(KEY_PUBLIC_KEY) &&
                prefs.contains(KEY_SESSION_TOKEN)
    }

    /**
     * Limpia la sesión (útil para logout).
     */
    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "✅ Sesión limpiada")
    }
}

