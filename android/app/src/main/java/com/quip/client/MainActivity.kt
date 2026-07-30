package com.quip.client

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    /**
     * Registry of selectable key layouts. Each is just an XML resource — to ship a new
     * control scheme, add a res/xml/keylayout_*.xml file (see keylayout_wasd.xml for the
     * schema) and add one entry here.
     */
    private val layoutOptions: List<Pair<String, Int>> = listOf(
        "WASD" to R.xml.keylayout_wasd,
        "WASD + Mods" to R.xml.keylayout_wasd_full
    )

    private val nativeClient = NativeQuipClient()
    private val transport = TransportManager(nativeClient)
    private val engine = InputEngine()
    private lateinit var keyboardPanel: KeyboardPanel

    private lateinit var etHostIp: EditText
    private lateinit var spinnerLayout: Spinner
    private lateinit var btnConnect: android.widget.Button
    private lateinit var tvStatus: TextView
    private lateinit var keyboardContainer: FrameLayout
    private lateinit var trackpad: TrackpadView

    private var isConnected = false

    // Drives the periodic transmission of the current key/mouse state at ~60Hz.
    private val sendHandler = Handler(Looper.getMainLooper())
    private val sendIntervalMs = 16L
    private val sendLoop = object : Runnable {
        override fun run() {
            transport.sendInputFrame(engine)
            sendHandler.postDelayed(this, sendIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHostIp = findViewById(R.id.etHostIp)
        spinnerLayout = findViewById(R.id.spinnerLayout)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        keyboardContainer = findViewById(R.id.keyboardContainer)
        trackpad = findViewById(R.id.trackpad)

        keyboardPanel = KeyboardPanel(this, engine)

        setupLayoutSpinner()
        setupConnectButton()
        setupTrackpad()

        renderKeyLayout(layoutOptions.first().second)
    }

    override fun onResume() {
        super.onResume()
        sendHandler.post(sendLoop)
    }

    override fun onPause() {
        super.onPause()
        sendHandler.removeCallbacks(sendLoop)
    }

    override fun onDestroy() {
        super.onDestroy()
        sendHandler.removeCallbacks(sendLoop)
        transport.disconnect()
    }

    // ---- Setup ----------------------------------------------------------------------

    private fun setupLayoutSpinner() {
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            layoutOptions.map { it.first }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setPadding(24, 16, 24, 16)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.quip_bg_elevated))
                view.setPadding(24, 16, 24, 16)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLayout.adapter = adapter

        spinnerLayout.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                renderKeyLayout(layoutOptions[position].second)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        })
    }

    private fun setupConnectButton() {
        btnConnect.setOnClickListener {
            val ip = etHostIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Please enter a host IP address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ok = transport.connectWifi(ip, 9876)
            isConnected = ok
            if (ok) {
                tvStatus.text = "Status: Connected to $ip:9876"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.quip_status_ok))
                Toast.makeText(this, "Connected to $ip:9876", Toast.LENGTH_SHORT).show()
            } else {
                tvStatus.text = "Status: Connection Failed"
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.quip_status_bad))
                Toast.makeText(this, "Failed to initialize client for $ip", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTrackpad() {
        trackpad.onMove = { dx, dy ->
            engine.addTouchDelta(dx, dy)
        }
        trackpad.onLeftClick = {
            if (!isConnected) {
                Toast.makeText(this, "Please connect to a PC host first", Toast.LENGTH_SHORT).show()
            } else {
                pulseMouseButton(QuipProtocol.BIT_MOUSE_L)
            }
        }
        trackpad.onRightClick = {
            if (!isConnected) {
                Toast.makeText(this, "Please connect to a PC host first", Toast.LENGTH_SHORT).show()
            } else {
                pulseMouseButton(QuipProtocol.BIT_MOUSE_R)
            }
        }
    }

    // ---- Layout rendering -------------------------------------------------------------

    private fun renderKeyLayout(xmlResId: Int) {
        // Releasing any held keys from the previous layout before swapping it out.
        engine.currentDigitalMask = 0L

        val layout = KeyLayoutParser.parse(this, xmlResId)
        keyboardContainer.removeAllViews()
        keyboardContainer.addView(keyboardPanel.build(layout))
    }

    // ---- Mouse click pulses ------------------------------------------------------------

    /** Sets [bit] for one short frame window then clears it, i.e. a click "down + up". */
    private fun pulseMouseButton(bit: Long) {
        engine.currentDigitalMask = engine.currentDigitalMask or bit
        sendHandler.postDelayed({
            engine.currentDigitalMask = engine.currentDigitalMask and bit.inv()
        }, 60L)
    }
}
