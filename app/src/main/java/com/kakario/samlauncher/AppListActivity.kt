package com.kakario.samlauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

/** 전체 앱 목록 / 숨긴 앱 목록. 선과 글자만. */
class AppListActivity : Activity() {
    private lateinit var store: Store
    private lateinit var apps: Apps
    private lateinit var list: PagedList
    private var hiddenMode = false
    private var pickSlot = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        apps = Apps(this)
        hiddenMode = intent.getBooleanExtra(EXTRA_HIDDEN, false)
        pickSlot = intent.getIntExtra(EXTRA_PICK_SLOT, -1)
        val title = when {
            pickSlot >= 0 -> "이 칸에 넣을 앱"
            hiddenMode -> "숨긴 앱"
            else -> "전체 앱"
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAPER); fitsSystemWindows = true }
        root.addView(header(this, title) { finish() })
        list = PagedList(this)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        fill()
    }

    private fun fill(keepPage: Boolean = false) {
        val all = apps.installed()
        val hidden = store.hidden
        val slots = store.slots
        val shown = if (hiddenMode) all.filter { it.key in hidden } else all
        if (shown.isEmpty()) {
            list.setRows(listOf({ text(if (hiddenMode) "숨긴 앱이 없어요" else "앱이 없어요", 18f).apply {
                setPadding(dp(24), 0, dp(24), 0); gravity = Gravity.CENTER_VERTICAL
            } }))
            return
        }
        list.setRows(shown.map { a -> { row(a, hidden, slots) } }, keepPage)
    }

    private fun row(a: AppEntry, hidden: Set<String>, slots: List<String>): View {
        val row = LineRow(this).apply { dashed = true }
        val ic = ImageView(this).apply { setImageDrawable(apps.icon(a)); scaleType = ImageView.ScaleType.FIT_CENTER }
        row.addView(ic, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(text(a.label, 20f).oneLine().apply { setPadding(dp(20), 0, 0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        when {
            pickSlot >= 0 -> {
                if (a.key in hidden) row.addView(text("숨김", 15f))
                else if (a.key in slots) row.addView(text("홈에 있음", 15f))
                row.setOnClickListener { putInSlot(a) }
            }
            hiddenMode -> {
                row.addView(text("되돌리기", 17f, bold = true))
                row.setOnClickListener { unhide(a) }
            }
            else -> {
                if (a.key in hidden) row.addView(text("숨김", 15f))
                row.setOnClickListener { openApp(a) }
                row.setOnLongClickListener { menu(a); true }
            }
        }
        return row
    }

    /** 고른 앱을 홈의 그 칸에 넣는다 (다른 칸에 있었으면 옮기고, 숨김이면 풀고). */
    private fun putInSlot(a: AppEntry) {
        val slots = store.slots
        val old = slots.indexOf(a.key)
        if (old >= 0) slots[old] = ""
        while (slots.size <= pickSlot) slots.add("")
        slots[pickSlot] = a.key
        store.slots = slots
        store.hidden = store.hidden.apply { remove(a.key) }
        finish()
    }

    private fun unhide(a: AppEntry) {
        store.hidden = store.hidden.apply { remove(a.key) }
        val slots = store.slots
        if (a.key !in slots) {
            // 마지막 앱 뒤에 다시 넣는다
            while (slots.isNotEmpty() && slots.last().isEmpty()) slots.removeAt(slots.size - 1)
            slots.add(a.key)
            store.slots = slots
        }
        toast("'${a.label}'을(를) 홈으로 되돌렸어요")
        fill(keepPage = true)
    }

    private fun menu(a: AppEntry) {
        val hidden = a.key in store.hidden
        val items = arrayOf(if (hidden) "홈으로 되돌리기" else "홈에서 빼기", "앱 정보", "앱 삭제")
        dialog(a.label).setItems(items) { _, w ->
            when (w) {
                0 -> if (hidden) unhide(a) else {
                    val s = store.slots
                    val i = s.indexOf(a.key)
                    if (i >= 0) { s[i] = ""; store.slots = s }
                    store.hidden = store.hidden.apply { add(a.key) }
                    fill(keepPage = true)
                }
                1 -> openAppInfo(a.pkg)
                2 -> uninstall(a.pkg)
            }
        }.setNegativeButton("닫기", null).show()
    }

    companion object {
        const val EXTRA_HIDDEN = "hidden"
        const val EXTRA_PICK_SLOT = "pickSlot"

        /** ← 제목 (기본 설정 화면과 같은 모양). */
        fun header(a: Activity, title: String, back: () -> Unit): View = LineRow(a).apply {
            minimumHeight = a.dp(72)
            setPadding(a.dp(8), 0, a.dp(24), 0)
            addView(a.text("←", 26f).apply {
                gravity = Gravity.CENTER
                setPadding(a.dp(16), a.dp(8), a.dp(24), a.dp(8))
                setOnClickListener { back() }
            })
            addView(a.text(title, 22f, bold = true))
        }
    }
}
