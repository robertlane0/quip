package com.quip.client

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Renders a [ControlScheme] as a single layered [FrameLayout]: trackpad(s) first (so they
 * sit at the bottom of the z-order), then the joystick and buttons on top. Android hit-tests
 * a ViewGroup's children topmost-first, so touches landing on the joystick/buttons are
 * claimed by them; everything else falls through to the trackpad beneath — which is exactly
 * what makes "background mouse" work with no manual region-exclusion logic.
 *
 * The trackpad move/click callbacks are passed in rather than owned here, so MainActivity
 * can wire the *same* click/move behavior into both this HUD and the classic keyboard+
 * trackpad panel — one implementation of "what a click/drag does," reused by both layouts.
 */
class ControlSchemeBuilder(
    private val context: Context,
    private val engine: InputEngine,
    private val onTrackpadMove: (dx: Float, dy: Float) -> Unit,
    private val onTrackpadLeftClick: () -> Unit,
    private val onTrackpadRightClick: () -> Unit
) {
    private val density = context.resources.displayMetrics.density

    fun build(scheme: ControlScheme): FrameLayout {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        scheme.elements.filterIsInstance<ControlElement.TrackpadElement>().forEach {
            root.addView(buildTrackpad(it))
        }
        scheme.elements.filterIsInstance<ControlElement.JoystickElement>().forEach {
            root.addView(buildJoystick(it))
        }
        scheme.elements.filterIsInstance<ControlElement.ButtonElement>().forEach {
            root.addView(buildButton(it))
        }

        return root
    }

    private fun buildTrackpad(spec: ControlElement.TrackpadElement): TrackpadView {
        // Only fullscreen trackpads are supported for now; bounded ones could be added
        // later using the same margin/gravity attributes as joystick/button.
        return TrackpadView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onMove = onTrackpadMove
            onLeftClick = onTrackpadLeftClick
            onRightClick = onTrackpadRightClick
        }
    }

    private fun buildJoystick(spec: ControlElement.JoystickElement): JoystickView {
        val sizePx = (spec.sizeDp * density).toInt()
        return JoystickView(context).apply {
            bitUp = QuipProtocol.bitByName(spec.bitUpName)
            bitDown = QuipProtocol.bitByName(spec.bitDownName)
            bitLeft = QuipProtocol.bitByName(spec.bitLeftName)
            bitRight = QuipProtocol.bitByName(spec.bitRightName)

            val ownedBits = bitUp or bitDown or bitLeft or bitRight
            onDirectionBitsChanged = { activeBits ->
                engine.currentDigitalMask = (engine.currentDigitalMask and ownedBits.inv()) or activeBits
            }

            layoutParams = marginedLayoutParams(sizePx, sizePx, spec.gravity, spec.marginStartDp, spec.marginEndDp, spec.marginTopDp, spec.marginBottomDp)
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun buildButton(spec: ControlElement.ButtonElement): TextView {
        val sizePx = (spec.sizeDp * density).toInt()
        val bit = QuipProtocol.bitByName(spec.bitName)

        return TextView(context).apply {
            text = spec.label
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.quip_text_primary))
            background = buttonBackground(pressed = false)
            isClickable = true
            isFocusable = true

            layoutParams = marginedLayoutParams(sizePx, sizePx, spec.gravity, spec.marginStartDp, spec.marginEndDp, spec.marginTopDp, spec.marginBottomDp)

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        engine.currentDigitalMask = engine.currentDigitalMask or bit
                        v.background = buttonBackground(pressed = true)
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        engine.currentDigitalMask = engine.currentDigitalMask and bit.inv()
                        v.background = buttonBackground(pressed = false)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun marginedLayoutParams(
        widthPx: Int,
        heightPx: Int,
        gravity: Int,
        marginStartDp: Float,
        marginEndDp: Float,
        marginTopDp: Float,
        marginBottomDp: Float
    ): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(widthPx, heightPx, gravity).apply {
            marginStart = (marginStartDp * density).toInt()
            marginEnd = (marginEndDp * density).toInt()
            topMargin = (marginTopDp * density).toInt()
            bottomMargin = (marginBottomDp * density).toInt()
        }

    private fun buttonBackground(pressed: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    context,
                    if (pressed) R.color.quip_key_fill_pressed else R.color.quip_key_fill
                )
            )
            setStroke((1.5f * density).toInt(), ContextCompat.getColor(context, R.color.quip_key_stroke))
        }
    }
}
