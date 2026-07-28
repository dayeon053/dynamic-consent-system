package com.dynamicconsent.monitor

import android.content.Context

/**
 * 감시 기능의 사용자 설정을 프로세스 종료·재부팅 후에도 유지하기 위한 저장소.
 *
 * [AppLaunchMonitorService.isRunning]은 인메모리 플래그라 프로세스가 죽으면 사라진다.
 * "사용자가 감시를 켜둔 상태였는가"는 재부팅 후 자동 복구 판단에 필요하므로 별도로 보존한다.
 */
object MonitorPreferences {

    private const val PREFS_NAME = "monitor_prefs"
    private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 사용자가 감시를 켜둔 상태인지 여부. 재부팅·강제 종료 후 복구 기준이 된다. */
    fun isMonitoringEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MONITORING_ENABLED, false)

    fun setMonitoringEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
    }
}
