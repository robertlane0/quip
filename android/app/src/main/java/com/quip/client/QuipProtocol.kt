package com.quip.client

/**
 * QUIP Protocol Bitfield Constant Map (64-Bit Digital Mask)
 */
object QuipProtocol {
    const val BIT_W: Long          = 1L shl 0   // Key W
    const val BIT_A: Long          = 1L shl 1   // Key A
    const val BIT_S: Long          = 1L shl 2   // Key S
    const val BIT_D: Long          = 1L shl 3   // Key D
    const val BIT_SPACE: Long      = 1L shl 4   // Spacebar
    const val BIT_SHIFT: Long      = 1L shl 5   // Left Shift
    const val BIT_TAB: Long        = 1L shl 6   // Tab
    const val BIT_ESC: Long        = 1L shl 7   // Escape
    const val BIT_E: Long          = 1L shl 8   // Key E
    const val BIT_R: Long          = 1L shl 9   // Key R
    const val BIT_C: Long          = 1L shl 10  // Key C
    const val BIT_CTRL: Long       = 1L shl 11  // Left Ctrl
    const val BIT_MOUSE_L: Long    = 1L shl 12  // Mouse Left Click
    const val BIT_MOUSE_R: Long    = 1L shl 13  // Mouse Right Click
    const val BIT_MOUSE_M: Long    = 1L shl 14  // Mouse Middle Click
    const val BIT_MOUSE_THUMB1: Long = 1L shl 15// Mouse Side Button 1
    const val BIT_MOUSE_THUMB2: Long = 1L shl 16// Mouse Side Button 2

    // Gamepad Mappings
    const val BIT_GAMEPAD_A: Long     = 1L shl 32
    const val BIT_GAMEPAD_B: Long     = 1L shl 33
    const val BIT_GAMEPAD_X: Long     = 1L shl 34
    const val BIT_GAMEPAD_Y: Long     = 1L shl 35
    const val BIT_GAMEPAD_LB: Long    = 1L shl 36
    const val BIT_GAMEPAD_RB: Long    = 1L shl 37
    const val BIT_GAMEPAD_LT: Long    = 1L shl 38
    const val BIT_GAMEPAD_RT: Long    = 1L shl 39
    const val BIT_GAMEPAD_SELECT: Long= 1L shl 40
    const val BIT_GAMEPAD_START: Long = 1L shl 41
    const val BIT_GAMEPAD_L3: Long    = 1L shl 43
    const val BIT_GAMEPAD_R3: Long    = 1L shl 44

    /**
     * Name -> bit lookup so that key layouts declared in XML (res/xml/keylayout_*.xml)
     * can reference a bit by its constant name (e.g. bit="BIT_W") instead of hardcoding
     * a numeric value. Add new bit constants above and they become available to XML
     * layouts automatically.
     */
    private val byName: Map<String, Long> = mapOf(
        "BIT_W" to BIT_W,
        "BIT_A" to BIT_A,
        "BIT_S" to BIT_S,
        "BIT_D" to BIT_D,
        "BIT_SPACE" to BIT_SPACE,
        "BIT_SHIFT" to BIT_SHIFT,
        "BIT_TAB" to BIT_TAB,
        "BIT_ESC" to BIT_ESC,
        "BIT_E" to BIT_E,
        "BIT_R" to BIT_R,
        "BIT_C" to BIT_C,
        "BIT_CTRL" to BIT_CTRL,
        "BIT_MOUSE_L" to BIT_MOUSE_L,
        "BIT_MOUSE_R" to BIT_MOUSE_R,
        "BIT_MOUSE_M" to BIT_MOUSE_M,
        "BIT_MOUSE_THUMB1" to BIT_MOUSE_THUMB1,
        "BIT_MOUSE_THUMB2" to BIT_MOUSE_THUMB2,
        "BIT_GAMEPAD_A" to BIT_GAMEPAD_A,
        "BIT_GAMEPAD_B" to BIT_GAMEPAD_B,
        "BIT_GAMEPAD_X" to BIT_GAMEPAD_X,
        "BIT_GAMEPAD_Y" to BIT_GAMEPAD_Y,
        "BIT_GAMEPAD_LB" to BIT_GAMEPAD_LB,
        "BIT_GAMEPAD_RB" to BIT_GAMEPAD_RB,
        "BIT_GAMEPAD_LT" to BIT_GAMEPAD_LT,
        "BIT_GAMEPAD_RT" to BIT_GAMEPAD_RT,
        "BIT_GAMEPAD_SELECT" to BIT_GAMEPAD_SELECT,
        "BIT_GAMEPAD_START" to BIT_GAMEPAD_START,
        "BIT_GAMEPAD_L3" to BIT_GAMEPAD_L3,
        "BIT_GAMEPAD_R3" to BIT_GAMEPAD_R3
    )

    /** Resolves a bit constant by its XML-declared name, e.g. "BIT_W". */
    fun bitByName(name: String): Long =
        byName[name] ?: throw IllegalArgumentException(
            "Unknown QuipProtocol bit \"$name\" referenced from a key-layout XML file"
        )
}
