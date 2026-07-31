package com.quip.client

import android.content.Context
import androidx.annotation.XmlRes
import org.xmlpull.v1.XmlPullParser

/**
 * Reads just the root tag name of a res/xml/.xml resource, so callers can decide which
 * parser to hand it to (e.g. "key-layout" -> [KeyLayoutParser], "control-scheme" ->
 * [ControlSchemeParser]) without needing a separate registry of which is which.
 */
object LayoutXmlUtils {

    fun peekRootTag(context: Context, @XmlRes resId: Int): String {
        val parser = context.resources.getXml(resId)
        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) return parser.name
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }
        return ""
    }
}
