package com.kakario.samlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kakario.samlauncher.update.Updater
import com.kakario.samlauncher.update.UpdateDialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 홈 화면. 기본 교보 런처처럼 선 격자에 아이콘 + 이름, 아래쪽 ☰ 메뉴와 페이지 표시.
 *  - 칸 누르기: 앱 열기 / 길게 누르기: 옮기기·홈에서 빼기·앱 정보·삭제 (빈 칸은 앱 넣기)
 *  - 좌우로 밀기 또는 페이지 표시 누르기: 페이지 넘김
 */
class HomeActivity : Activity(), CellGrid.Listener {
    private lateinit var store: Store
    private lateinit var apps: Apps
    private lateinit var grid: CellGrid
    private lateinit var pageText: TextView
    private lateinit var hintText: TextView
    private lateinit var flash: View

    private var installed: Map<String, AppEntry> = emptyMap()
    private var slots: MutableList<String> = mutableListOf()
    private var page = 0
    private var moveFrom = -1 // 옮기는 중인 칸 번호 (전체 기준), -1 = 아님

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var updater: Updater
    private lateinit var updateDialogs: UpdateDialogs
    private var updateJob: Job? = null

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { main.post { reload() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        apps = Apps(this)
        updater = Updater.get(this)
        updateDialogs = UpdateDialogs(this, updater)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAPER); fitsSystemWindows = true }
        grid = CellGrid(this, this)
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomBar(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))

        val frame = FrameLayout(this)
        frame.addView(root)
        flash = View(this).apply { setBackgroundColor(INK); visibility = View.GONE }
        frame.addView(flash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(frame)

        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    private fun bottomBar(): View {
        // 위쪽 선은 격자 아래 테두리가 맡는다
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val menu = text("☰", 30f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { if (moveFrom >= 0) cancelMove() else showMenu() }
        }
        hintText = text("", 16f).apply { gravity = Gravity.CENTER; oneLine() }
        pageText = text("1/1", 20f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { goPage(page + 1, wrap = true) }
        }
        bar.addView(menu, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        bar.addView(hintText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        bar.addView(pageText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
        return bar
    }

    override fun onResume() {
        super.onResume()
        reload()
        updateJob = scope.launch { updater.state.collect { updateDialogs.show(it) } }
        maybeCheckUpdate()
    }

    override fun onPause() {
        updateJob?.cancel(); updateJob = null
        updateDialogs.close()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 홈 버튼 다시 누르면 첫 페이지로
        if (moveFrom >= 0) cancelMove()
        if (page != 0) goPage(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (moveFrom >= 0) cancelMove() else if (page != 0) goPage(0)
        // 홈에서는 뒤로 가기로 꺼지지 않는다
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pkgReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    // ── 배치 ──

    private fun reload() {
        val list = apps.installed()
        installed = list.associateBy { it.key }
        syncLayout(store, list)
        slots = store.slots
        grid.setSize(store.cols, store.rows)
        page = page.coerceIn(0, pageCount() - 1)
        render()
    }

    private fun pageCount(): Int {
        val used = slots.indexOfLast { it.isNotEmpty() } + 1
        var n = maxOf(1, (used + store.perPage - 1) / store.perPage)
        if (moveFrom >= 0) n++ // 옮길 때는 빈 새 페이지로도 옮길 수 있게
        return n
    }

    private fun render() {
        val per = store.perPage
        for (i in 0 until per) {
            val idx = page * per + i
            val e = slots.getOrNull(idx)?.let { installed[it] }
            grid.cells[i].bind(e?.let { apps.icon(it) }, e?.label)
            grid.cells[i].marked = idx == moveFrom
        }
        pageText.text = "${page + 1}/${pageCount()}"
        hintText.text = if (moveFrom >= 0) "옮길 칸을 누르세요  (☰ 취소)" else ""
    }

    private fun goPage(p: Int, wrap: Boolean = false) {
        val n = pageCount()
        val np = when {
            p in 0 until n -> p
            wrap -> 0
            else -> return
        }
        if (np == page) return
        page = np
        if (store.flashOnPage) {
            flash.visibility = View.VISIBLE
            main.postDelayed({ flash.visibility = View.GONE; render() }, 120)
        } else render()
    }

    private fun save() {
        store.slots = slots
        slots = store.slots
    }

    private fun ensureSize(n: Int) { while (slots.size < n) slots.add("") }

    // ── 칸 눌렀을 때 ──

    override fun onCellClick(i: Int) {
        val idx = page * store.perPage + i
        if (moveFrom >= 0) {
            ensureSize(maxOf(idx, moveFrom) + 1)
            val t = slots[idx]; slots[idx] = slots[moveFrom]; slots[moveFrom] = t
            moveFrom = -1
            save(); page = page.coerceIn(0, pageCount() - 1); render()
            return
        }
        val e = slots.getOrNull(idx)?.let { installed[it] } ?: return
        openApp(e)
    }

    override fun onCellLongClick(i: Int) {
        if (moveFrom >= 0) return
        val idx = page * store.perPage + i
        val e = slots.getOrNull(idx)?.let { installed[it] }
        if (e == null) { pickAppFor(idx); return }
        val items = arrayOf("옮기기", "홈에서 빼기", "앱 정보", "앱 삭제")
        dialog(e.label).setItems(items) { _, w ->
            when (w) {
                0 -> { moveFrom = idx; render() }
                1 -> hide(idx, e)
                2 -> openAppInfo(e.pkg)
                3 -> uninstall(e.pkg)
            }
        }.setNegativeButton("닫기", null).show()
    }

    override fun onSwipe(next: Boolean) = goPage(if (next) page + 1 else page - 1)

    private fun cancelMove() { moveFrom = -1; page = page.coerceIn(0, pageCount() - 1); render() }

    private fun hide(idx: Int, e: AppEntry) {
        slots[idx] = ""
        save()
        store.hidden = store.hidden.apply { add(e.key) }
        render()
        toast("'${e.label}'을(를) 홈에서 뺐어요. ☰ → 숨긴 앱에서 되돌릴 수 있어요")
    }

    /** 빈 칸 길게 누르기: 이 칸에 넣을 앱 고르기 (페이지로 나뉜 목록 화면). */
    private fun pickAppFor(idx: Int) {
        startActivity(Intent(this, AppListActivity::class.java).putExtra(AppListActivity.EXTRA_PICK_SLOT, idx))
    }

    // ── ☰ 메뉴 ──

    private fun showMenu() {
        val names = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        fun add(n: String, f: () -> Unit) { names.add(n); actions.add(f) }
        add("전체 앱") { startActivity(Intent(this, AppListActivity::class.java)) }
        if (store.linkInMenu) add("이북 링크") { openEreaderLink() }
        add("숨긴 앱 (되돌리기)") {
            startActivity(Intent(this, AppListActivity::class.java).putExtra(AppListActivity.EXTRA_HIDDEN, true))
        }
        add("격자 크기 (${store.cols}×${store.rows})") { chooseGrid(this, store) { reload() } }
        add("런처 설정") { startActivity(Intent(this, SettingsActivity::class.java)) }
        dialog(null).setItems(names.toTypedArray()) { _, w -> actions[w]() }
            .setNegativeButton("닫기", null).show()
    }

    // ── 자동 업데이트 확인 (홈은 자주 열리므로 6시간에 한 번만) ──

    private fun maybeCheckUpdate() {
        if (!store.autoUpdate) return
        val now = System.currentTimeMillis()
        if (now - store.lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        store.lastUpdateCheck = now
        updater.check(quiet = true)
    }

    companion object {
        /** 격자 크기 고르기 (홈 메뉴·설정 공용). */
        fun chooseGrid(ctx: Context, store: Store, done: () -> Unit) {
            val names = GRID_CHOICES.map { (c, r) -> "${c}×${r}" + if (c == store.cols && r == store.rows) "  (지금)" else "" }
            AlertDialog.Builder(ctx).setTitle("격자 크기 (가로×세로)")
                .setItems(names.toTypedArray()) { _, w ->
                    store.cols = GRID_CHOICES[w].first
                    store.rows = GRID_CHOICES[w].second
                    done()
                }.setNegativeButton("닫기", null).show()
        }
    }
}
