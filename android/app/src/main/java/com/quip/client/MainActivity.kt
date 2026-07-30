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

    private lateinit var contentContainer: FrameLayout
    private lateinit var statusDot: View
    private lateinit var tvStatus: TextView

    private lateinit var keyboardPanel: KeyboardPanel
    private lateinit var controlSchemeBuilder: ControlSchemeBuilder

    // Tracks which scheme is currently built into contentContainer so we only rebuild it
    // when the selection actually changed (e.g. after returning from Settings).
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

    // One implementation of "what a trackpad drag/tap/double-tap does," shared by both the
    // classic boxed TrackpadView (panel_keyboard_trackpad.xml) and the fullscreen
    // background trackpad a control-scheme HUD builds via ControlSchemeBuilder.
    private val trackpadOnMove: (Float, Float) -> Unit = { dx, dy ->
        AppState.engine.addTouchDelta(dx, dy)
    }
    private val trackpadOnLeftClick: () -> Unit = {
        if (!AppState.isConnected) {
            Toast.makeText(this, "Connect to a PC host in Settings first", Toast.LENGTH_SHORT).show()
        } else {
            pulseMouseButton(QuipProtocol.BIT_MOUSE_L)
        }
    }
    private val trackpadOnRightClick: () -> Unit = {
        if (!AppState.isConnected) {
            Toast.makeText(this, "Connect to a PC host in Settings first", Toast.LENGTH_SHORT).show()
        } else {
            pulseMouseButton(QuipProtocol.BIT_MOUSE_R)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemBars()

        contentContainer = findViewById(R.id.contentContainer)
        statusDot = findViewById(R.id.statusDot)
        tvStatus = findViewById(R.id.tvStatus)
        val btnSettings: View = findViewById(R.id.btnSettings)

        keyboardPanel = KeyboardPanel(this, AppState.engine)
        controlSchemeBuilder = ControlSchemeBuilder(
            this, AppState.engine, trackpadOnMove, trackpadOnLeftClick, trackpadOnRightClick
        )

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        attemptAutoConnectIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        renderSchemeIfNeeded()
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

    // ---- Auto-connect -----------------------------------------------------------------

    /**
     * Tries, once per process lifetime, to reconnect to the last device the user
     * successfully connected to. No-ops if auto-connect is off, we're already
     * connected, or nothing has ever been connected to yet.
     */
    private fun attemptAutoConnectIfNeeded() {
        if (AppState.autoConnectAttempted) return
        AppState.autoConnectAttempted = true

        if (!AppState.autoConnectEnabled || AppState.isConnected) return
        val lastIp = AppState.hostIp.takeIf { it.isNotBlank() } ?: return

        val ok = AppState.connect(lastIp)
        refreshStatus()
        Toast.makeText(
            this,
            if (ok) "Auto-connected to $lastIp" else "Auto-connect to $lastIp failed",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ---- Scheme rendering ---------------------------------------------------------------

    /**
     * Builds whichever scheme is currently selected into contentContainer — a grid
     * key-layout renders the classic Keyboard|Trackpad panel, a control-scheme renders a
     * freeform HUD. Dispatches purely on the selected XML resource's root tag, so adding a
     * third schema type later needs no changes here beyond one more "when" branch.
     */
    private fun renderSchemeIfNeeded() {
        if (renderedLayoutIndex == AppState.selectedLayoutIndex) return
        renderedLayoutIndex = AppState.selectedLayoutIndex

        // Release any keys/buttons held from the previous scheme before swapping it out.
        AppState.engine.currentDigitalMask = 0L

        contentContainer.removeAllViews()
        val resId = AppState.selectedLayoutRes

        when (LayoutXmlUtils.peekRootTag(this, resId)) {
            "control-scheme" -> renderControlScheme(resId)
            else -> renderKeyGridPanel(resId)
        }
    }

    private fun renderKeyGridPanel(resId: Int) {
        layoutInflater.inflate(R.layout.panel_keyboard_trackpad, contentContainer, true)

        val keyboardContainer: FrameLayout = contentContainer.findViewById(R.id.keyboardContainer)
        val trackpad: TrackpadView = contentContainer.findViewById(R.id.trackpad)

        val layout = KeyLayoutParser.parse(this, resId)
        keyboardContainer.addView(keyboardPanel.build(layout))

        trackpad.onMove = trackpadOnMove
        trackpad.onLeftClick = trackpadOnLeftClick
        trackpad.onRightClick = trackpadOnRightClick
    }

    private fun renderControlScheme(resId: Int) {
        val scheme = ControlSchemeParser.parse(this, resId)
        contentContainer.addView(controlSchemeBuilder.build(scheme))
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
