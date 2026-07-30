package com.quip.client

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val client = NativeQuipClient()
    private var isConnected = false

    private lateinit var etHostIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHostIp = findViewById(R.id.ipAddressEditText)
        btnConnect = findViewById(R.id.connectButton)
        tvStatus = findViewById(R.id.statusText)

        val keyLayoutContainer = findViewById<FrameLayout>(R.id.keyLayoutContainer)
        val wasdLayout = LayoutInflater.from(this).inflate(R.layout.keylayout_wasd, keyLayoutContainer, false)
        keyLayoutContainer.addView(wasdLayout)

        val trackpad = findViewById<TrackpadView>(R.id.trackpad)

        btnConnect.setOnClickListener {
            val ip = etHostIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter a host IP address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ok = client.initClient(ip, 9876)
            if (ok) {
                isConnected = true
                tvStatus.text = "Status: Connected to $ip:9876"
                tvStatus.setTextColor(Color.GREEN)
                Toast.makeText(this, "Connected to $ip:9876", Toast.LENGTH_SHORT).show()
            } else {
                isConnected = false
                tvStatus.text = "Status: Connection Failed"
                tvStatus.setTextColor(Color.RED)
                Toast.makeText(this, "Failed to initialize client for $ip", Toast.LENGTH_SHORT).show()
            }
        }

        setupKeyButton(wasdLayout.findViewById(R.id.keyW), QuipProtocol.BIT_W)
        setupKeyButton(wasdLayout.findViewById(R.id.keyA), QuipProtocol.BIT_A)
        setupKeyButton(wasdLayout.findViewById(R.id.keyS), QuipProtocol.BIT_S)
        setupKeyButton(wasdLayout.findViewById(R.id.keyD), QuipProtocol.BIT_D)

        trackpad.listener = object : TrackpadView.Listener {
            override fun onMouseMove(dx: Int, dy: Int) {
                if (isConnected) {
                    client.sendInputPacket(
                        digitalMask = 0L,
                        mouseDx = dx,
                        mouseDy = dy,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = System.nanoTime() / 1000
                    )
                }
            }

            override fun onLeftClick() {
                if (isConnected) {
                    client.sendInputPacket(
                        digitalMask = QuipProtocol.BIT_MOUSE_L,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = System.nanoTime() / 1000
                    )
                    client.sendInputPacket(
                        digitalMask = 0L,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = System.nanoTime() / 1000
                    )
                }
            }

            override fun onRightClick() {
                if (isConnected) {
                    client.sendInputPacket(
                        digitalMask = QuipProtocol.BIT_MOUSE_R,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = System.nanoTime() / 1000
                    )
                    client.sendInputPacket(
                        digitalMask = 0L,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = System.nanoTime() / 1000
                    )
                }
            }
        }
    }

    private fun setupKeyButton(button: View, bit: Long) {
        val originalBackground = button.background
        val greenBackground = android.graphics.drawable.ColorDrawable(Color.parseColor("#4CAF50"))

        button.setOnTouchListener { _, event ->
            if (!isConnected) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(this, "Please connect to a PC host first", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener false
            }

            val timestampUs = System.nanoTime() / 1000

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    button.setBackground(greenBackground)
                    client.sendInputPacket(
                        digitalMask = bit,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = timestampUs
                    )
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.setBackground(originalBackground)
                    client.sendInputPacket(
                        digitalMask = 0L,
                        mouseDx = 0,
                        mouseDy = 0,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0,
                        timestampUs = timestampUs
                    )
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        client.closeClient()
    }
}