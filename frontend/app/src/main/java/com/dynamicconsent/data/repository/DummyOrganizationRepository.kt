package com.dynamicconsent.data.repository

import android.content.res.AssetManager
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import kotlinx.serialization.json.Json

/**
 * 실제 API가 붙기 전까지 사용하는 임시 데이터 소스.
 * assets/mock 폴더의 JSON 파일을 파싱해서 반환하며, 이 JSON은 추후 실제 API 응답 포맷의 초안 역할을 한다.
 * TODO: 실제 API 연동 구현체로 교체
 */
class DummyOrganizationRepository(
    private val assets: AssetManager,
) : OrganizationRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val organizations: List<Organization> by lazy {
        val text = assets.open(ORGANIZATIONS_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(text)
    }

    private val details: Map<String, OrganizationDetail> by lazy {
        val text = assets.open(ORGANIZATION_DETAILS_ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(text)
    }

    override suspend fun getOrganizations(): OrganizationsResult = OrganizationsResult(organizations)

    override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = details[id]

    private companion object {
        const val ORGANIZATIONS_ASSET = "mock/organizations.json"
        const val ORGANIZATION_DETAILS_ASSET = "mock/organization_details.json"
    }
}
