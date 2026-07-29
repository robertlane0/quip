package com.quip.client

class NativeQuipClient {

    companion object {
        init {
            System.loadLibrary("quip_jni")
        }
    }

    external fun initClient(hostIp: String, port: Int): Boolean

    external fun sendInputPacket(
        digitalMask: Long,
        mouseDx: Int,
        mouseDy: Int,
        leftStickX: Int,
        leftStickY: Int,
        gyroYaw: Int,
        gyroPitch: Int,
        timestampUs: Long
    ): Boolean

    external fun sendHeartbeat(timestampUs: Long): Boolean

    external fun closeClient()
}
