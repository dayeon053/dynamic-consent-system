package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.ConsentChangeRecord
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.remote.CompanyMapper
import com.dynamicconsent.data.remote.ConsentHistoryMapper
import com.dynamicconsent.data.remote.ConsentRadarApi
import com.dynamicconsent.data.remote.dto.ConsentPatchRequest
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse

/**
 * 백엔드 REST API 기반 구현체.
 * AppConfig.USE_REMOTE_API를 켜면 RepositoryProvider가 이 클래스를 주입한다.
 *
 * 기업 요약(GET /companies)과 동의 항목(GET /companies/{id}/consent-items)이 별도 호출이라,
 * 상세 데이터는 두 응답을 합쳐 만든다. 3~5개 기업 기준이라 목록 진입 시 한 번에 로드하고 캐시한다.
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

    /**
     * 스위치 상태를 서버에 반영하고, 서버가 재산출한 위험도를 돌려받는다.
     * [checked]에 원하는 상태를 실어 보내므로 같은 요청이 중복돼도 결과가 같다(멱등).
     */
    suspend fun patchConsent(consentItemId: Int, checked: Boolean): ConsentPatchResponse =
        api.patchConsent(
            userId = userId,
            consentItemId = consentItemId.toLong(),
            body = ConsentPatchRequest(checked = checked),
        )

    /** 다음 조회 때 서버에서 다시 받아오도록 캐시를 비운다. */
    fun invalidateCache() {
        cache = null
    }

    /**
     * 동의 변경 내역 탭(4-7)이 쓰는 이력 조회. 2-8(GET /users/{userId}/consents/history)을
     * 단일 소스로 사용한다 — PATCH 응답엔 changed_at이 없다(api_spec_v2_final.md 확정
     * 사항 4번). 서버 응답은 전체 기업 통합이라 [orgId]로 걸러서 반환한다.
     */
    suspend fun getConsentHistory(orgId: String): List<ConsentChangeRecord> =
        ConsentHistoryMapper.toChangeRecords(api.getConsentHistory(userId), orgId.toLong())

    private suspend fun loadAll(): Map<String, OrganizationDetail> {
        cache?.let { return it }
        val companies = api.getCompanies(userId)
        val details = companies.associate { company ->
            val items = api.getConsentItems(company.companyId, userId)
            company.companyId.toString() to CompanyMapper.toOrganizationDetail(company, items)
        }
        cache = details
        return details
    }

    companion object {
        /** 로그인 기능 전까지 사용하는 데모 사용자 id */
        const val DEMO_USER_ID = 1L
    }
}
