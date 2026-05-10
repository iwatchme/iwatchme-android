package com.iwatchme.player.feature.playerpage.page

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class PlayerGestureDetector(
    context: Context,
    private val widthProvider: () -> Int,
) {

    enum class Side { LEFT, RIGHT }

    interface OnDoubleTapListener {
        fun onDoubleTap(event: MotionEvent): Boolean
    }

    interface OnSingleTapListener {
        fun onTap(event: MotionEvent): Boolean
    }

    interface OnLongPressListener {
        fun onLongPress(event: MotionEvent?): Boolean
        fun onLongPressEnd(event: MotionEvent?) {}
    }

    interface OnVerticalScrollListener {
        fun onScrollStart(side: Side, point: PointF)
        // totalDy: 从 ACTION_DOWN 累计 y 偏移，向下为正
        fun onScroll(side: Side, totalDy: Float)
        fun onScrollStop(side: Side)
        fun onCancel()
    }

    interface OnHorizontalScrollListener {
        fun onScrollStart(point: PointF)
        // totalDx: 从 ACTION_DOWN 累计 x 偏移，向右为正
        fun onScroll(totalDx: Float)
        fun onScrollStop(totalDx: Float)
        fun onCancel()
    }

    private val doubleTapProcessor = PriorityGestureProcessor<OnDoubleTapListener>()
    private val singleTapProcessor = PriorityGestureProcessor<OnSingleTapListener>()
    private val longPressProcessor = PriorityGestureProcessor<OnLongPressListener>()

    private var verticalScrollListener: OnVerticalScrollListener? = null
    private var horizontalScrollListener: OnHorizontalScrollListener? = null

    fun addOnDoubleTapListener(
        l: OnDoubleTapListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = doubleTapProcessor.add(l, priority)

    fun removeOnDoubleTapListener(l: OnDoubleTapListener) = doubleTapProcessor.remove(l)

    fun addOnSingleTapListener(
        l: OnSingleTapListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = singleTapProcessor.add(l, priority)

    fun removeOnSingleTapListener(l: OnSingleTapListener) = singleTapProcessor.remove(l)

    fun addOnLongPressListener(
        l: OnLongPressListener,
        priority: Int = PriorityGestureProcessor.PRIORITY_NORMAL,
    ) = longPressProcessor.add(l, priority)

    fun removeOnLongPressListener(l: OnLongPressListener) = longPressProcessor.remove(l)

    fun setVerticalScrollListener(l: OnVerticalScrollListener?) { verticalScrollListener = l }

    fun setHorizontalScrollListener(l: OnHorizontalScrollListener?) { horizontalScrollListener = l }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private enum class Mode { IDLE, LOCKED_HORIZONTAL, LOCKED_VERTICAL }

    private var mode: Mode = Mode.IDLE
    private var verticalSide: Side = Side.RIGHT
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var directionDecided: Boolean = false

    private var longPressActive: Boolean = false

    private val systemDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return singleTapProcessor.process { it.onTap(e) }
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                return doubleTapProcessor.process { it.onDoubleTap(e) }
            }

            override fun onLongPress(e: MotionEvent) {
                val consumed = longPressProcessor.process { it.onLongPress(e) }
                if (consumed) longPressActive = true
            }
        },
    )

    fun dispatchTouchEvent(event: MotionEvent): Boolean {
        systemDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                mode = Mode.IDLE
                directionDecided = false
                longPressActive = false
            }
            MotionEvent.ACTION_MOVE -> if (!longPressActive) handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel(event)
        }
        return true
    }

    private fun handleMove(event: MotionEvent) {
        val dx = event.x - downX
        val dy = event.y - downY
        if (!directionDecided) {
            if (abs(dx) < touchSlop && abs(dy) < touchSlop) return
            directionDecided = true
            if (abs(dx) >= abs(dy)) {
                mode = Mode.LOCKED_HORIZONTAL
                horizontalScrollListener?.onScrollStart(PointF(downX, downY))
            } else {
                mode = Mode.LOCKED_VERTICAL
                val width = widthProvider().coerceAtLeast(1)
                verticalSide = if (downX < width / 2f) Side.LEFT else Side.RIGHT
                verticalScrollListener?.onScrollStart(verticalSide, PointF(downX, downY))
            }
        }
        when (mode) {
            Mode.LOCKED_HORIZONTAL -> horizontalScrollListener?.onScroll(dx)
            Mode.LOCKED_VERTICAL -> verticalScrollListener?.onScroll(verticalSide, dy)
            Mode.IDLE -> Unit
        }
    }

    private fun handleUp(event: MotionEvent) {
        if (longPressActive) {
            longPressProcessor.process { l -> l.onLongPressEnd(event); false }
            longPressActive = false
        }
        when (mode) {
            Mode.LOCKED_HORIZONTAL -> horizontalScrollListener?.onScrollStop(event.x - downX)
            Mode.LOCKED_VERTICAL -> verticalScrollListener?.onScrollStop(verticalSide)
            Mode.IDLE -> Unit
        }
        mode = Mode.IDLE
        directionDecided = false
    }

    private fun handleCancel(event: MotionEvent) {
        if (longPressActive) {
            longPressProcessor.process { l -> l.onLongPressEnd(event); false }
            longPressActive = false
        }
        when (mode) {
            Mode.LOCKED_HORIZONTAL -> horizontalScrollListener?.onCancel()
            Mode.LOCKED_VERTICAL -> verticalScrollListener?.onCancel()
            Mode.IDLE -> Unit
        }
        mode = Mode.IDLE
        directionDecided = false
    }
}
