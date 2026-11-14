package com.kakario.samlauncher

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

const val INK = 0xFF000000.toInt()
const val PAPER = 0xFFFFFFFF.toInt()

fun Context.dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()
fun Context.dp(v: Int): Int = dp(v.toFloat())

fun Context.text(s: CharSequence, sp: Float = 18f, bold: Boolean = false): TextView = TextView(this).apply {
    text = s
    setTextColor(INK)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    if (bold) typeface = Typeface.DEFAULT_BOLD
    includeFontPadding = true
}

/** 아래쪽 얇은 선이 있는 한 줄 (설정·목록용). */
class LineRow(context: Context) : LinearLayout(context) {
    private val line = Paint().apply { color = INK; strokeWidth = context.dp(1).toFloat().coerceAtLeast(1f) }
    var dashed = false
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = context.dp(64)
        setPadding(context.dp(24), context.dp(10), context.dp(24), context.dp(10))
        setWillNotDraw(false)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val y = height - line.strokeWidth / 2
        if (!dashed) { canvas.drawLine(0f, y, width.toFloat(), y, line); return }
        var x = 0f
        val d = context.dp(6).toFloat()
        while (x < width) { canvas.drawLine(x, y, (x + d).coerceAtMost(width.toFloat()), y, line); x += d * 2 }
    }
}

/** 제목 + (설명) + 오른쪽 값 한 줄. */
fun Context.settingRow(title: String, sub: String? = null, value: String? = null, onClick: (() -> Unit)?): LineRow =
    LineRow(this).apply {
        dashed = true
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 19f))
        if (sub != null) col.addView(text(sub, 14f).apply { setPadding(0, dp(2), 0, 0) })
        addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (value != null) addView(text(value, 17f, bold = true).apply { setPadding(dp(12), 0, 0, 0) })
        if (onClick != null) setOnClickListener { onClick() }
    }

/** 회색 띠 대신 굵은 글씨 + 선으로 된 구역 제목. */
fun Context.sectionTitle(title: String): View = LineRow(this).apply {
    minimumHeight = dp(48)
    setPadding(dp(24), dp(18), dp(24), dp(6))
    addView(text(title, 15f, bold = true))
}

fun Context.dialog(title: String?, message: String? = null): AlertDialog.Builder =
    AlertDialog.Builder(this).apply {
        if (title != null) setTitle(title)
        if (message != null) setMessage(message)
    }

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.openApp(e: AppEntry) {
    try { startActivity(e.intent()) } catch (_: Exception) { toast("열 수 없어요: ${e.label}") }
}

/** 이북 링크 앱 열기 (설치 안 돼 있으면 알려 주기). */
fun Context.openEreaderLink() {
    val i = packageManager.getLaunchIntentForPackage(Apps.LINK_PKG)
    if (i == null) { toast("이북 링크 앱이 설치돼 있지 않아요"); return }
    startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openAppInfo(pkg: String) {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast("앱 정보를 열 수 없어요") }
}

fun Context.uninstall(pkg: String) {
    runCatching {
        @Suppress("DEPRECATION")
        startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { toast("이 앱은 지울 수 없어요") }
}

/** 지금 기본 홈 앱이 이 런처인지. */
fun Context.isDefaultHome(): Boolean {
    val r = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
    return r?.activityInfo?.packageName == packageName
}

/** 기본 홈 앱 지정 화면 열기 — 기기가 막아 두었으면 PC 연결 방법 안내. */
fun Context.openHomeSettings() {
    val tries = listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    for (a in tries) {
        try { startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) {} catch (_: SecurityException) {}
    }
    dialog(
        "기본 홈 앱 지정",
        "이 기기는 기본 홈 앱 설정 화면을 막아 두었어요.\n\n" +
            "방법 1) 홈 버튼을 눌렀을 때 홈 앱 고르는 창이 뜨면 '샘 런처' → '항상'.\n\n" +
            "방법 2) PC에 연결해서 아래 명령 한 줄:\n" +
            "adb shell cmd package set-home-activity com.kakario.samlauncher/.HomeActivity",
    ).setPositiveButton("확인", null).show()
}

fun TextView.oneLine(): TextView = apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
