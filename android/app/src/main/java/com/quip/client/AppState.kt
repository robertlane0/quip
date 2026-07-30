package com.quip.client

/**
 * Small process-wide singleton that owns the QUIP connection and shared input state so
 * MainActivity (keyboard/trackpad UI) and SettingsActivity (connection + layout picker)
 * can operate on the same session without passing objects through Intents.
 */
object AppState {

    /**
     * Available key layouts. To register a new one: add a res/xml/keylayout_*.xml file
     * (see keylayout_wasd.xml for the schema) and add one line here — it will show up in
     * the Settings screen's layout picker automatically.
     */
    val layouts: List<Pair<String, Int>> = listOf(
        "WASD" to R.xml.keylayout_wasd,
        "WASD + Mods" to R.xml.keylayout_wasd_full
    )

    val engine = InputEngine()
    val transport = TransportManager(NativeQuipClient())

    var selectedLayoutIndex: Int = 0

    var hostIp: String = ""
        private set

    var isConnected: Boolean = false
        private set

    val selectedLayoutRes: Int
        get() = layouts.getOrElse(selectedLayoutIndex) { layouts.first() }.second

    fun connect(ip: String, port: Int = 9876): Boolean {
        hostIp = ip
        isConnected = transport.connectWifi(ip, port)
        return isConnected
    }

    fun disconnect() {
        transport.disconnect()
        isConnected = false
    }
}
