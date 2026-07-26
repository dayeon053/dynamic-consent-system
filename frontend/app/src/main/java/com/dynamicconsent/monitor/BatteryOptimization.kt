package com.dynamicconsent.monitor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 배터리 최적화(Doze) 예외 헬퍼.
 *
 * Doze 모드에 들어가면 포그라운드 서비스라도 CPU 사용이 제한돼
 * UsageStatsManager 폴링 주기가 늘어지거나 감지가 지연될 수 있다.
 * 예외 목록에 등록되면 화면이 꺼진 상태에서도 감시 주기가 유지된다.
 *
 * 제조사 커스텀 배터리 관리(삼성 '앱 절전' 등)는 이 설정과 별개로 동작하므로,
 * 실기기 검증 시에는 해당 설정도 함께 해제해야 할 수 있다.
 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * 배터리 최적화 예외를 요청한다.
 * 직접 요청 다이얼로그를 띄우고, 기기에서 지원하지 않으면 설정 목록 화면으로 대체한다.
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

    val directRequest = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(directRequest) }
        .recoverCatching { context.startActivity(fallback) }
}
