package com.quip.client

import android.animation.ValueAnimator
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
 * The View's own bounds are its *floating range*: touching anywhere within them relocates
 * the visible track/knob to that point, so the player doesn't have to find one exact fixed
 * spot with their thumb. [trackDiameterPx] controls the actual visual track/knob size,
 * independent of how large the floating range is — set the View's bounds equal to
 * [trackDiameterPx] for a classic fixed-position joystick, or larger for a floating one.
 *
 * Optionally supports a "sprint on full extension" bit: holding the knob pinned to the
 * track's outer edge fires [bitSprint] as a bounded pulse (matching games that toggle
 * sprint on with a brief Shift press rather than requiring it held down), and cancels the
 * pulse early if the knob is released or pulled back off the edge before it would
 * otherwise expire.
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** The joystick's visual/track diameter in pixels — independent of the View's own
     *  (possibly larger) floating-range bounds. */
    var trackDiameterPx: Int = 0

    /** Bits toggled when the knob is pushed up / down / left / right of center. */
    var bitUp: Long = 0
    var bitDown: Long = 0
    var bitLeft: Long = 0
    var bitRight: Long = 0

    /** Optional bit fired as a bounded pulse when the knob is pushed fully to the edge. */
    var bitSprint: Long = 0

    /** Called whenever the combined active-bit mask (direction + sprint) changes. */
    var onDirectionBitsChanged: ((bits: Long) -> Unit)? = null

    // Per-axis dead zone, as a fraction of the track radius, before a direction "engages".
    private val deadZoneFraction = 0.35f

    // Knob extension (fraction of radius) at/above which sprint engages, and the lower
    // fraction it must retreat past to disengage — the gap between the two avoids flicker
    // right at the boundary ("no part of the inner circle touching the edge" to release).
    private val sprintEngageFraction = 0.97f
    private val sprintDisengageFraction = 0.90f
    private val sprintPulseDurationMs = 1500L

    private var trackCenterX = 0f
    private var trackCenterY = 0f
    private var trackCenterSet = false

    private var knobOffsetX = 0f
    private var knobOffsetY = 0f
    private var directionBits = 0L
    private var sprintActive = false
    private var atEdge = false
    private var lastPublishedBits = 0L

    private var knobAnimator: ValueAnimator? = null
    private val sprintTimeoutRunnable = Runnable {
        sprintActive = false
        publishBits()
    }

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!trackCenterSet) {
            // Default resting position: bottom-left of the floating range — the same
            // corner a classic fixed-position joystick would occupy.
            val r = trackRadius()
            trackCenterX = r
            trackCenterY = h - r
            trackCenterSet = true
        }
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                knobAnimator?.cancel()
                recenterTrackTo(event.x, event.y)
                updateKnob(0f, 0f)
            }
            MotionEvent.ACTION_MOVE -> {
                updateKnob(event.x - trackCenterX, event.y - trackCenterY)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(sprintTimeoutRunnable)
        knobAnimator?.cancel()
    }

    private fun trackRadius(): Float = trackDiameterPx / 2f

    /** Moves the track/knob base to (touchX, touchY), clamped so it stays fully in bounds. */
    private fun recenterTrackTo(touchX: Float, touchY: Float) {
        val r = trackRadius()
        trackCenterX = touchX.coerceIn(r, (width - r).coerceAtLeast(r))
        trackCenterY = touchY.coerceIn(r, (height - r).coerceAtLeast(r))
        trackCenterSet = true
    }

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
        directionBits = bits

        updateSprintState(radius, clamped)
        publishBits()
        invalidate()
    }

    private fun updateSprintState(radius: Float, clamped: Float) {
        if (bitSprint == 0L || radius <= 0f) return
        val extension = clamped / radius
        if (!atEdge && extension >= sprintEngageFraction) {
            atEdge = true
            sprintActive = true
            removeCallbacks(sprintTimeoutRunnable)
            postDelayed(sprintTimeoutRunnable, sprintPulseDurationMs)
        } else if (atEdge && extension < sprintDisengageFraction) {
            atEdge = false
            sprintActive = false
            removeCallbacks(sprintTimeoutRunnable)
        }
    }

    private fun releaseKnob() {
        val startX = knobOffsetX
        val startY = knobOffsetY
        knobAnimator?.cancel()
        knobAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 120L
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                knobOffsetX = startX * t
                knobOffsetY = startY * t
                invalidate()
            }
            start()
        }
        directionBits = 0L
        atEdge = false
        sprintActive = false
        removeCallbacks(sprintTimeoutRunnable)
        publishBits()
    }

    private fun publishBits() {
        val bits = if (sprintActive) directionBits or bitSprint else directionBits
        if (bits != lastPublishedBits) {
            lastPublishedBits = bits
            onDirectionBitsChanged?.invoke(bits)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = trackRadius()
        canvas.drawCircle(trackCenterX, trackCenterY, radius, trackPaint)
        canvas.drawCircle(trackCenterX + knobOffsetX, trackCenterY + knobOffsetY, radius * 0.4f, knobPaint)
    }
}
