package com.kakario.samlauncher

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

/** 전체 앱 목록 / 숨긴 앱 목록. 선과 글자만. */
class AppListActivity : Activity() {
    private lateinit var store: Store
    private lateinit var apps: Apps
    private lateinit var list: LinearLayout
    private var hiddenMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        apps = Apps(this)
        hiddenMode = intent.getBooleanExtra(EXTRA_HIDDEN, false)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAPER) }
        root.addView(header(this, if (hiddenMode) "숨긴 앱" else "전체 앱") { finish() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        fill()
    }

    private fun fill() {
        list.removeAllViews()
        val all = apps.installed()
        val hidden = store.hidden
        val shown = if (hiddenMode) all.filter { it.key in hidden } else all
        if (shown.isEmpty()) {
            list.addView(text(if (hiddenMode) "숨긴 앱이 없어요" else "앱이 없어요", 18f).apply {
                setPadding(dp(24), dp(32), dp(24), dp(32))
            })
            return
        }
        for (a in shown) {
            val row = LineRow(this).apply { dashed = true }
            val ic = ImageView(this).apply { setImageDrawable(apps.icon(a)); scaleType = ImageView.ScaleType.FIT_CENTER }
            row.addView(ic, LinearLayout.LayoutParams(dp(52), dp(52)))
            row.addView(text(a.label, 20f).oneLine().apply { setPadding(dp(20), 0, 0, 0) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (hiddenMode) {
                row.addView(text("되돌리기", 17f, bold = true))
                row.setOnClickListener { unhide(a) }
            } else {
                if (a.key in hidden) row.addView(text("숨김", 15f))
                row.setOnClickListener { openApp(a) }
                row.setOnLongClickListener { menu(a); true }
            }
            list.addView(row)
        }
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
        fill()
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
                    fill()
                }
                1 -> openAppInfo(a.pkg)
                2 -> uninstall(a.pkg)
            }
        }.setNegativeButton("닫기", null).show()
    }

    companion object {
        const val EXTRA_HIDDEN = "hidden"

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
