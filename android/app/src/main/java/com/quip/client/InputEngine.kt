package com.quip.client

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.atomic.AtomicInteger

class InputEngine : SensorEventListener {

    // Touch Delta Accumulators (Sub-pixel mouse motion)
    private val accumulatedTouchX = AtomicInteger(0)
    private val accumulatedTouchY = AtomicInteger(0)

    // Gyroscope Motion Deltas (Yaw / Pitch)
    @Volatile private var gyroYawDelta: Int = 0
    @Volatile private var gyroPitchDelta: Int = 0

    // Digital Bitmask State
    @Volatile var currentDigitalMask: Long = 0L

    // Left Analog Stick Coordinates (-128 to +127)
    @Volatile var leftStickX: Int = 0
    @Volatile var leftStickY: Int = 0

    fun addTouchDelta(dx: Float, dy: Float, sensitivity: Float = 1.0f) {
        accumulatedTouchX.addAndGet((dx * sensitivity).toInt())
        accumulatedTouchY.addAndGet((dy * sensitivity).toInt())
    }

    fun consumeTouchX(): Int = accumulatedTouchX.getAndSet(0)
    fun consumeTouchY(): Int = accumulatedTouchY.getAndSet(0)

    fun getGyroYaw(): Int = gyroYawDelta
    fun getGyroPitch(): Int = gyroPitchDelta

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        
        // Convert angular velocity (rad/s) to sub-pixel gyro deltas
        val yawRate = event.values[1] // Y-axis rotation (Yaw)
        val pitchRate = event.values[0] // X-axis rotation (Pitch)

        gyroYawDelta = (yawRate * 10f).toInt().coerceIn(-128, 127)
        gyroPitchDelta = (pitchRate * 10f).toInt().coerceIn(-128, 127)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
