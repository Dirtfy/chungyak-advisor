package com.chungyak.advisor.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * DownloadManager 완료 알림. 앱이 백그라운드(또는 종료)일 때 다 받으면 "업데이트 준비됨" 알림을 띄운다.
 * 앱이 화면에 있으면 UpdateViewModel이 진행 상태를 보고 있다가 바로 설치 화면을 연다.
 * 아무 앱이나 이 액션을 보낼 수 있지만, 우리가 저장한 id의 실제 상태만 확인하므로 위조해도 영향이 없다.
 */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (!UpdateDownloads.isOurs(context, id)) return
        if (UpdateDownloads.isForeground()) return
        val s = UpdateDownloads.status(context)
        if (s is UpdateDownloads.Status.Done) UpdateDownloads.notifyReady(context, s.info)
    }
}
