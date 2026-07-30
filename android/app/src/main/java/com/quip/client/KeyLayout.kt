package com.quip.client

import android.content.Context
import androidx.annotation.XmlRes
import org.xmlpull.v1.XmlPullParser

/**
 * A single button in a key layout, bound to a QuipProtocol digital-mask bit.
 *
 * [row]/[col] are grid coordinates within the parent [KeyLayout]'s [KeyLayout.rows] x
 * [KeyLayout.columns] grid; [rowSpan]/[colSpan] let a key occupy more than one cell
 * (e.g. a wide Space bar).
 */
data class KeyDef(
    val label: String,
    val bitName: String,
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val accent: Boolean = false
)

/**
 * A named grid of [KeyDef]s, parsed from a res/xml/keylayout_*.xml resource.
 *
 * [rowWeights] controls each row's share of the available vertical space (same idea as
 * LinearLayout's layout_weight) — a row with weight 2 renders twice as tall as a row
 * with weight 1. Defaults to equal weight (1f) per row when not specified in XML.
 */
data class KeyLayout(
    val name: String,
    val rows: Int,
    val columns: Int,
    val rowWeights: List<Float>,
    val keys: List<KeyDef>
)

/**
 * Parses key-layout XML resources (see res/xml/keylayout_wasd.xml for the schema and
 * comments) into [KeyLayout] objects. This is what makes different control schemes
 * data-driven: shipping a new layout is "add an XML file", not "write new Kotlin".
 */
object KeyLayoutParser {

    fun parse(context: Context, @XmlRes resId: Int): KeyLayout {
        val parser = context.resources.getXml(resId)
        var name = ""
        var rows = 1
        var columns = 1
        var rowWeightsAttr: String? = null
        val keys = mutableListOf<KeyDef>()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "key-layout" -> {
                            name = parser.getAttributeValue(null, "name") ?: "Layout"
                            rows = parser.getAttributeIntValue(null, "rows", 1)
                            columns = parser.getAttributeIntValue(null, "columns", 1)
                            rowWeightsAttr = parser.getAttributeValue(null, "rowWeights")
                        }
                        "key" -> {
                            val bitName = parser.getAttributeValue(null, "bit")
                            requireNotNull(bitName) { "<key> in key-layout \"$name\" is missing a bit attribute" }
                            keys.add(
                                KeyDef(
                                    label = parser.getAttributeValue(null, "label") ?: "",
                                    bitName = bitName,
                                    row = parser.getAttributeIntValue(null, "row", 0),
                                    col = parser.getAttributeIntValue(null, "col", 0),
                                    rowSpan = parser.getAttributeIntValue(null, "rowSpan", 1),
                                    colSpan = parser.getAttributeIntValue(null, "colSpan", 1),
                                    accent = parser.getAttributeBooleanValue(null, "accent", false)
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

        val rowWeights = rowWeightsAttr
            ?.split(",")
            ?.map { it.trim().toFloatOrNull() ?: 1f }
            ?.let { parsed -> List(rows) { i -> parsed.getOrElse(i) { 1f } } }
            ?: List(rows) { 1f }

        return KeyLayout(name, rows, columns, rowWeights, keys)
    }
}
