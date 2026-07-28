package com.dynamicconsent.monitor

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * '사용 정보 접근'(PACKAGE_USAGE_STATS) 권한 헬퍼.
 *
 * 이 권한은 일반 런타임 권한 다이얼로그로 요청할 수 없고,
 * 시스템 설정의 '사용 정보 접근' 화면에서 사용자가 직접 허용해야 한다.
 * 허용 여부는 AppOpsManager 로 확인한다.
 */
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

/** 시스템 설정의 '사용 정보 접근' 화면을 연다. 복귀 후 hasUsageAccess() 로 재확인할 것. */
fun openUsageAccessSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
