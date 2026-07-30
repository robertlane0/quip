package com.quip.client

import android.content.Context
import android.content.SharedPreferences

/**
 * Small process-wide singleton that owns the QUIP connection and shared input state so
 * MainActivity (keyboard/trackpad UI) and SettingsActivity (connection + layout picker)
 * can operate on the same session without passing objects through Intents.
 *
 * Also owns lightweight persistence (SharedPreferences) for connection preferences:
 * the last-connected host, up to 3 unique recent hosts for IP autofill, and the
 * auto-connect / remember-history toggles. Call [init] once (from QuipApplication)
 * before anything else touches this object.
 */
object AppState {

    private const val PREFS_NAME = "quip_prefs"
    private const val KEY_AUTOCONNECT = "autoconnect_enabled"
    private const val KEY_HISTORY_ENABLED = "history_enabled"
    private const val KEY_LAST_IP = "last_connected_ip"
    private const val KEY_IP_HISTORY = "ip_history"
    private const val KEY_SELECTED_LAYOUT = "selected_layout_index"
    private const val HISTORY_SEPARATOR = ","
    private const val MAX_HISTORY = 3

    private var prefs: SharedPreferences? = null

    /**
     * Available key layouts. To register a new one: add a res/xml/keylayout_*.xml file
     * (see keylayout_wasd.xml for the schema) and add one line here — it will show up in
     * the Settings screen's layout picker automatically.
     */
    val layouts: List<Pair<String, Int>> = listOf(
        "WASD" to R.xml.keylayout_wasd,
        "WASD + Mods" to R.xml.keylayout_wasd_full,
        "Joystick HUD" to R.xml.controlscheme_joystick_hud
    )

    val engine = InputEngine()
    val transport = TransportManager(NativeQuipClient())

    var selectedLayoutIndex: Int = 0
        private set

    var isConnected: Boolean = false
        private set

    /** The most recently *successfully* connected host. Powers auto-connect and prefill. */
    var hostIp: String = ""
        private set

    /** Up to [MAX_HISTORY] unique, most-recent-first hosts. Powers the IP autofill list. */
    var ipHistory: List<String> = emptyList()
        private set

    var autoConnectEnabled: Boolean = true
        private set

    var historyEnabled: Boolean = true
        private set

    /** Set once MainActivity has made (or skipped) its one launch-time auto-connect attempt. */
    var autoConnectAttempted: Boolean = false

    val selectedLayoutRes: Int
        get() = layouts.getOrElse(selectedLayoutIndex) { layouts.first() }.second

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        autoConnectEnabled = p.getBoolean(KEY_AUTOCONNECT, true)
        historyEnabled = p.getBoolean(KEY_HISTORY_ENABLED, true)
        hostIp = p.getString(KEY_LAST_IP, "") ?: ""
        ipHistory = parseHistory(p.getString(KEY_IP_HISTORY, null))
        selectedLayoutIndex = p.getInt(KEY_SELECTED_LAYOUT, 0).coerceIn(0, layouts.lastIndex)
    }

    fun connect(ip: String, port: Int = 9876): Boolean {
        val ok = transport.connectWifi(ip, port)
        isConnected = ok
        if (ok) {
            hostIp = ip
            prefs?.edit()?.putString(KEY_LAST_IP, ip)?.apply()
            if (historyEnabled) recordHistory(ip)
        }
        return ok
    }

    fun disconnect() {
        transport.disconnect()
        isConnected = false
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        autoConnectEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_AUTOCONNECT, enabled)?.apply()
    }

    fun setSelectedLayoutIndex(index: Int) {
        if (index !in layouts.indices || index == selectedLayoutIndex) return
        selectedLayoutIndex = index
        prefs?.edit()?.putInt(KEY_SELECTED_LAYOUT, index)?.apply()
    }

    /**
     * Toggles whether new hosts get added to the recent-IP autofill list. Does not erase
     * already-remembered hosts, so re-enabling restores the previous suggestions. This is
     * independent of auto-connect, which only needs the single last-connected [hostIp].
     */
    fun setHistoryEnabled(enabled: Boolean) {
        historyEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_HISTORY_ENABLED, enabled)?.apply()
    }

    private fun recordHistory(ip: String) {
        val updated = (listOf(ip) + ipHistory.filterNot { it.equals(ip, ignoreCase = true) })
            .take(MAX_HISTORY)
        ipHistory = updated
        prefs?.edit()?.putString(KEY_IP_HISTORY, updated.joinToString(HISTORY_SEPARATOR))?.apply()
    }

    private fun parseHistory(raw: String?): List<String> =
        raw?.split(HISTORY_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_HISTORY)
            ?: emptyList()
}
