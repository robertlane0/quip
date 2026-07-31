package com.quip.client

import android.content.Context
import android.view.Gravity
import androidx.annotation.XmlRes
import org.xmlpull.v1.XmlPullParser

/**
 * Parses control-scheme XML resources (see controlscheme_joystick_hud.xml for the schema
 * and an annotated example) into a [ControlScheme]. This is what makes a joystick / button
 * / trackpad HUD data-driven — a new layout is "add an XML file", not "write new Kotlin",
 * exactly like [KeyLayoutParser] does for grid-based keyboards.
 */
object ControlSchemeParser {

    fun parse(context: Context, @XmlRes resId: Int): ControlScheme {
        val parser = context.resources.getXml(resId)
        var name = "Control Scheme"
        val elements = mutableListOf<ControlElement>()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "control-scheme" -> {
                            name = parser.getAttributeValue(null, "name") ?: name
                        }
                        "trackpad" -> {
                            elements.add(
                                ControlElement.TrackpadElement(
                                    fullscreen = parser.getAttributeBooleanValue(null, "fullscreen", true)
                                )
                            )
                        }
                        "joystick" -> {
                            elements.add(
                                ControlElement.JoystickElement(
                                    sizeDp = floatAttr(parser, "size", 200f),
                                    gravity = gravityAttr(parser, "bottom|start"),
                                    marginStartDp = floatAttr(parser, "marginStart", 0f),
                                    marginEndDp = floatAttr(parser, "marginEnd", 0f),
                                    marginTopDp = floatAttr(parser, "marginTop", 0f),
                                    marginBottomDp = floatAttr(parser, "marginBottom", 0f),
                                    bitUpName = parser.getAttributeValue(null, "bitUp") ?: "BIT_W",
                                    bitDownName = parser.getAttributeValue(null, "bitDown") ?: "BIT_S",
                                    bitLeftName = parser.getAttributeValue(null, "bitLeft") ?: "BIT_A",
                                    bitRightName = parser.getAttributeValue(null, "bitRight") ?: "BIT_D",
                                    bitSprintName = parser.getAttributeValue(null, "bitSprint"),
                                    floatRangeFraction = parser.getAttributeValue(null, "floatRange")?.toFloatOrNull()
                                )
                            )
                        }
                        "button" -> {
                            val bitName = parser.getAttributeValue(null, "bit")
                            requireNotNull(bitName) { "<button> in control-scheme \"$name\" is missing a bit attribute" }
                            elements.add(
                                ControlElement.ButtonElement(
                                    label = parser.getAttributeValue(null, "label") ?: "",
                                    bitName = bitName,
                                    sizeDp = floatAttr(parser, "size", 110f),
                                    gravity = gravityAttr(parser, "bottom|end"),
                                    marginStartDp = floatAttr(parser, "marginStart", 0f),
                                    marginEndDp = floatAttr(parser, "marginEnd", 0f),
                                    marginTopDp = floatAttr(parser, "marginTop", 0f),
                                    marginBottomDp = floatAttr(parser, "marginBottom", 0f)
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }

        return ControlScheme(name, elements)
    }

    private fun floatAttr(parser: XmlPullParser, name: String, default: Float): Float =
        parser.getAttributeValue(null, name)?.toFloatOrNull() ?: default

    private fun gravityAttr(parser: XmlPullParser, default: String): Int =
        parseGravity(parser.getAttributeValue(null, "gravity") ?: default)

    private fun parseGravity(raw: String): Int {
        var g = 0
        for (token in raw.split("|")) {
            g = g or when (token.trim()) {
                "top" -> Gravity.TOP
                "bottom" -> Gravity.BOTTOM
                "start" -> Gravity.START
                "end" -> Gravity.END
                "left" -> Gravity.LEFT
                "right" -> Gravity.RIGHT
                "center" -> Gravity.CENTER
                "center_horizontal" -> Gravity.CENTER_HORIZONTAL
                "center_vertical" -> Gravity.CENTER_VERTICAL
                else -> 0
            }
        }
        return if (g == 0) Gravity.CENTER else g
    }
}
