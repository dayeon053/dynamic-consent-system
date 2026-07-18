package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.remote.CompanyMapper
import com.dynamicconsent.data.remote.ConsentRadarApi
import com.dynamicconsent.data.remote.dto.ConsentPatchRequest
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse

/**
 * 백엔드 REST API 기반 구현체.
 * 서버 배포 후 ViewModel의 DummyOrganizationRepository를 이 클래스로 교체하면 된다.
 * (교체 시 스위치 토글 → patchConsent 호출 → 응답 new_risk_score로 보정하는 흐름 연결)
 */
class ApiOrganizationRepository(
    private val api: ConsentRadarApi,
    private val userId: Long = DEMO_USER_ID,
) : OrganizationRepository {

    private var cache: Map<String, OrganizationDetail>? = null

    override suspend fun getOrganizations(): List<Organization> =
        loadAll().values
            .map { it.organization }
            .sortedByDescending { it.riskScore }

    override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = loadAll()[id]

    /** 스위치 토글을 서버에 저장하고, 서버가 재산출한 위험도를 돌려받는다. */
    suspend fun patchConsent(consentItemId: Int, enabled: Boolean): ConsentPatchResponse =
        api.patchConsent(
            userId = userId,
            consentItemId = consentItemId.toLong(),
            body = ConsentPatchRequest(isChecked = enabled),
        )

    /** 다음 조회 때 서버에서 다시 받아오도록 캐시를 비운다. */
    fun invalidateCache() {
        cache = null
    }

    private suspend fun loadAll(): Map<String, OrganizationDetail> =
        cache ?: api.getCompanies(userId)
            .map(CompanyMapper::toOrganizationDetail)
            .associateBy { it.organization.id }
            .also { cache = it }

    companion object {
        /** 로그인 기능 전까지 사용하는 데모 사용자 id */
        const val DEMO_USER_ID = 1L
    }
}
