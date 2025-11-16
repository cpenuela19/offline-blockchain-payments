package com.g22.offline_blockchain_payments.ble.model

import org.json.JSONObject

data class PairingPayload(
    val serviceUuid: String,
    val sessionId: String
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("serviceUuid", serviceUuid)
            put("sessionId", sessionId)
        }.toString()
    }

    companion object {
        fun fromJson(json: String): PairingPayload? {
            return try {
                val obj = JSONObject(json)
                PairingPayload(
                    serviceUuid = obj.getString("serviceUuid"),
                    sessionId = obj.getString("sessionId")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

