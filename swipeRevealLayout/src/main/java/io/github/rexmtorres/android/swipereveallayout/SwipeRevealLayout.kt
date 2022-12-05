/**
The MIT License (MIT)

Copyright (c) 2022 Rex Mag-uyon Torres

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package io.github.rexmtorres.android.swipereveallayout

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import mu.KotlinLogging
import kotlin.math.abs
import kotlin.math.min

class SwipeRevealLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    private val logger = KotlinLogging.logger {}

    companion object {
        const val DRAG_EDGE_LEFT = 0x1
        const val DRAG_EDGE_RIGHT = 0x1 shl 1
        const val DRAG_EDGE_TOP = 0x1 shl 2
        const val DRAG_EDGE_BOTTOM = 0x1 shl 3

        // These states are used only for ViewBindHelper
        internal const val STATE_CLOSE = 0
        internal const val STATE_CLOSING = 1
        internal const val STATE_OPEN = 2
        internal const val STATE_OPENING = 3
        internal const val STATE_DRAGGING = 4

        private const val DEFAULT_MIN_FLING_VELOCITY = 300 // dp per second
        private const val DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT = 1 // dp

        /**
         * The secondary view will be under the main view.
         */
        const val MODE_NORMAL = 0

        /**
         * The secondary view will stick the edge of the main view.
         */
        const val MODE_SAME_LEVEL = 1

        @Suppress("unused")
        fun getStateString(state: Int): String {
            return when (state) {
                STATE_CLOSE -> "state_close"
                STATE_CLOSING -> "state_closing"
                STATE_OPEN -> "state_open"
                STATE_OPENING -> "state_opening"
                STATE_DRAGGING -> "state_dragging"
                else -> "undefined"
            }
        }
    }

    //region Inner classes/interfaces

    interface DragStateChangeListener {
        fun onDragStateChanged(state: Int)
    }

    /**
     * Listener for monitoring events about swipe layout.
     */
    interface SwipeListener {
        /**
         * Called when the main view becomes completely closed.
         */
        fun onClosed(view: SwipeRevealLayout?)

        /**
         * Called when the main view becomes completely opened.
         */
        fun onOpened(view: SwipeRevealLayout?)

        /**
         * Called when the main view's position changes.
         * @param slideOffset The new offset of the main view within its range, from 0-1
         */
        fun onSlide(view: SwipeRevealLayout?, slideOffset: Float)
    }

    /**
     * No-op stub for [SwipeListener]. If you only want ot implement a subset
     * of the listener methods, you can extend this instead of implement the full interface.
     */
    @Suppress("unused")
    class SimpleSwipeListener : SwipeListener {
        override fun onClosed(view: SwipeRevealLayout?) {}
        override fun onOpened(view: SwipeRevealLayout?) {}
        override fun onSlide(view: SwipeRevealLayout?, slideOffset: Float) {}
    }

    //endregion Inner classes/interfaces

    //region Public properties

    /**
     * @return true if layout is fully opened, false otherwise.
     */
    @Suppress("unused")
    val isOpened: Boolean
        get() = (state == STATE_OPEN)

    /**
     * @return true if layout is fully closed, false otherwise.
     */
    @Suppress("unused")
    val isClosed: Boolean
        get() = (state == STATE_CLOSE)

    /**
     * @return `true` if the drag/swipe motion is currently locked; `false` otherwise.
     */
    @Volatile
    var isDragLocked = false
        private set

    /**
     * Get/set the minimum fling velocity, in dp per second, to cause the layout to open/close.
     */
    var minFlingVelocity = DEFAULT_MIN_FLING_VELOCITY

    /**
     * Get/set the edge where the layout can be dragged from.
     *
     * Value can be one of these::
     *
     *  * [DRAG_EDGE_LEFT]
     *  * [DRAG_EDGE_TOP]
     *  * [DRAG_EDGE_RIGHT]
     *  * [DRAG_EDGE_BOTTOM]
     *
     */
    var dragEdge = DRAG_EDGE_LEFT

    //endregion Public properties

    //region Private properties

    /**
     * Main view is the view which is shown when the layout is closed.
     */
    private lateinit var mainView: View

    /**
     * Secondary view is the view which is shown when the layout is opened.
     */
    private lateinit var secondaryView: View

    /**
     * The rectangle position of the main view when the layout is closed.
     */
    private val rectMainClose = Rect()

    /**
     * The rectangle position of the main view when the layout is opened.
     */
    private val rectMainOpen = Rect()

    /**
     * The rectangle position of the secondary view when the layout is closed.
     */
    private val rectSecClose = Rect()

    /**
     * The rectangle position of the secondary view when the layout is opened.
     */
    private val rectSecOpen = Rect()

    /**
     * The minimum distance (px) to the closest drag edge that the SwipeRevealLayout
     * will disallow the parent to intercept touch event.
     */
    private var minDistRequestDisallowParent = 0

    private var areRectsInitialized = false
    private var isOpenBeforeInit = false

    @Volatile
    private var isAborted = false

    @Volatile
    private var isScrolling = false

    @Volatile
    private var touchedDown = false

    private var state = STATE_CLOSE
    private var mode = MODE_NORMAL
    private var lastMainLeft = 0
    private var lastMainTop = 0

    private var dragDist = 0f
    private var prevX = -1f
    private var prevY = -1f

    private lateinit var dragHelper: ViewDragHelper
    private val gestureDetector: GestureDetectorCompat

    // only used for ViewBindHelper
    private var dragStateChangeListener: DragStateChangeListener? = null
    private var swipeListener: SwipeListener? = null
    private var onLayoutCount = 0

    private val mainOpenLeft: Int
        get() = when (dragEdge) {
            DRAG_EDGE_LEFT -> rectMainClose.start + secondaryView.width
            DRAG_EDGE_RIGHT -> rectMainClose.start - secondaryView.width
            DRAG_EDGE_TOP -> rectMainClose.start
            DRAG_EDGE_BOTTOM -> rectMainClose.start
            else -> 0
        }

    private val mainOpenTop: Int
        get() = when (dragEdge) {
            DRAG_EDGE_LEFT -> rectMainClose.top
            DRAG_EDGE_RIGHT -> rectMainClose.top
            DRAG_EDGE_TOP -> rectMainClose.top + secondaryView.height
            DRAG_EDGE_BOTTOM -> rectMainClose.top - secondaryView.height
            else -> 0
        }

    private val secOpenLeft: Int
        get() {
            if ((mode == MODE_NORMAL || dragEdge == DRAG_EDGE_BOTTOM) || dragEdge == DRAG_EDGE_TOP) {
                return rectSecClose.start
            }

            return if (dragEdge == DRAG_EDGE_LEFT) {
                rectSecClose.start + secondaryView.width
            } else {
                rectSecClose.start - secondaryView.width
            }
        }

    private val secOpenTop: Int
        get() {
            if ((mode == MODE_NORMAL) || dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT) {
                return rectSecClose.top
            }

            return if (dragEdge == DRAG_EDGE_TOP) {
                rectSecClose.top + secondaryView.height
            } else {
                rectSecClose.top - secondaryView.height
            }
        }

    private val gestureListener: GestureDetector.OnGestureListener =
        object : SimpleOnGestureListener() {
            var hasDisallowed = false

            override fun onDown(e: MotionEvent): Boolean {
                isScrolling = false
                hasDisallowed = false

                return true
            }

            override fun onFling(
                e1: MotionEvent,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                isScrolling = true

                return false
            }

            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                isScrolling = true

                if (parent != null) {
                    val shouldDisallow: Boolean

                    if (!hasDisallowed) {
                        shouldDisallow = distToClosestEdge >= minDistRequestDisallowParent

                        if (shouldDisallow) {
                            hasDisallowed = true
                        }
                    } else {
                        shouldDisallow = true
                    }

                    // disallow parent to intercept touch event so that the layout will work
                    // properly on RecyclerView or view that handles scroll gesture.
                    parent.requestDisallowInterceptTouchEvent(shouldDisallow)
                }

                return false
            }
        }

    private val distToClosestEdge: Int
        get() = when (dragEdge) {
            DRAG_EDGE_LEFT -> {
                val pivotRight = rectMainClose.start + secondaryView.width

                (mainView.start - rectMainClose.start)
                    .coerceAtMost(pivotRight - mainView.start)
            }

            DRAG_EDGE_RIGHT -> {
                val pivotLeft = rectMainClose.end - secondaryView.width

                (mainView.end - pivotLeft)
                    .coerceAtMost(rectMainClose.end - mainView.end)
            }

            DRAG_EDGE_TOP -> {
                val pivotBottom = rectMainClose.top + secondaryView.height

                (mainView.bottom - pivotBottom)
                    .coerceAtMost(pivotBottom - mainView.top)
            }

            DRAG_EDGE_BOTTOM -> {
                val pivotTop = rectMainClose.bottom - secondaryView.height

                (rectMainClose.bottom - mainView.bottom)
                    .coerceAtMost(mainView.bottom - pivotTop)
            }

            else -> 0
        }

    private val halfwayPivotHorizontal: Int
        get() = if (dragEdge == DRAG_EDGE_LEFT) {
            rectMainClose.start + secondaryView.width / 2
        } else {
            rectMainClose.end - secondaryView.width / 2
        }

    private val halfwayPivotVertical: Int
        get() = if (dragEdge == DRAG_EDGE_TOP) {
            rectMainClose.top + secondaryView.height / 2
        } else {
            rectMainClose.bottom - secondaryView.height / 2
        }

    private val dragHelperCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {
        private val slideOffset: Float
            get() = when (dragEdge) {
                DRAG_EDGE_LEFT -> (mainView.start - rectMainClose.start).toFloat() / secondaryView.width
                DRAG_EDGE_RIGHT -> (rectMainClose.start - mainView.start).toFloat() / secondaryView.width
                DRAG_EDGE_TOP -> (mainView.top - rectMainClose.top).toFloat() / secondaryView.height
                DRAG_EDGE_BOTTOM -> (rectMainClose.top - mainView.top).toFloat() / secondaryView.height
                else -> 0f
            }

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            isAborted = false

            if (isDragLocked) {
                return false
            }

            dragHelper.captureChildView(mainView, pointerId)

            return false
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return when (dragEdge) {
                DRAG_EDGE_TOP -> {
                    (top.coerceAtMost(rectMainClose.top + secondaryView.height))
                        .coerceAtLeast(rectMainClose.top)
                }

                DRAG_EDGE_BOTTOM -> {
                    (top.coerceAtMost(rectMainClose.top))
                        .coerceAtLeast(rectMainClose.top - secondaryView.height)
                }

                else -> child.top
            }
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return when (dragEdge) {
                DRAG_EDGE_RIGHT -> {
                    (left.coerceAtMost(rectMainClose.start))
                        .coerceAtLeast(rectMainClose.start - secondaryView.width)
                }

                DRAG_EDGE_LEFT -> {
                    (left.coerceAtMost(rectMainClose.start + secondaryView.width))
                        .coerceAtLeast(rectMainClose.start)
                }

                else -> child.start
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            val velRightExceeded: Boolean = pxToDp(xvel.toInt()) >= minFlingVelocity
            val velLeftExceeded: Boolean = pxToDp(xvel.toInt()) <= -minFlingVelocity
            val velUpExceeded: Boolean = pxToDp(yvel.toInt()) <= -minFlingVelocity
            val velDownExceeded: Boolean = pxToDp(yvel.toInt()) >= minFlingVelocity
            val pivotHorizontal: Int = halfwayPivotHorizontal
            val pivotVertical: Int = halfwayPivotVertical

            when (dragEdge) {
                DRAG_EDGE_RIGHT -> if (velRightExceeded) {
                    close(true)
                } else if (velLeftExceeded) {
                    open(true)
                } else {
                    if (mainView.end < pivotHorizontal) {
                        open(true)
                    } else {
                        close(true)
                    }
                }

                DRAG_EDGE_LEFT -> if (velRightExceeded) {
                    open(true)
                } else if (velLeftExceeded) {
                    close(true)
                } else {
                    if (mainView.start < pivotHorizontal) {
                        close(true)
                    } else {
                        open(true)
                    }
                }

                DRAG_EDGE_TOP -> if (velUpExceeded) {
                    close(true)
                } else if (velDownExceeded) {
                    open(true)
                } else {
                    if (mainView.top < pivotVertical) {
                        close(true)
                    } else {
                        open(true)
                    }
                }

                DRAG_EDGE_BOTTOM -> if (velUpExceeded) {
                    open(true)
                } else if (velDownExceeded) {
                    close(true)
                } else {
                    if (mainView.bottom < pivotVertical) {
                        open(true)
                    } else {
                        close(true)
                    }
                }
            }
        }

        override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
            super.onEdgeDragStarted(edgeFlags, pointerId)

            if (isDragLocked) {
                return
            }

            val edgeStartLeft = (dragEdge == DRAG_EDGE_RIGHT) &&
                    (edgeFlags == ViewDragHelper.EDGE_LEFT)

            val edgeStartRight = (dragEdge == DRAG_EDGE_LEFT) &&
                    (edgeFlags == ViewDragHelper.EDGE_RIGHT)

            val edgeStartTop = (dragEdge == DRAG_EDGE_BOTTOM) &&
                    (edgeFlags == ViewDragHelper.EDGE_TOP)

            val edgeStartBottom = (dragEdge == DRAG_EDGE_TOP) &&
                    (edgeFlags == ViewDragHelper.EDGE_BOTTOM)

            if (edgeStartLeft || edgeStartRight || edgeStartTop || edgeStartBottom) {
                dragHelper.captureChildView(mainView, pointerId)
            }
        }

        override fun onViewPositionChanged(
            changedView: View,
            left: Int,
            top: Int,
            dx: Int,
            dy: Int
        ) {
            super.onViewPositionChanged(changedView, left, top, dx, dy)

            if (mode == MODE_SAME_LEVEL) {
                if (dragEdge == DRAG_EDGE_LEFT || dragEdge == DRAG_EDGE_RIGHT) {
                    secondaryView.offsetLeftAndRight(dx)
                } else {
                    secondaryView.offsetTopAndBottom(dy)
                }
            }

            val isMoved = (mainView.start != lastMainLeft) || (mainView.top != lastMainTop)

            if (swipeListener != null && isMoved) {
                if (mainView.start == rectMainClose.start && mainView.top == rectMainClose.top) {
                    swipeListener!!.onClosed(this@SwipeRevealLayout)
                } else if (mainView.start == rectMainOpen.start && mainView.top == rectMainOpen.top) {
                    swipeListener!!.onOpened(this@SwipeRevealLayout)
                } else {
                    swipeListener!!.onSlide(this@SwipeRevealLayout, slideOffset)
                }
            }

            lastMainLeft = mainView.start
            lastMainTop = mainView.top

            ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)

            val prevState = this@SwipeRevealLayout.state

            when (state) {
                ViewDragHelper.STATE_DRAGGING -> this@SwipeRevealLayout.state = STATE_DRAGGING
                // drag edge is left or right
                ViewDragHelper.STATE_IDLE -> this@SwipeRevealLayout.state = if (
                    (dragEdge == DRAG_EDGE_LEFT) || (dragEdge == DRAG_EDGE_RIGHT)
                ) {
                    if (mainView.start == rectMainClose.start) {
                        STATE_CLOSE
                    } else {
                        STATE_OPEN
                    }
                } else {
                    if (mainView.top == rectMainClose.top) {
                        STATE_CLOSE
                    } else {
                        STATE_OPEN
                    }
                }
            }

            if ((dragStateChangeListener != null && !isAborted) && prevState != this@SwipeRevealLayout.state) {
                dragStateChangeListener!!.onDragStateChanged(this@SwipeRevealLayout.state)
            }
        }
    }

    //endregion Private properties

    init {
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(
                attrs,
                R.styleable.SwipeRevealLayout,
                0, 0
            )
            dragEdge = a.getInteger(R.styleable.SwipeRevealLayout_dragEdge, DRAG_EDGE_LEFT)
            minFlingVelocity = a.getInteger(
                R.styleable.SwipeRevealLayout_flingVelocity,
                DEFAULT_MIN_FLING_VELOCITY
            )
            mode = a.getInteger(R.styleable.SwipeRevealLayout_mode, MODE_NORMAL)
            minDistRequestDisallowParent = a.getDimensionPixelSize(
                R.styleable.SwipeRevealLayout_minDistRequestDisallowParent,
                dpToPx(DEFAULT_MIN_DIST_REQUEST_DISALLOW_PARENT)
            )
        }

        dragHelper = ViewDragHelper.create(this, 1.0f, dragHelperCallback)
        dragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_ALL)
        gestureDetector = GestureDetectorCompat(context, gestureListener)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        dragHelper.processTouchEvent(event)

        if (isClickable) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchedDown = true
                }

                MotionEvent.ACTION_UP -> {
                    touchedDown = false
                    performClick()
                }
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        // Nothing to do, really...
        logger.debug { "performClick" }
        return super.performClick()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isDragLocked) {
            return super.onInterceptTouchEvent(ev)
        }

        dragHelper.processTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)

        accumulateDragDist(ev)

        val couldBecomeClick = couldBecomeClick(ev)
        val settling = dragHelper.viewDragState == ViewDragHelper.STATE_SETTLING
        val idleAfterScrolled =
            (dragHelper.viewDragState == ViewDragHelper.STATE_IDLE && isScrolling)

        // must be placed as the last statement
        prevX = ev.x
        prevY = ev.y

        // return true => intercept, cannot trigger onClick event
        return !couldBecomeClick && (settling || idleAfterScrolled)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // get views
        if (childCount >= 2) {
            secondaryView = getChildAt(0)
            mainView = getChildAt(1)
        } else if (childCount == 1) {
            mainView = getChildAt(0)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        logger.debug { "onLayout: changed = $changed" }
        logger.debug { "onLayout: left = $l" }
        logger.debug { "onLayout: top = $t" }
        logger.debug { "onLayout: right = $r" }
        logger.debug { "onLayout: bottom = $b" }

        isAborted = false

        for (index in 0 until childCount) {
            val child = getChildAt(index)

            val minLeft = paddingStart // l.coerceAtLeast(0) // paddingStart
            val maxRight =
                (r - paddingEnd - l).coerceAtLeast(0) // (r - l).coerceAtLeast(0) // (r - paddingEnd - l).coerceAtLeast(0)
            val minTop = paddingTop // t.coerceAtLeast(0) // paddingTop
            val maxBottom =
                (b - paddingBottom - t).coerceAtLeast(0) // (b - t).coerceAtLeast(0) // (b - paddingBottom - t).coerceAtLeast(0)

            logger.debug { "onLayout: minLeft = $minLeft" }
            logger.debug { "onLayout: maxRight = $maxRight" }
            logger.debug { "onLayout: minTop = $minTop" }
            logger.debug { "onLayout: maxBottom = $maxBottom" }

            var measuredChildHeight = child.measuredHeight
            var measuredChildWidth = child.measuredWidth

            // need to take account if child size is match_parent
            val childParams = child.layoutParams

            var matchParentHeight = false
            var matchParentWidth = false

            if (childParams != null) {
                matchParentHeight = (childParams.height == LayoutParams.MATCH_PARENT)
                matchParentWidth = (childParams.width == LayoutParams.MATCH_PARENT)
            }

            if (matchParentHeight) {
                measuredChildHeight = maxBottom - minTop
                childParams!!.height = measuredChildHeight
            }

            if (matchParentWidth) {
                measuredChildWidth = maxRight - minLeft
                childParams!!.width = measuredChildWidth
            }

            var left = 0
            var right = 0
            var top = 0
            var bottom = 0

            when (dragEdge) {
                DRAG_EDGE_RIGHT -> {
                    left = (r - measuredChildWidth - paddingEnd - l).coerceAtLeast(minLeft)
                    top = paddingTop.coerceAtMost(maxBottom)
                    right = (r - l).coerceAtLeast(minLeft)
                    bottom = (measuredChildHeight + paddingTop).coerceAtMost(maxBottom)
                }

                DRAG_EDGE_LEFT -> {
                    left = paddingStart.coerceAtMost(maxRight)
                    top = paddingTop.coerceAtMost(maxBottom)
                    right = (measuredChildWidth + paddingStart).coerceAtMost(maxRight)
                    bottom = (measuredChildHeight + paddingTop).coerceAtMost(maxBottom)
                }

                DRAG_EDGE_TOP -> {
                    left = paddingStart.coerceAtMost(maxRight)
                    top = paddingTop.coerceAtMost(maxBottom)
                    right = (measuredChildWidth + paddingStart).coerceAtMost(maxRight)
                    bottom = (measuredChildHeight + paddingTop).coerceAtMost(maxBottom)
                }

                DRAG_EDGE_BOTTOM -> {
                    left = paddingStart.coerceAtMost(maxRight)
                    top = (b - measuredChildHeight - paddingBottom - t).coerceAtLeast(minTop)
                    right = (measuredChildWidth + paddingStart).coerceAtMost(maxRight)
                    bottom = (b - paddingBottom - t).coerceAtLeast(minTop)
                }
            }

            logger.debug { "onLayout: child.layout(l: $left, t: $top, r: $right, b: $bottom)" }
            child.layout(left, top, right, bottom)
        }

        // taking account offset when mode is SAME_LEVEL
        if (mode == MODE_SAME_LEVEL) {
            when (dragEdge) {
                DRAG_EDGE_LEFT -> secondaryView.offsetLeftAndRight(-secondaryView.width)
                DRAG_EDGE_RIGHT -> secondaryView.offsetLeftAndRight(secondaryView.width)
                DRAG_EDGE_TOP -> secondaryView.offsetTopAndBottom(-secondaryView.height)
                DRAG_EDGE_BOTTOM -> secondaryView.offsetTopAndBottom(secondaryView.height)
            }
        }

        initRects()

        if (isOpenBeforeInit) {
            open(false)
        } else {
            close(false)
        }

        lastMainLeft = mainView.start
        lastMainTop = mainView.top
        onLayoutCount++
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var widthMeasureSpec2 = widthMeasureSpec
        var heightMeasureSpec2 = heightMeasureSpec

        if (childCount < 2) {
            throw RuntimeException("Layout must have two children")
        }

        val params = layoutParams

        val widthMode = MeasureSpec.getMode(widthMeasureSpec2)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec2)

        @Suppress("KotlinConstantConditions")
        logger.debug {
            "onMeasure: widthMode = ${
                when (widthMode) {
                    MeasureSpec.UNSPECIFIED -> "UNSPECIFIED ($widthMode)"
                    MeasureSpec.EXACTLY -> "EXACTLY ($widthMode)"
                    MeasureSpec.AT_MOST -> "AT_MOST ($widthMode)"
                    else -> "undefined ($widthMode)"
                }
            }"
        }

        @Suppress("KotlinConstantConditions")
        logger.debug {
            "onMeasure: heightMode = ${
                when (heightMode) {
                    MeasureSpec.UNSPECIFIED -> "UNSPECIFIED ($heightMode)"
                    MeasureSpec.EXACTLY -> "EXACTLY ($heightMode)"
                    MeasureSpec.AT_MOST -> "AT_MOST ($heightMode)"
                    else -> "undefined ($heightMode)"
                }
            }"
        }

        var desiredWidth = 0
        var desiredHeight = 0

        // first find the largest child
        logger.debug { "onMeasure: ++++++++++++++++++++++++++++++++++++++++++" }
        logger.debug { "onMeasure: Finding largest child..." }

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            logger.debug { "onMeasure: child = $child" }

            measureChild(child, widthMeasureSpec2, heightMeasureSpec2)

            val measuredWidth = child.measuredWidth
            val measuredHeight = child.measuredHeight

            logger.debug { "onMeasure:     measuredWidth = $measuredWidth" }
            logger.debug { "onMeasure:     measuredHeight = $measuredHeight" }

            desiredWidth = measuredWidth.coerceAtLeast(desiredWidth)
            desiredHeight = measuredHeight.coerceAtLeast(desiredHeight)

            logger.debug { "onMeasure:     desiredWidth = $desiredWidth" }
            logger.debug { "onMeasure:     desiredHeight = $desiredHeight" }
        }

        logger.debug { "onMeasure: Largest:" }
        logger.debug { "onMeasure:     desiredWidth = $desiredWidth" }
        logger.debug { "onMeasure:     desiredHeight = $desiredHeight" }
        logger.debug { "onMeasure: ------------------------------------------" }

        // create new measure spec using the largest child width
        logger.debug { "onMeasure: ++++++++++++++++++++++++++++++++++++++++++" }
        logger.debug { "onMeasure: Measuring children based on largest child..." }

        widthMeasureSpec2 = MeasureSpec.makeMeasureSpec(desiredWidth, widthMode)
        heightMeasureSpec2 = MeasureSpec.makeMeasureSpec(desiredHeight, heightMode)

        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec2)
        val measuredHeight = MeasureSpec.getSize(heightMeasureSpec2)

        logger.debug { "onMeasure:     measuredWidth = $measuredWidth" }
        logger.debug { "onMeasure:     measuredHeight = $measuredHeight" }

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childParams = child.layoutParams

            logger.debug { "onMeasure:     child = $child" }

            if (childParams != null) {
                if (childParams.height == LayoutParams.MATCH_PARENT) {
                    child.minimumHeight = measuredHeight
                }

                if (childParams.width == LayoutParams.MATCH_PARENT) {
                    child.minimumWidth = measuredWidth
                }
            }

            measureChild(child, widthMeasureSpec2, heightMeasureSpec2)

            desiredWidth = child.measuredWidth.coerceAtLeast(desiredWidth)
            desiredHeight = child.measuredHeight.coerceAtLeast(desiredHeight)

            logger.debug { "onMeasure:         desiredWidth = $desiredWidth" }
            logger.debug { "onMeasure:         desiredHeight = $desiredHeight" }
        }

        logger.debug { "onMeasure:     paddingLeft = $paddingStart" }
        logger.debug { "onMeasure:     paddingRight = $paddingEnd" }
        logger.debug { "onMeasure:     paddingTop = $paddingTop" }
        logger.debug { "onMeasure:     paddingBottom = $paddingBottom" }

        // adjust desired width
        if (widthMode == MeasureSpec.EXACTLY) {
            desiredWidth = measuredWidth
        } else {
            if (params.width == LayoutParams.MATCH_PARENT) {
                desiredWidth = measuredWidth
            }

            if (widthMode == MeasureSpec.AT_MOST) {
                desiredWidth = min(desiredWidth, measuredWidth)
            }
        }

        // adjust desired height
        if (heightMode == MeasureSpec.EXACTLY) {
            desiredHeight = measuredHeight
        } else {
            if (params.height == LayoutParams.MATCH_PARENT) {
                desiredHeight = measuredHeight
            }

            if (heightMode == MeasureSpec.AT_MOST) {
                desiredHeight = min(desiredHeight, measuredHeight)
            }
        }

        logger.debug { "onMeasure:     After adjustment:" }
        logger.debug { "onMeasure:         desiredWidth = $desiredWidth" }
        logger.debug { "onMeasure:         desiredHeight = $desiredHeight" }

        // taking accounts of padding
        desiredWidth += paddingStart + paddingEnd
        desiredHeight += paddingTop + paddingBottom

        logger.debug { "onMeasure:     With padding:" }
        logger.debug { "onMeasure:         desiredWidth = $desiredWidth" }
        logger.debug { "onMeasure:         desiredHeight = $desiredHeight" }

        setMeasuredDimension(desiredWidth, desiredHeight)
        logger.debug { "onMeasure: ------------------------------------------" }
    }

    override fun computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    /**
     * Open the panel to show the secondary view
     * @param animation true to animate the open motion. [SwipeListener] won't be
     * called if is animation is false.
     */
    fun open(animation: Boolean) {
        isOpenBeforeInit = true
        isAborted = false

        if (animation) {
            state = STATE_OPENING
            dragHelper.smoothSlideViewTo(mainView, rectMainOpen.start, rectMainOpen.top)

            if (dragStateChangeListener != null) {
                dragStateChangeListener!!.onDragStateChanged(state)
            }
        } else {
            state = STATE_OPEN

            dragHelper.abort()

            mainView.layout(
                rectMainOpen.start,
                rectMainOpen.top,
                rectMainOpen.end,
                rectMainOpen.bottom
            )

            secondaryView.layout(
                rectSecOpen.start,
                rectSecOpen.top,
                rectSecOpen.end,
                rectSecOpen.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
    }

    /**
     * Close the panel to hide the secondary view
     * @param animation true to animate the close motion. [SwipeListener] won't be
     * called if is animation is false.
     */
    fun close(animation: Boolean) {
        isOpenBeforeInit = false
        isAborted = false

        if (animation) {
            state = STATE_CLOSING
            dragHelper.smoothSlideViewTo(mainView, rectMainClose.start, rectMainClose.top)

            if (dragStateChangeListener != null) {
                dragStateChangeListener!!.onDragStateChanged(state)
            }
        } else {
            state = STATE_CLOSE
            dragHelper.abort()
            mainView.layout(
                rectMainClose.start,
                rectMainClose.top,
                rectMainClose.end,
                rectMainClose.bottom
            )
            secondaryView.layout(
                rectSecClose.start,
                rectSecClose.top,
                rectSecClose.end,
                rectSecClose.bottom
            )
        }

        ViewCompat.postInvalidateOnAnimation(this@SwipeRevealLayout)
    }

    @Suppress("unused")
    fun setSwipeListener(listener: SwipeListener?) {
        swipeListener = listener
    }

    /**
     * @param lock if set to true, the user cannot drag/swipe the layout.
     */
    fun setLockDrag(lock: Boolean) {
        isDragLocked = lock
    }

    /**
     * In RecyclerView/ListView, onLayout should be called 2 times to display children views correctly.
     * This method check if it've already called onLayout two times.
     * @return true if you should call [.requestLayout].
     */
    fun shouldRequestLayout(): Boolean {
        return onLayoutCount < 2
    }

    /** Only used for [ViewBinderHelper]  */
    internal fun setDragStateChangeListener(listener: DragStateChangeListener?) {
        dragStateChangeListener = listener
    }

    /** Abort current motion in progress. Only used for [ViewBinderHelper]  */
    internal fun abort() {
        isAborted = true
        dragHelper.abort()
    }

    private fun initRects() {
        if (areRectsInitialized) {
            return
        }

        areRectsInitialized = true

        // close position of main view
        rectMainClose.set(
            mainView.start,
            mainView.top,
            mainView.end,
            mainView.bottom
        )
        logger.debug { "initRects: rectMainClose = $rectMainClose" }

        // close position of secondary view
        rectSecClose.set(
            secondaryView.start,
            secondaryView.top,
            secondaryView.end,
            secondaryView.bottom
        )
        logger.debug { "initRects: rectSecClose = $rectSecClose" }

        // open position of the main view
        rectMainOpen.set(
            mainOpenLeft,
            mainOpenTop,
            mainOpenLeft + mainView.width,
            mainOpenTop + mainView.height
        )
        logger.debug { "initRects: rectMainOpen = $rectMainOpen" }

        // open position of the secondary view
        rectSecOpen.set(
            secOpenLeft,
            secOpenTop,
            secOpenLeft + secondaryView.width,
            secOpenTop + secondaryView.height
        )
        logger.debug { "initRects: rectSecOpen = $rectSecOpen" }
    }

    private fun couldBecomeClick(ev: MotionEvent): Boolean =
        isInMainView(ev) && !shouldInitiateADrag()

    private fun isInMainView(ev: MotionEvent): Boolean {
        val x = ev.x
        val y = ev.y
        val withinVertical = mainView.top <= y && y <= mainView.bottom
        val withinHorizontal = mainView.start <= x && x <= mainView.end
        return withinVertical && withinHorizontal
    }

    private fun shouldInitiateADrag(): Boolean = dragDist >= dragHelper.touchSlop.toFloat()

    private fun accumulateDragDist(ev: MotionEvent) {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            dragDist = 0f
            return
        }

        val dragHorizontally = (dragEdge == DRAG_EDGE_LEFT) || (dragEdge == DRAG_EDGE_RIGHT)

        val dragged: Float = if (dragHorizontally) {
            abs(ev.x - prevX)
        } else {
            abs(ev.y - prevY)
        }

        dragDist += dragged
    }

    private fun pxToDp(px: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (px / (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    @Suppress("SameParameterValue")
    private fun dpToPx(dp: Int): Int {
        val resources = context.resources
        val metrics = resources.displayMetrics
        return (dp * (metrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)).toInt()
    }

    private val View.start: Int
        get() = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            right
        } else {
            left
        }

    private val View.end: Int
        get() = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            left
        } else {
            right
        }

    private val Rect.start: Int
        get() = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            right
        } else {
            left
        }

    private val Rect.end: Int
        get() = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            left
        } else {
            right
        }
}
