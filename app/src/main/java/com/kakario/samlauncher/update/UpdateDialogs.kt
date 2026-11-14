package com.kakario.samlauncher.update

import android.app.Activity
import android.app.AlertDialog

/** 업데이트 상태 → 흰 바탕 기본 창. 받는 중 진행률은 10% 단위로만 다시 그린다(이잉크). */
class UpdateDialogs(private val activity: Activity, private val updater: Updater) {
    private var shown: AlertDialog? = null
    private var shownKind: String? = null
    private var lastPct = -1

    fun show(s: Updater.State) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (s is Updater.State.Downloading && shownKind == "dl") {
            val p = s.percent / 10 * 10
            if (p != lastPct) { lastPct = p; shown?.setMessage("받는 중 $p%") }
            return
        }
        shown?.dismiss(); shown = null; shownKind = null
        val b = AlertDialog.Builder(activity)
        when (s) {
            is Updater.State.Available -> b.setTitle("샘 런처 새 버전")
                .setMessage("지금 빌드 #${s.current} → 새 빌드 #${s.release.build}\n받아서 바로 설치할까요?")
                .setPositiveButton("업데이트") { _, _ -> updater.download(s.release) }
                .setNegativeButton("나중에") { _, _ -> updater.dismiss() }
            is Updater.State.Downloading -> {
                lastPct = s.percent / 10 * 10
                shownKind = "dl"
                b.setTitle("업데이트").setMessage("받는 중 $lastPct%").setCancelable(false)
            }
            is Updater.State.Ready -> b.setTitle("설치 준비 완료")
                .setMessage(
                    "'설치'를 누르면 바로 업데이트돼요. 런처가 잠깐 꺼졌다 다시 켜져요.\n" +
                        "처음 한 번은 '이 출처 허용'을 켜 주세요." +
                        (updater.lastError?.let { "\n(지난 시도 오류: $it)" } ?: "")
                )
                .setPositiveButton("설치") { _, _ -> updater.install(s.file) }
                .setNegativeButton("닫기") { _, _ -> updater.dismiss() }
            Updater.State.Installing -> b.setTitle("업데이트 설치 중…")
                .setMessage("끝나면 런처가 잠깐 꺼졌다가 다시 켜져요.").setCancelable(false)
            is Updater.State.UpToDate -> b.setTitle("최신 버전이에요").setMessage("지금 빌드 #${s.build}")
                .setPositiveButton("확인") { _, _ -> updater.dismiss() }
            is Updater.State.Failed -> b.setTitle("업데이트").setMessage(s.message)
                .setPositiveButton("확인") { _, _ -> updater.dismiss() }
            Updater.State.Checking -> b.setMessage("새 버전 확인하는 중…")
            Updater.State.Idle -> return
        }
        b.setOnCancelListener { updater.dismiss() }
        shown = b.show()
    }

    fun close() { shown?.dismiss(); shown = null; shownKind = null }
}
