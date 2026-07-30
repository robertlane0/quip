package com.quip.client

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var keyboardPanel: KeyboardPanel
    private lateinit var keyboardContainer: FrameLayout
    private lateinit var trackpad: TrackpadView
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView

    // Tracks which layout is currently built into keyboardContainer so we only rebuild
    // it when the selection actually changed (e.g. after returning from Settings).
    private var renderedLayoutIndex: Int = -1

    // Drives the periodic transmission of the current key/mouse state at ~60Hz.
    private val sendHandler = Handler(Looper.getMainLooper())
    private val sendIntervalMs = 16L
    private val sendLoop = object : Runnable {
        override fun run() {
            AppState.transport.sendInputFrame(AppState.engine)
            sendHandler.postDelayed(this, sendIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        keyboardContainer = findViewById(R.id.keyboardContainer)
        trackpad = findViewById(R.id.trackpad)
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        val btnSettings: View = findViewById(R.id.btnSettings)

        keyboardPanel = KeyboardPanel(this, AppState.engine)

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupTrackpad()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        refreshLayoutIfNeeded()
        refreshStatus()
        sendHandler.post(sendLoop)
    }

    override fun onPause() {
        super.onPause()
        sendHandler.removeCallbacks(sendLoop)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Hides the status/nav bars so the keyboard and trackpad get the full screen. */
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    // ---- Setup ----------------------------------------------------------------------

    private fun setupTrackpad() {
        trackpad.onMove = { dx, dy ->
            AppState.engine.addTouchDelta(dx, dy)
        }
        trackpad.onLeftClick = {
            if (!AppState.isConnected) {
                Toast.makeText(this, "Connect to a PC host in Settings first", Toast.LENGTH_SHORT).show()
            } else {
                pulseMouseButton(QuipProtocol.BIT_MOUSE_L)
            }
        }
        trackpad.onRightClick = {
            if (!AppState.isConnected) {
                Toast.makeText(this, "Connect to a PC host in Settings first", Toast.LENGTH_SHORT).show()
            } else {
                pulseMouseButton(QuipProtocol.BIT_MOUSE_R)
            }
        }
    }

    // ---- Layout / status rendering -----------------------------------------------------

    private fun refreshLayoutIfNeeded() {
        if (renderedLayoutIndex == AppState.selectedLayoutIndex) return
        renderedLayoutIndex = AppState.selectedLayoutIndex

        // Release any keys that were held from the previous layout before swapping it out.
        AppState.engine.currentDigitalMask = 0L

        val layout = KeyLayoutParser.parse(this, AppState.selectedLayoutRes)
        keyboardContainer.removeAllViews()
        keyboardContainer.addView(keyboardPanel.build(layout))
    }

    private fun refreshStatus() {
        val colorRes = if (AppState.isConnected) R.color.quip_status_ok else R.color.quip_status_bad
        statusDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
        tvStatus.text = if (AppState.isConnected) {
            "Connected \u00b7 ${AppState.hostIp}"
        } else {
            "Not connected"
        }
    }

    // ---- Mouse click pulses ------------------------------------------------------------

    /** Sets [bit] for one short frame window then clears it, i.e. a click "down + up". */
    private fun pulseMouseButton(bit: Long) {
        AppState.engine.currentDigitalMask = AppState.engine.currentDigitalMask or bit
        sendHandler.postDelayed({
            AppState.engine.currentDigitalMask = AppState.engine.currentDigitalMask and bit.inv()
        }, 60L)
    }
}
