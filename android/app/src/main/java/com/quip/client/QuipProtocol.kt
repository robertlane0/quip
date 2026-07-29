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
}
