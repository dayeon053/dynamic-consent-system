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

    /**
     * 직전 조회가 실 서버가 아니라 **mock 폴백**으로 채워졌는지.
     *
     * 시연 중 서버가 흔들려도 화면이 비지 않도록 폴백하되, 화면 쪽에서 "오프라인 데이터"임을
     * 밝힐 수 있게 노출한다(요구사항 10-B, 3안). 특히 오버레이는 사용자가 직접 연 화면이 아니라
     * 갑자기 뜨는 팝업이고 mock은 사용자의 철회가 반영되지 않은 초기값이라, 라벨 없이 보여주면
     * 틀린 점수를 사실처럼 보여주게 된다.
     *
     * 읽는 법: [getOrganizations] 호출 **직후**에 확인한다.
     * 폴백이 없는 구현체(mock 전용 / 실 API 전용)는 항상 false다.
     */
    val isFallback: Boolean get() = false
}
