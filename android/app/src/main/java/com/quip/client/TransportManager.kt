package com.quip.client

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.util.UUID

class TransportManager(private val nativeClient: NativeQuipClient) {

    private val TAG = "QUIP_TransportManager"
    @Volatile private var isConnected = false
    private var bluetoothSocket: BluetoothSocket? = null

    fun connectWifi(hostIp: String, port: Int = 9876): Boolean {
        val ok = nativeClient.initClient(hostIp, port)
        if (ok) {
            isConnected = true
            Log.i(TAG, "Connected Wi-Fi transport to $hostIp:$port")
        }
        return ok
    }

    fun connectBluetoothL2cap(device: BluetoothDevice, psm: Int = 0x1001): Boolean {
        return try {
            // Android 10+ (API 29+) Bluetooth L2CAP CoC socket setup
            val socket = device.createInsecureL2capChannel(psm)
            socket.connect()
            bluetoothSocket = socket
            Log.i(TAG, "Established Bluetooth L2CAP channel to ${device.address}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth L2CAP connection failed: ${e.message}")
            false
        }
    }

    fun sendInputFrame(
        engine: InputEngine,
        timestampUs: Long = System.nanoTime() / 1000
    ): Boolean {
        if (!isConnected) return false

        val mouseDx = engine.consumeTouchX()
        val mouseDy = engine.consumeTouchY()

        return nativeClient.sendInputPacket(
            digitalMask = engine.currentDigitalMask,
            mouseDx = mouseDx,
            mouseDy = mouseDy,
            leftStickX = engine.leftStickX,
            leftStickY = engine.leftStickY,
            gyroYaw = engine.getGyroYaw(),
            gyroPitch = engine.getGyroPitch(),
            timestampUs = timestampUs
        )
    }

    fun disconnect() {
        isConnected = false
        nativeClient.closeClient()
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothSocket = null
    }
}
