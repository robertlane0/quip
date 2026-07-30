package com.quip.client

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val client = NativeQuipClient()
    private var isConnected = false

    private lateinit var etHostIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var gearIcon: ImageView
    private lateinit var backButton: Button

    private val prefs by lazy { getSharedPreferences("quip_prefs", Context.MODE_PRIVATE) }
    private val KEY_LAST_IP = "last_connected_ip"

    // Combined digital mask of all currently pressed keys/buttons
    private var currentDigitalMask: Long = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFlipper = findViewById(R.id.viewFlipper)
        gearIcon = findViewById(R.id.gearIcon)
        backButton = findViewById(R.id.backButton)

        // Connection setup views (page 2)
        etHostIp = findViewById(R.id.ipAddressEditText)
        btnConnect = findViewById(R.id.connectButton)
        tvStatus = findViewById(R.id.statusText)

        // Load last connected IP
        etHostIp.setText(prefs.getString(KEY_LAST_IP, ""))

        // Keyboard layout (page 1)
        val keyLayoutContainer = findViewById<FrameLayout>(R.id.keyLayoutContainer)
        val wasdLayout = LayoutInflater.from(this).inflate(R.layout.keylayout_wasd, keyLayoutContainer, false)
        keyLayoutContainer.addView(wasdLayout)

        setupKeyButton(wasdLayout.findViewById(R.id.keyW), QuipProtocol.BIT_W)
        setupKeyButton(wasdLayout.findViewById(R.id.keyA), QuipProtocol.BIT_A)
        setupKeyButton(wasdLayout.findViewById(R.id.keyS), QuipProtocol.BIT_S)
        setupKeyButton(wasdLayout.findViewById(R.id.keyD), QuipProtocol.BIT_D)

        // Trackpad (page 1)
        val trackpad = findViewById<TrackpadView>(R.id.trackpad)
        trackpad.listener = object : TrackpadView.Listener {
            override fun onMouseMove(dx: Int, dy: Int) {
                if (isConnected) {
                    sendPacketWithCurrentMask(
                        digitalMask = currentDigitalMask,
                        mouseDx = dx,
                        mouseDy = dy,
                        leftStickX = 0,
                        leftStickY = 0,
                        gyroYaw = 0,
                        gyroPitch = 0
                    )
                }
            }

            override fun onLeftClick() {
                if (isConnected) {
                    currentDigitalMask = currentDigitalMask or QuipProtocol.BIT_MOUSE_L
                    sendPacketWithCurrentMask()
                    currentDigitalMask = currentDigitalMask and QuipProtocol.BIT_MOUSE_L.inv()
                    sendPacketWithCurrentMask()
                }
            }

            override fun onRightClick() {
                if (isConnected) {
                    currentDigitalMask = currentDigitalMask or QuipProtocol.BIT_MOUSE_R
                    sendPacketWithCurrentMask()
                    currentDigitalMask = currentDigitalMask and QuipProtocol.BIT_MOUSE_R.inv()
                    sendPacketWithCurrentMask()
                }
            }
        }

        // Navigation
        gearIcon.setOnClickListener { viewFlipper.showNext() }
        backButton.setOnClickListener { viewFlipper.showPrevious() }

        // Connect button
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
                prefs.edit().putString(KEY_LAST_IP, ip).apply()
                viewFlipper.showPrevious() // Return to controls
            } else {
                isConnected = false
                tvStatus.text = "Status: Connection Failed"
                tvStatus.setTextColor(Color.RED)
                Toast.makeText(this, "Failed to initialize client for $ip", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendPacketWithCurrentMask(
        digitalMask: Long = currentDigitalMask,
        mouseDx: Int = 0,
        mouseDy: Int = 0,
        leftStickX: Int = 0,
        leftStickY: Int = 0,
        gyroYaw: Int = 0,
        gyroPitch: Int = 0
    ) {
        if (!isConnected) return
        val timestampUs = System.nanoTime() / 1000
        client.sendInputPacket(
            digitalMask = digitalMask,
            mouseDx = mouseDx,
            mouseDy = mouseDy,
            leftStickX = leftStickX,
            leftStickY = leftStickY,
            gyroYaw = gyroYaw,
            gyroPitch = gyroPitch,
            timestampUs = timestampUs
        )
    }

    private fun setupKeyButton(button: View, bit: Long) {
        val originalTint = button.backgroundTintList

        button.setOnTouchListener { _, event ->
            if (!isConnected) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(this, "Please connect to a PC host first", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    button.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    currentDigitalMask = currentDigitalMask or bit
                    sendPacketWithCurrentMask()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.backgroundTintList = originalTint
                    currentDigitalMask = currentDigitalMask and bit.inv()
                    sendPacketWithCurrentMask()
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release all keys on app close
        currentDigitalMask = 0L
        sendPacketWithCurrentMask()
        client.closeClient()
    }
}