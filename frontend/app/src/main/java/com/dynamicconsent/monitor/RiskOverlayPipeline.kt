package com.dynamicconsent.monitor

import android.content.Context
import android.util.Log
import com.dynamicconsent.data.repository.DummyOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.overlay.RiskOverlayService

/**
 * [2단계] 오버레이 파이프라인.
 *
 * 감지된 앱 패키지명을 받아
 *   ① 패키지명 → 기관 ID 매핑 (WatchedApps)
 *   ② 기관 위험도 데이터 조회 (OrganizationRepository)
 *   ③ 위험도 오버레이 팝업 표시 (#8 RiskOverlayService)
 * 까지 이어주는 흐름 전체를 담당한다.
 *
 * 데이터 소스는 현재 mock JSON(DummyOrganizationRepository)이며,
 * 백엔드 REST API 연동 시 repository 구현체만 교체하면 파이프라인 코드는 그대로 유지된다.
 */
class RiskOverlayPipeline(
    private val appContext: Context,
    private val repository: OrganizationRepository =
        DummyOrganizationRepository(appContext.assets),
) {

    /**
     * 패키지명에 해당하는 기관의 위험도를 조회해 오버레이를 띄운다.
     * @return 오버레이 표시까지 성공하면 true
     */
    suspend fun showOverlayFor(packageName: String): Boolean {
        Log.d(TAG, "파이프라인 시작: package=$packageName")

        val orgId = WatchedApps.packageToOrgId[packageName]
        if (orgId == null) {
            Log.w(TAG, "매핑된 기관 없음: $packageName")
            return false
        }

        val org = repository.getOrganizations().firstOrNull { it.id == orgId }
        if (org == null) {
            Log.w(TAG, "위험도 데이터 없음: orgId=$orgId")
            return false
        }

        Log.i(TAG, "위험도 조회 완료: ${org.name} score=${org.riskScore} grade=${org.riskGrade} → 오버레이 표시")
        RiskOverlayService.start(appContext, org.riskScore, org.riskGrade)
        return true
    }

    private companion object {
        const val TAG = "RiskOverlayPipeline"
    }
}
