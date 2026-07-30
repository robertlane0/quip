package com.quip.client

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class TrackpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onMouseMove(dx: Int, dy: Int)
        fun onLeftClick()
        fun onRightClick()
    }

    var listener: Listener? = null

    /**
     * Multiplier applied to finger movement.
     */
    var sensitivity = 1.3f

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    listener?.onLeftClick()
                    performClick()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    listener?.onRightClick()
                    return true
                }
            }
        )

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val touchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private var touchX = -1f
    private var touchY = -1f

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y

                touchX = event.x
                touchY = event.y

                dragging = true
                invalidate()
            }

            MotionEvent.ACTION_MOVE -> {

                if (dragging) {

                    val dx = ((event.x - lastX) * sensitivity).roundToInt()
                    val dy = ((event.y - lastY) * sensitivity).roundToInt()

                    if (dx != 0 || dy != 0) {
                        listener?.onMouseMove(dx, dy)
                    }

                    lastX = event.x
                    lastY = event.y

                    touchX = event.x
                    touchY = event.y

                    invalidate()
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                dragging = false

                touchX = -1f
                touchY = -1f

                invalidate()
            }
        }

        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Border
        canvas.drawRoundRect(
            2f,
            2f,
            width - 2f,
            height - 2f,
            24f,
            24f,
            borderPaint
        )

        // Finger indicator
        if (touchX >= 0f && touchY >= 0f) {
            canvas.drawCircle(
                touchX,
                touchY,
                36f,
                touchPaint
            )
        }
    }
}
