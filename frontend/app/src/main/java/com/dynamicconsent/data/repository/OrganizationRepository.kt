package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail

/**
 * 기관 목록 조회 결과.
 *
 * [isFallback]을 전역 상태가 아니라 **이번 호출의 결과로** 함께 돌려주는 이유:
 * 화면(메인 스레드)과 오버레이(서비스 스레드)가 서로 독립적으로 조회하기 때문이다.
 * 공유 플래그로 두면 한쪽 조회가 실패한 순간 다른 쪽 화면까지 "오프라인"으로 표시되거나,
 * 반대로 폴백으로 받은 목록에 "정상" 표시가 붙는다.
 */
data class OrganizationsResult(
    val organizations: List<Organization>,
    /** 서버 대신 mock 데이터로 채워졌는지. 이 호출에 한정된 값이다. */
    val isFallback: Boolean = false,
)

/**
 * 기관 데이터 소스 인터페이스.
 * 구현체는 mock([DummyOrganizationRepository]), 실 API([ApiOrganizationRepository]),
 * 그리고 둘을 묶어 실패 시 mock으로 떨어지는 [FallbackOrganizationRepository]가 있다.
 */
interface OrganizationRepository {
    suspend fun getOrganizations(): OrganizationsResult

    suspend fun getOrganizationDetail(id: String): OrganizationDetail?
}
