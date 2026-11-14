package com.kakario.samlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import org.json.JSONArray

/** 설치된 앱 하나 (홈 칸에 들어가는 단위). key = "패키지/클래스". */
data class AppEntry(val key: String, val pkg: String, val cls: String, val label: String) {
    fun intent(): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(pkg, cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

/** 격자 크기 선택지 (열 × 행). */
val GRID_CHOICES = listOf(3 to 3, 3 to 4, 4 to 5, 4 to 6, 5 to 6)

/**
 * 런처 설정·배치 저장소.
 *  - slots: 칸 순서대로 앱 key (빈 칸은 ""), 페이지는 '칸 수'로 잘라서 만든다
 *  - hidden: 홈에서 뺀 앱 (언제든 되돌리기 가능)
 */
class Store(context: Context) {
    private val ctx = context.applicationContext
    private val p = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var cols: Int
        get() = p.getInt("cols", 4)
        set(v) = p.edit().putInt("cols", v).apply()
    var rows: Int
        get() = p.getInt("rows", 5)
        set(v) = p.edit().putInt("rows", v).apply()
    val perPage: Int get() = cols * rows

    /** 새로 설치된 앱을 홈 빈 칸에 자동으로 넣기 */
    var autoAdd: Boolean
        get() = p.getBoolean("autoAdd", true)
        set(v) = p.edit().putBoolean("autoAdd", v).apply()
    /** ☰ 메뉴에 '이북 링크' 표시 */
    var linkInMenu: Boolean
        get() = p.getBoolean("linkInMenu", true)
        set(v) = p.edit().putBoolean("linkInMenu", v).apply()
    /** 홈을 열 때 새 버전 자동 확인 */
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    var lastUpdateCheck: Long
        get() = p.getLong("lastUpdateCheck", 0L)
        set(v) = p.edit().putLong("lastUpdateCheck", v).apply()
    /** 페이지 넘길 때 화면을 한 번 검게 깜빡여 잔상 지우기 */
    var flashOnPage: Boolean
        get() = p.getBoolean("flashOnPage", false)
        set(v) = p.edit().putBoolean("flashOnPage", v).apply()

    var slots: MutableList<String>
        get() {
            val s = p.getString("slots", null) ?: return mutableListOf()
            val a = JSONArray(s)
            return MutableList(a.length()) { a.optString(it, "") }
        }
        set(v) {
            // 끝쪽 빈 칸은 저장하지 않는다
            var end = v.size
            while (end > 0 && v[end - 1].isEmpty()) end--
            p.edit().putString("slots", JSONArray(v.subList(0, end)).toString()).apply()
        }
    val initialized: Boolean get() = p.contains("slots")

    var hidden: MutableSet<String>
        get() = p.getStringSet("hidden", emptySet())!!.toMutableSet()
        set(v) = p.edit().putStringSet("hidden", v.toSet()).apply()

    fun resetLayout() = p.edit().remove("slots").remove("hidden").apply()
}

/** 설치된 앱 읽기 + 아이콘 캐시. */
class Apps(context: Context) {
    private val ctx = context.applicationContext
    private val la = ctx.getSystemService(LauncherApps::class.java)
    private val icons = HashMap<String, Drawable>()

    fun installed(): List<AppEntry> {
        val list = runCatching { la.getActivityList(null, Process.myUserHandle()) }.getOrDefault(emptyList())
        return list.mapNotNull {
            val cn = it.componentName
            if (cn.packageName == ctx.packageName) null
            else AppEntry("${cn.packageName}/${cn.className}", cn.packageName, cn.className, it.label?.toString() ?: cn.packageName)
        }.sortedWith(compareBy({ !it.pkg.startsWith("com.kakario.") }, { it.label }))
    }

    fun icon(e: AppEntry): Drawable? = icons[e.key] ?: runCatching {
        ctx.packageManager.getActivityIcon(android.content.ComponentName(e.pkg, e.cls))
    }.getOrNull()?.also { icons[e.key] = it }

    fun clearIcons() = icons.clear()

    companion object {
        const val LINK_PKG = "com.kakario.ereaderlink"
    }
}

/**
 * 홈 배치를 설치 상태에 맞춘다.
 *  - 지워진 앱 칸은 비운다
 *  - (처음이거나 자동 추가가 켜져 있으면) 홈에 없는 새 앱을 빈 칸에 넣는다
 */
fun syncLayout(store: Store, installed: List<AppEntry>) {
    val keys = installed.map { it.key }.toSet()
    val first = !store.initialized
    val slots = store.slots
    for (i in slots.indices) if (slots[i].isNotEmpty() && slots[i] !in keys) slots[i] = ""
    val hidden = store.hidden.apply { retainAll(keys) }
    if (first || store.autoAdd) {
        // 새 앱은 마지막 앱 뒤에 이어 붙인다 (일부러 비워 둔 칸은 건드리지 않음)
        while (slots.isNotEmpty() && slots.last().isEmpty()) slots.removeAt(slots.size - 1)
        val placed = slots.filter { it.isNotEmpty() }.toSet()
        for (a in installed) {
            if (a.key in placed || a.key in hidden) continue
            slots.add(a.key)
        }
    }
    store.slots = slots
    store.hidden = hidden
}
