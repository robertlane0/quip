package com.quip.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.hypot
import kotlin.math.min

/**
 * A virtual analog-look joystick that drives up to 4 independent digital bits (typically
 * WASD) rather than a continuous analog axis — dragging diagonally activates two bits at
 * once (e.g. up+right), the same way holding two real keys would.
 *
 * The view's full touchable bounds are intentionally larger than the visible track (a
 * bigger hit target is easier to find with a thumb without looking); only the circular
 * track + knob are actually drawn. Touching anywhere in the bounds — not just on the knob
 * — immediately moves the knob there, so no precise initial grab is required.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Bits toggled when the knob is pushed up / down / left / right of center. */
    var bitUp: Long = 0
    var bitDown: Long = 0
    var bitLeft: Long = 0
    var bitRight: Long = 0

    /** Called whenever the combined active-direction bitmask changes. */
    var onDirectionBitsChanged: ((bits: Long) -> Unit)? = null

    // How far (as a fraction of the track radius) the knob must move off-center before a
    // direction "engages". Per-axis, so a mostly-vertical drag with a slight lean sideways
    // won't spuriously add a left/right bit.
    private val deadZoneFraction = 0.35f

    // The visible track circle is inset from the full (larger) touchable view bounds.
    private val trackInsetFraction = 0.15f

    private var knobOffsetX = 0f
    private var knobOffsetY = 0f
    private var activeBits = 0L
    private var knobAnimator: android.animation.ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = ContextCompat.getColor(context, R.color.quip_joystick_track)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.quip_joystick_knob)
    }

    init {
        isClickable = true
        isFocusable = true
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                knobAnimator?.cancel()
                updateKnob(event.x - width / 2f, event.y - height / 2f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                performClick()
                releaseKnob()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun trackRadius(): Float = min(width, height) * (0.5f - trackInsetFraction)

    private fun updateKnob(rawDx: Float, rawDy: Float) {
        val radius = trackRadius()
        val distance = hypot(rawDx, rawDy)
        val clamped = min(distance, radius)
        val scale = if (distance > 0f) clamped / distance else 0f
        knobOffsetX = rawDx * scale
        knobOffsetY = rawDy * scale

        val threshold = radius * deadZoneFraction
        var bits = 0L
        if (knobOffsetY < -threshold) bits = bits or bitUp
        if (knobOffsetY > threshold) bits = bits or bitDown
        if (knobOffsetX < -threshold) bits = bits or bitLeft
        if (knobOffsetX > threshold) bits = bits or bitRight
        setActiveBits(bits)
        invalidate()
    }

    private fun releaseKnob() {
        val startX = knobOffsetX
        val startY = knobOffsetY
        knobAnimator?.cancel()
        knobAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 120L
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                knobOffsetX = startX * t
                knobOffsetY = startY * t
                invalidate()
            }
            start()
        }
        setActiveBits(0L)
    }

    private fun setActiveBits(bits: Long) {
        if (bits != activeBits) {
            activeBits = bits
            onDirectionBitsChanged?.invoke(bits)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = trackRadius()

        canvas.drawCircle(cx, cy, radius, trackPaint)
        canvas.drawCircle(cx + knobOffsetX, cy + knobOffsetY, radius * 0.4f, knobPaint)
    }
}
