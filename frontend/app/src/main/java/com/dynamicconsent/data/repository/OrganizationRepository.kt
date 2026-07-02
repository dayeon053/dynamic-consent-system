package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail

/**
 * 기관 데이터 소스 인터페이스.
 * 지금은 [DummyOrganizationRepository]만 존재하지만, 추후 실제 API 연동 구현체로 교체한다.
 */
interface OrganizationRepository {
    suspend fun getOrganizations(): List<Organization>

    suspend fun getOrganizationDetail(id: String): OrganizationDetail?
}
