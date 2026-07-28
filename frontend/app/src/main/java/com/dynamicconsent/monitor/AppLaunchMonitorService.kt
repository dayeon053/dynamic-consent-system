package com.dynamicconsent.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dynamicconsent.overlay.canDrawOverlays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [3단계] 백그라운드에서 감시 대상 앱(카카오톡 등)의 실행을 감지하는 포그라운드 서비스.
 *
 * 1초 간격으로 AppLaunchDetector 를 폴링하고,
 * 감지되면 RiskOverlayPipeline(2단계)을 통해 위험도 오버레이(#8)를 띄운다.
 *
 * 필요 권한 (둘 다 사용자가 설정 화면에서 직접 허용해야 함):
 *  - PACKAGE_USAGE_STATS : 사용 정보 접근 (UsageStatsManager)
 *  - SYSTEM_ALERT_WINDOW : 다른 앱 위에 표시 (오버레이)
 *
 * 이 서비스에서 RiskOverlayService(또 다른 FGS)를 시작하는 것은
 * SYSTEM_ALERT_WINDOW 권한 보유 앱에 적용되는 백그라운드 FGS 시작 예외에 해당한다.
 */
class AppLaunchMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        if (!hasUsageAccess(this) || !canDrawOverlays(this)) {
            Log.w(TAG, "권한 부족으로 감시를 시작할 수 없음 " +
                    "(usageAccess=${hasUsageAccess(this)}, overlay=${canDrawOverlays(this)})")
            MonitorPreferences.setMonitoringEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }

        if (monitorJob?.isActive == true) {
            Log.d(TAG, "이미 감시 중 — 재시작 생략")
            return START_STICKY
        }

        val detector = AppLaunchDetector(this)
        val pipeline = RiskOverlayPipeline(applicationContext)

        monitorJob = serviceScope.launch {
            Log.i(TAG, "앱 실행 감시 시작 (poll=${POLL_INTERVAL_MILLIS}ms, 대상=${WatchedApps.watchedPackages})")
            while (isActive) {
                runCatching {
                    detector.pollLaunchedWatchedApp()?.let { pkg ->
                        Log.i(TAG, "감지 → 파이프라인 호출: $pkg")
                        pipeline.showOverlayFor(pkg)
                    }
                }.onFailure { Log.e(TAG, "감시 루프 오류", it) }
                delay(POLL_INTERVAL_MILLIS)
            }
        }

        isRunning = true
        return START_STICKY
    }

    /**
     * 최근 앱 목록에서 스와이프로 앱을 제거하면 시스템이 서비스를 함께 종료한다.
     * 사용자가 감시를 켜둔 상태였다면 즉시 다시 시작해 감시가 끊기지 않게 한다.
     * (SYSTEM_ALERT_WINDOW 권한 보유 앱은 백그라운드 FGS 시작 제한의 예외에 해당한다.)
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (MonitorPreferences.isMonitoringEnabled(this)) {
            Log.i(TAG, "태스크 제거 감지 → 감시 서비스 재시작")
            runCatching { start(applicationContext) }
                .onFailure { Log.e(TAG, "감시 서비스 재시작 실패", it) }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.i(TAG, "앱 실행 감시 종료")
        monitorJob?.cancel()
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    // ---- 포그라운드 서비스 알림 ----
    private fun startAsForeground() {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("개인정보 지킴이 작동 중")
            .setContentText("앱 실행을 감지해 위험도 알림을 준비하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_menu_view) // TODO: 앱 아이콘으로 교체
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "앱 실행 감시",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AppLaunchMonitor"
        private const val CHANNEL_ID = "app_launch_monitor_channel"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_INTERVAL_MILLIS = 1_000L

        /** UI 표시용 간이 상태 플래그 */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** 화면에 보이는 컨텍스트(Activity/Compose)에서 호출할 것. */
        fun start(context: Context) {
            MonitorPreferences.setMonitoringEnabled(context, true)
            val intent = Intent(context, AppLaunchMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            // 사용자가 명시적으로 끈 것이므로 재부팅 후에도 자동 복구하지 않는다.
            MonitorPreferences.setMonitoringEnabled(context, false)
            context.stopService(Intent(context, AppLaunchMonitorService::class.java))
        }
    }
}
