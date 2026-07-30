package com.quip.client

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Renders a [KeyLayout] as a grid of pressable key buttons and forwards press/release
 * state directly into an [InputEngine]'s digital bitmask. The periodic send loop in
 * MainActivity picks that state up and transmits it — this class never touches the
 * network itself.
 */
class KeyboardPanel(private val context: Context, private val engine: InputEngine) {

    fun build(layout: KeyLayout): GridLayout {
        val density = context.resources.displayMetrics.density
        val margin = (5 * density).toInt()

        val grid = GridLayout(context).apply {
            rowCount = layout.rows
            columnCount = layout.columns
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        for (keyDef in layout.keys) {
            val bit = QuipProtocol.bitByName(keyDef.bitName)
            val keyView = createKeyView(keyDef, bit)
            val rowWeight = layout.rowWeights.getOrElse(keyDef.row) { 1f }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(keyDef.row, keyDef.rowSpan, GridLayout.FILL, rowWeight),
                GridLayout.spec(keyDef.col, keyDef.colSpan, GridLayout.FILL, 1f)
            )
            params.setMargins(margin, margin, margin, margin)
            keyView.layoutParams = params
            grid.addView(keyView)
        }

        return grid
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createKeyView(keyDef: KeyDef, bit: Long): TextView {
        return TextView(context).apply {
            text = keyDef.label
            textSize = if (keyDef.label.length <= 1) 26f else 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.quip_text_primary))
            isClickable = true
            isFocusable = true
            background = keyBackground(keyDef.accent, pressed = false)

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        engine.currentDigitalMask = engine.currentDigitalMask or bit
                        v.background = keyBackground(keyDef.accent, pressed = true)
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        engine.currentDigitalMask = engine.currentDigitalMask and bit.inv()
                        v.background = keyBackground(keyDef.accent, pressed = false)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun keyBackground(accent: Boolean, pressed: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density
        val fillRes = when {
            accent && pressed -> R.color.quip_key_accent_fill_pressed
            accent -> R.color.quip_key_accent_fill
            pressed -> R.color.quip_key_fill_pressed
            else -> R.color.quip_key_fill
        }
        val strokeRes = if (accent) R.color.quip_key_accent_stroke else R.color.quip_key_stroke

        return GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(ContextCompat.getColor(context, fillRes))
            setStroke((1.5f * density).toInt(), ContextCompat.getColor(context, strokeRes))
        }
    }
}
