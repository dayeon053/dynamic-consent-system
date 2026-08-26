package com.dynamicconsent.monitor

import android.content.Context
import android.util.Log
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.data.repository.RepositoryProvider
import com.dynamicconsent.overlay.RiskOverlayService

/**
 * [2단계] 오버레이 파이프라인.
 *
 * 감지된 앱 패키지명을 받아
 *   ① 패키지명 → 기관 ID 매핑 (WatchedAppRegistry)
 *   ② 기관 위험도 데이터 조회 (OrganizationRepository)
 *   ③ 위험도 오버레이 팝업 표시 (RiskOverlayService)
 * 까지 이어주는 흐름 전체를 담당한다.
 *
 * 데이터 소스는 RepositoryProvider가 AppConfig.USE_REMOTE_API에 따라 결정하므로,
 * mock/실 API 어느 쪽이든 기관 id 체계가 데이터와 항상 일치한다.
 */
class RiskOverlayPipeline(
    private val appContext: Context,
    private val repository: OrganizationRepository =
        RepositoryProvider.organizationRepository(appContext.assets),
    private val registry: WatchedAppRegistry = WatchedAppRegistry(repository),
) {

    /**
     * 패키지명에 해당하는 기관의 위험도를 조회해 오버레이를 띄운다.
     * @return 오버레이 표시까지 성공하면 true
     */
    suspend fun showOverlayFor(packageName: String): Boolean {
        Log.d(TAG, "파이프라인 시작: package=$packageName")

        // 매핑이 아직 없으면(첫 실행·서버 실패) 재시도 간격을 지켜 한 번 더 받아온다.
        if (registry.isEmpty) registry.refresh()

        val orgId = registry.orgIdFor(packageName)
        if (orgId == null) {
            Log.w(TAG, "매핑된 기관 없음: $packageName")
            return false
        }

        // result.isFallback = 서버 대신 mock으로 채워진 조회. 오버레이에 "오프라인 데이터"
        // 라벨을 붙일 때 이 값을 RiskOverlayService.start()로 넘기면 된다 (요구사항 10-B ②③).
        val result = repository.getOrganizations()
        val org = result.organizations.firstOrNull { it.id == orgId }
        if (org == null) {
            Log.w(TAG, "위험도 데이터 없음: orgId=$orgId")
            return false
        }

        Log.i(TAG, "위험도 조회 완료: ${org.name} score=${org.riskScore} grade=${org.riskGrade} → 오버레이 표시")
        RiskOverlayService.start(appContext, org.id, org.riskScore, org.riskGrade)
        return true
    }

    /** 감시 대상 목록을 미리 받아둔다(서비스 시작 시 1회). */
    suspend fun prepareWatchedApps(): Boolean = registry.refresh(force = true)

    /** 현재 감시 중인 패키지 목록 (UI 표시·감지기 구성용) */
    val watchedPackages: Set<String>
        get() = registry.watchedPackages

    private companion object {
        const val TAG = "RiskOverlayPipeline"
    }
}
