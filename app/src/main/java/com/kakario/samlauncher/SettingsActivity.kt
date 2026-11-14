package com.kakario.samlauncher

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import com.kakario.samlauncher.update.UpdateDialogs
import com.kakario.samlauncher.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 런처 설정. 기본 설정 화면처럼 줄 + 점선. */
class SettingsActivity : Activity() {
    private lateinit var store: Store
    private lateinit var body: LinearLayout
    private lateinit var updater: Updater
    private lateinit var dialogs: UpdateDialogs
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        updater = Updater.get(this)
        dialogs = UpdateDialogs(this, updater)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(PAPER) }
        root.addView(AppListActivity.header(this, "런처 설정") { finish() })
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        fill()
        job = scope.launch { updater.state.collect { dialogs.show(it) } }
    }

    override fun onPause() {
        job?.cancel(); job = null
        dialogs.close()
        super.onPause()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun onOff(b: Boolean) = if (b) "켬" else "끔"

    private fun fill() {
        body.removeAllViews()

        body.addView(sectionTitle("홈 화면"))
        body.addView(settingRow("격자 크기", "가로×세로 칸 수", "${store.cols}×${store.rows}") {
            HomeActivity.chooseGrid(this, store) { fill() }
        })
        body.addView(settingRow("새 앱 자동으로 넣기", "새로 설치한 앱을 홈 마지막 칸 뒤에 넣어요", onOff(store.autoAdd)) {
            store.autoAdd = !store.autoAdd; fill()
        })
        body.addView(settingRow("페이지 넘길 때 잔상 지우기", "화면을 한 번 검게 깜빡여 잔상을 지워요", onOff(store.flashOnPage)) {
            store.flashOnPage = !store.flashOnPage; fill()
        })
        body.addView(settingRow("숨긴 앱", "홈에서 뺀 앱을 되돌려요", "${store.hidden.size}개") {
            startActivity(android.content.Intent(this, AppListActivity::class.java).putExtra(AppListActivity.EXTRA_HIDDEN, true))
        })
        body.addView(settingRow("배치 처음으로", "칸 배치와 숨긴 앱을 모두 처음 상태로", null) {
            dialog("배치 처음으로", "홈 칸 배치와 숨긴 앱 목록을 처음 상태로 되돌릴까요?")
                .setPositiveButton("되돌리기") { _, _ -> store.resetLayout(); toast("처음 상태로 되돌렸어요") }
                .setNegativeButton("취소", null).show()
        })

        body.addView(sectionTitle("이북 링크"))
        body.addView(settingRow("이북 링크 열기", "PC와 무선 연결", null) { openEreaderLink() })
        body.addView(settingRow("☰ 메뉴에 이북 링크 표시", "홈 아래 ☰ 메뉴에서 바로 열기", onOff(store.linkInMenu)) {
            store.linkInMenu = !store.linkInMenu; fill()
        })

        body.addView(sectionTitle("기본 홈"))
        body.addView(settingRow(
            "기본 홈 앱으로 지정",
            if (isDefaultHome()) "지금 샘 런처가 기본 홈이에요" else "지금은 다른 런처가 기본 홈이에요",
            null,
        ) { openHomeSettings() })

        body.addView(sectionTitle("업데이트"))
        body.addView(settingRow("새 버전 확인", "지금 빌드 #${updater.currentBuild}", null) { updater.check(quiet = false) })
        body.addView(settingRow("자동으로 새 버전 확인", "홈을 열 때 확인 (6시간에 한 번)", onOff(store.autoUpdate)) {
            store.autoUpdate = !store.autoUpdate; fill()
        })
    }
}
