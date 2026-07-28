package com.dynamicconsent.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log

/**
 * [3단계] UsageStatsManager 로 앱 실행(포그라운드 진입) 순간을 감지한다.
 *
 * queryEvents() 는 시스템에 쌓인 사용 이벤트를 구간 조회하는 방식이라
 * 콜백이 아닌 폴링으로 동작한다. 호출 측(AppLaunchMonitorService)이
 * 주기적으로 pollLaunchedWatchedApp() 을 호출하는 구조.
 *
 *  - API 29+ : ACTIVITY_RESUMED 이벤트로 감지
 *  - API 26~28 : MOVE_TO_FOREGROUND 이벤트로 감지 (29에서 deprecated 되었으나 하위 호환용)
 *
 * 같은 앱이 화면 전환마다 이벤트를 반복 발생시키므로,
 * 패키지별 쿨다운을 둬서 팝업이 연속으로 뜨는 것을 막는다.
 */
class AppLaunchDetector(
    context: Context,
    /**
     * 감시 대상 패키지 목록 제공자.
     * 목록이 서버 응답으로 런타임에 갱신되므로 고정 Set이 아니라 매 조회 시 읽어온다.
     * 목록이 비어 있으면(아직 못 받아옴) 어떤 앱도 감지하지 않는다.
     */
    private val watchedPackagesProvider: () -> Set<String>,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** 직전 조회 구간의 끝 시각. 다음 조회는 여기서부터 이어서 본다. */
    private var lastQueriedTime: Long = System.currentTimeMillis()

    /** 패키지별 마지막 감지(트리거) 시각 — 쿨다운 판정용 */
    private val lastTriggeredAt = mutableMapOf<String, Long>()

    /**
     * 직전 호출 이후 발생한 이벤트를 조회해서,
     * 감시 대상 앱이 새로 포그라운드로 올라온 경우 그 패키지명을 반환한다.
     * 감지된 게 없으면 null.
     */
    fun pollLaunchedWatchedApp(): String? {
        val watchedPackages = watchedPackagesProvider()
        val now = System.currentTimeMillis()
        // 이벤트 기록 지연에 대비해 직전 구간과 살짝 겹치게 조회한다 (중복은 쿨다운이 걸러줌)
        val events = usageStatsManager.queryEvents(lastQueriedTime - QUERY_OVERLAP_MILLIS, now)
        lastQueriedTime = now

        var detectedPackage: String? = null
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val resumed = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED

            @Suppress("DEPRECATION")
            val legacyForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

            if (!resumed && !legacyForeground) continue

            // ── 감지 로그: 어떤 앱이 포그라운드로 왔는지 전부 기록 ──
            Log.d(TAG, "포그라운드 진입 감지: ${event.packageName} (t=${event.timeStamp})")

            val pkg = event.packageName ?: continue
            if (pkg !in watchedPackages) continue

            val last = lastTriggeredAt[pkg] ?: 0L
            if (event.timeStamp - last < cooldownMillis) {
                Log.d(TAG, "쿨다운 중이라 무시: $pkg")
                continue
            }

            lastTriggeredAt[pkg] = event.timeStamp
            Log.i(TAG, "★ 감시 대상 앱 실행 감지: $pkg")
            detectedPackage = pkg
        }
        return detectedPackage
    }

    companion object {
        private const val TAG = "AppLaunchDetector"
        private const val DEFAULT_COOLDOWN_MILLIS = 30_000L
        private const val QUERY_OVERLAP_MILLIS = 1_000L
    }
}
