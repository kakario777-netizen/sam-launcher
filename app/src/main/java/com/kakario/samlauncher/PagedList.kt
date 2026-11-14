package com.kakario.samlauncher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/**
 * 스크롤 없는 페이지 목록 (이잉크용).
 * 화면 높이에 들어가는 줄 수만큼 한 페이지에 담고, 줄 높이를 똑같이 나눠
 * 마지막 줄이 잘리지 않고 화면에 딱 맞게 채운다. 아래 ◀ 이전 · 1/3 · 다음 ▶.
 */
class PagedList(context: Context, private val minRowDp: Int = 76) : LinearLayout(context) {
    private val box = LinearLayout(context).apply { orientation = VERTICAL }
    private val prev = context.text("◀ 이전", 19f).apply { gravity = Gravity.CENTER }
    private val next = context.text("다음 ▶", 19f).apply { gravity = Gravity.CENTER }
    private val pageText = context.text("1/1", 19f).apply { gravity = Gravity.CENTER }
    private val nav: View

    private var rows: List<() -> View> = emptyList()
    private var perPage = 0
    private var page = 0

    init {
        orientation = VERTICAL
        addView(box, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        nav = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setTopLine(this)
            addView(prev, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(pageText, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(next, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(nav, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(64)))
        prev.setOnClickListener { go(page - 1) }
        next.setOnClickListener { go(page + 1) }
    }

    private fun setTopLine(v: View) {
        v.background = object : android.graphics.drawable.Drawable() {
            val p = android.graphics.Paint().apply { color = INK; strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f) }
            override fun draw(c: android.graphics.Canvas) { val y = p.strokeWidth / 2; c.drawLine(0f, y, bounds.width().toFloat(), y, p) }
            override fun setAlpha(a: Int) {}
            override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    /** 줄 목록 지정. keepPage = 지금 페이지 유지 (설정 값만 바뀌었을 때). */
    fun setRows(list: List<() -> View>, keepPage: Boolean = false) {
        rows = list
        if (!keepPage) page = 0
        render()
    }

    val pageCount: Int get() = if (perPage <= 0) 1 else maxOf(1, (rows.size + perPage - 1) / perPage)

    fun go(p: Int) {
        if (p < 0 || p >= pageCount || p == page) return
        page = p
        render()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val avail = h - context.dp(64)
        val n = maxOf(1, avail / context.dp(minRowDp))
        if (n != perPage) { perPage = n; post { render() } }
    }

    private fun render() {
        if (perPage <= 0) return
        page = page.coerceIn(0, pageCount - 1)
        box.removeAllViews()
        val start = page * perPage
        for (i in 0 until perPage) {
            // 빈 줄도 같은 높이로 채워 줄 높이가 페이지마다 같게
            val v = rows.getOrNull(start + i)?.invoke() ?: View(context)
            box.addView(v, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val many = pageCount > 1
        nav.visibility = if (many) VISIBLE else INVISIBLE
        pageText.text = "${page + 1}/$pageCount"
        prev.visibility = if (page > 0) VISIBLE else INVISIBLE
        next.visibility = if (page < pageCount - 1) VISIBLE else INVISIBLE
    }
}
