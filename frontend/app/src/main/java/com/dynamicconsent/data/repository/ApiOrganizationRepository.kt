package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.remote.CompanyMapper
import com.dynamicconsent.data.remote.ConsentRadarApi
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
     * 스위치 토글을 서버에 반영하고, 서버가 재산출한 위험도를 돌려받는다.
     * 서버는 토글(반전) 방식이므로 응답의 checked를 신뢰 기준으로 화면 상태를 보정할 것.
     */
    suspend fun patchConsent(consentItemId: Int): ConsentPatchResponse =
        api.patchConsent(userId = userId, consentItemId = consentItemId.toLong())

    /** 다음 조회 때 서버에서 다시 받아오도록 캐시를 비운다. */
    fun invalidateCache() {
        cache = null
    }

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
