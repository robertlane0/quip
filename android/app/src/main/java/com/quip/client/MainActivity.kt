package com.quip.client

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val client = NativeQuipClient()
    private var isConnected = false

    private lateinit var etHostIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnW: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHostIp = findViewById(R.id.etHostIp)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        btnW = findViewById(R.id.btnW)

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

        // Giant W Touch Listener
        btnW.setOnTouchListener { _, event ->
            if (!isConnected) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    Toast.makeText(this, "Please connect to a PC host first", Toast.LENGTH_SHORT).show()
                }
                return@setOnTouchListener false
            }

            val timestampUs = System.nanoTime() / 1000

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btnW.setBackgroundColor(Color.parseColor("#4CAF50")) // Bright Green on press
                    client.sendInputPacket(
                        digitalMask = QuipProtocol.BIT_W,
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
                    btnW.setBackgroundColor(Color.parseColor("#222222")) // Dark Idle
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
