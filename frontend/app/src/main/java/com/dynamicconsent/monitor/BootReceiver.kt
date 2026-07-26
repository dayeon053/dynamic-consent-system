package com.dynamicconsent.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dynamicconsent.overlay.canDrawOverlays

/**
 * 재부팅·앱 업데이트 후 감시를 자동 복구하는 리시버.
 *
 * 포그라운드 서비스라도 기기가 꺼지면 프로세스가 사라지므로,
 * 사용자가 감시를 켜둔 상태였다면([MonitorPreferences]) 부팅 완료 시점에 다시 시작한다.
 * 권한이 회수된 상태에서 시작하면 서비스가 곧바로 stopSelf 하므로 여기서 미리 확인한다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        if (!MonitorPreferences.isMonitoringEnabled(context)) {
            Log.d(TAG, "감시가 꺼진 상태였으므로 복구하지 않음 (action=$action)")
            return
        }

        if (!hasUsageAccess(context) || !canDrawOverlays(context)) {
            Log.w(TAG, "권한이 없어 감시를 복구하지 못함 " +
                    "(usageAccess=${hasUsageAccess(context)}, overlay=${canDrawOverlays(context)})")
            return
        }

        Log.i(TAG, "부팅 완료 감지 → 앱 실행 감시 자동 복구 (action=$action)")
        runCatching { AppLaunchMonitorService.start(context) }
            .onFailure { Log.e(TAG, "감시 자동 복구 실패", it) }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
