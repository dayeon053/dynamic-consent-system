package com.dynamicconsent.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.ui.navigation.Screen
import com.dynamicconsent.ui.orgdetail.OrgDetailTab
import com.dynamicconsent.ui.theme.FrontendTheme

/**
 * 위험도 요약 팝업을 다른 앱 위에 띄우는 포그라운드 서비스.
 *
 * 앱이 백그라운드로 가도 팝업이 유지돼야 하므로 FGS로 구현한다.
 *  - API 26+ : startForegroundService + startForeground 필수
 *  - API 34+ : foregroundServiceType(specialUse) 명시 + 매니페스트 property 필수
 *  - API 36+ : SYSTEM_ALERT_WINDOW 앱도 '보이는 오버레이'가 있어야 백그라운드에서 FGS 시작 가능.
 *              이 서비스는 화면에 보이는 Compose 화면에서 start() 하므로 문제없음.
 *
 * ComposeView는 Activity 밖(Service)에서 동작하므로 OverlayLifecycleOwner를 붙여준다.
 */
class RiskOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "risk_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_SCORE = "extra_score"
        private const val EXTRA_GRADE = "extra_grade"
        private const val EXTRA_ORG_ID = "extra_org_id"

        /** 화면에 보이는 컨텍스트에서 호출할 것. */
        fun start(context: Context, orgId: String, score: Double, grade: RiskGrade) {
            val intent = Intent(context, RiskOverlayService::class.java).apply {
                putExtra(EXTRA_ORG_ID, orgId)
                putExtra(EXTRA_SCORE, score)
                putExtra(EXTRA_GRADE, grade.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RiskOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var overlayView: ComposeView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        lifecycleOwner.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val orgId = intent?.getStringExtra(EXTRA_ORG_ID).orEmpty()
        val score = intent?.getDoubleExtra(EXTRA_SCORE, Double.NaN) ?: Double.NaN
        val grade = intent?.getStringExtra(EXTRA_GRADE)
            ?.let { runCatching { RiskGrade.valueOf(it) }.getOrNull() }

        if (orgId.isEmpty() || score.isNaN() || grade == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(orgId, score, grade)
        return START_NOT_STICKY
    }

    private fun showOverlay(orgId: String, score: Double, grade: RiskGrade) {
        removeOverlay()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 160
        }

        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setContent {
            FrontendTheme {
                RiskOverlayContent(
                    score = score,
                    grade = grade,
                    onDetail = {
                        openOrgDetail(orgId)
                        stopSelf()
                    },
                    onClose = { stopSelf() },
                    // 카드를 드래그해서 위치 이동 (버튼 탭은 그대로 동작)
                    modifier = Modifier.pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            params.x += drag.x.toInt()
                            params.y += drag.y.toInt()
                            windowManager.updateViewLayout(view, params)
                        }
                    },
                )
            }
        }

        windowManager.addView(view, params)
        overlayView = view
    }

    /** #13에서 등록한 딥링크로 기업상세 화면(위험도 탭)을 연다. */
    private fun openOrgDetail(orgId: String) {
        val uri = Uri.parse(Screen.OrgDetail.createDeepLinkUri(orgId, OrgDetailTab.RISK))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
            .onFailure { android.util.Log.e("RiskOverlayService", "기업상세 이동 실패: $orgId", it) }
    }

    private fun removeOverlay() {
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
    }

    override fun onDestroy() {
        removeOverlay()
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }

    // ---- 포그라운드 서비스 알림 ----
    private fun startAsForeground() {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("개인정보 위험도 알림")
            .setContentText("동의 위험도 분석 결과를 표시하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: 앱 아이콘으로 교체
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
                "위험도 오버레이",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
