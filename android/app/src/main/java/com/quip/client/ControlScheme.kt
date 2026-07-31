package com.quip.client

/**
 * A "control scheme" is the freeform counterpart to [KeyLayout]'s row/column grid: instead
 * of keys in cells, elements are absolutely positioned (gravity + margins) and layered, so
 * a joystick and action buttons can float over a full-screen trackpad. Parsed from
 * res/xml/controlscheme_*.xml by [ControlSchemeParser]; see keylayout_wasd.xml-style docs
 * in controlscheme_joystick_hud.xml for the tag schema.
 */
data class ControlScheme(
    val name: String,
    val elements: List<ControlElement>
)

sealed class ControlElement {

    /**
     * A mouse surface. When [fullscreen] is true it fills the entire scheme (added first,
     * so it sits behind every other element in z-order) — any touch not claimed by a
     * joystick or button above it falls through and is treated as trackpad input.
     */
    data class TrackpadElement(val fullscreen: Boolean) : ControlElement()

    /** A virtual stick mapped to up to 4 independent digital bits (see [JoystickView]). */
    data class JoystickElement(
        val sizeDp: Float,
        val gravity: Int,
        val marginStartDp: Float,
        val marginEndDp: Float,
        val marginTopDp: Float,
        val marginBottomDp: Float,
        val bitUpName: String,
        val bitDownName: String,
        val bitLeftName: String,
        val bitRightName: String,
        /** Optional: bit fired as a bounded pulse when pushed fully to the edge (e.g. sprint). */
        val bitSprintName: String? = null,
        /** Optional: fraction of the screen's width/height the joystick can "float" within
         *  when tapped, relative to its own anchor corner. Null = fixed position (bounds
         *  equal the track's own size, matching [sizeDp] exactly). */
        val floatRangeFraction: Float? = null
    ) : ControlElement()

    /** A circular, freely-positioned press-and-hold button bound to one digital bit. */
    data class ButtonElement(
        val label: String,
        val bitName: String,
        val sizeDp: Float,
        val gravity: Int,
        val marginStartDp: Float,
        val marginEndDp: Float,
        val marginTopDp: Float,
        val marginBottomDp: Float
    ) : ControlElement()
}
