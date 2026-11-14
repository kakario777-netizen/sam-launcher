package com.kakario.samlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** 칸 하나: 아이콘 + 이름 (이름은 칸 밖으로 넘치지 않게 최대 2줄, 넘치면 …). */
class Cell(context: Context) : LinearLayout(context) {
    val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    val label = TextView(context).apply {
        setTextColor(INK)
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    var marked = false
        set(v) { field = v; invalidate() }
    private val box = Paint().apply { color = INK; style = Paint.Style.STROKE }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setWillNotDraw(false)
        addView(icon)
        addView(label)
    }

    fun bind(d: Drawable?, name: String?) {
        icon.setImageDrawable(d)
        icon.visibility = if (d == null) INVISIBLE else VISIBLE
        label.text = name ?: ""
    }

    /** 칸 크기에 맞춰 아이콘·글자 크기를 정한다. */
    fun fit(w: Int, h: Int) {
        val s = (minOf(w, (h * 0.75f).toInt()) * 0.48f).toInt().coerceAtMost(context.dp(110))
        (icon.layoutParams as LayoutParams).apply { width = s; height = s; bottomMargin = (s * 0.18f).toInt() }
        val sp = when {
            w < context.dp(110) -> 14f
            w < context.dp(150) -> 16f
            else -> 18f
        }
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        val pad = context.dp(6)
        label.setPadding(pad, 0, pad, 0)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (marked) {
            // 옮기는 중인 칸: 굵은 테두리
            val t = context.dp(4).toFloat()
            box.strokeWidth = t
            canvas.drawRect(t, t, width - t, height - t, box)
        }
    }
}

/**
 * 기본 런처와 같은 선 격자. 칸 사이 얇은 선, 위·아래 테두리.
 * 좌우로 밀면 페이지 넘김.
 */
@SuppressLint("ViewConstructor")
class CellGrid(context: Context, private val listener: Listener) : ViewGroup(context) {
    interface Listener {
        fun onCellClick(i: Int)
        fun onCellLongClick(i: Int)
        fun onSwipe(next: Boolean)
    }

    var cols = 4; private set
    var rows = 5; private set
    val cells = ArrayList<Cell>()
    private val line = Paint().apply { color = INK; strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f) }

    init { setWillNotDraw(false) }

    fun setSize(c: Int, r: Int) {
        if (c == cols && r == rows && cells.size == c * r) return
        cols = c; rows = r
        removeAllViews(); cells.clear()
        repeat(c * r) { i ->
            val cell = Cell(context)
            cell.setOnClickListener { listener.onCellClick(i) }
            cell.setOnLongClickListener { listener.onCellLongClick(i); true }
            cells.add(cell); addView(cell)
        }
        requestLayout()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        setMeasuredDimension(w, h)
        val cw = w / cols; val ch = h / rows
        for (c in cells) {
            c.fit(cw, ch)
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = width / cols; val ch = height / rows
        cells.forEachIndexed { i, c ->
            val x = (i % cols) * cw; val y = (i / cols) * ch
            c.layout(x, y, x + cw, y + ch)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val cw = width / cols; val ch = height / rows
        val half = line.strokeWidth / 2
        canvas.drawLine(0f, half, width.toFloat(), half, line)
        for (r in 1 until rows) canvas.drawLine(0f, (r * ch).toFloat(), width.toFloat(), (r * ch).toFloat(), line)
        for (c in 1 until cols) canvas.drawLine((c * cw).toFloat(), 0f, (c * cw).toFloat(), (rows * ch).toFloat(), line)
        canvas.drawLine(0f, (rows * ch).toFloat() - half, width.toFloat(), (rows * ch).toFloat() - half, line)
    }

    // ── 좌우 밀기 ──
    private var downX = 0f; private var downY = 0f; private var swiping = false
    private val slop = context.dp(60)

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiping = false }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) { swiping = true; return true }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_UP -> {
                val dx = e.x - downX
                if (abs(dx) > slop) listener.onSwipe(next = dx < 0)
                swiping = false
            }
        }
        return true
    }
}
