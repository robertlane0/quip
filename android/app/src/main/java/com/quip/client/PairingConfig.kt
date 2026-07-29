package com.quip.client

/**
 * Parsed QR Code Pairing Data Model
 * QR format: quip://<IP>:<PORT>?mac=<BT_MAC>&pk=<STATIC_PUBLIC_KEY>
 */
data class PairingConfig(
    val hostIp: String,
    val port: Int = 9876,
    val bluetoothMac: String? = null,
    val hostPublicKeyHex: String? = null
) {
    companion object {
        fun parseQrPayload(payload: String): PairingConfig? {
            return try {
                if (!payload.startsWith("quip://")) return null
                val clean = payload.removePrefix("quip://")
                val parts = clean.split("?")
                val hostPort = parts[0].split(":")
                val ip = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toInt() else 9876

                var mac: String? = null
                var pk: String? = null

                if (parts.size > 1) {
                    val queryParams = parts[1].split("&")
                    for (param in queryParams) {
                        val kv = param.split("=")
                        if (kv.size == 2) {
                            when (kv[0]) {
                                "mac" -> mac = kv[1]
                                "pk" -> pk = kv[1]
                            }
                        }
                    }
                }

                PairingConfig(
                    hostIp = ip,
                    port = port,
                    bluetoothMac = mac,
                    hostPublicKeyHex = pk
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
