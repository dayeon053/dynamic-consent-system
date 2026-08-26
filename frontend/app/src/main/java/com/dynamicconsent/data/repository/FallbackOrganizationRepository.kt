package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.OrganizationDetail
import kotlinx.coroutines.CancellationException

/**
 * 실 서버 조회가 실패하면 mock 데이터로 대신 채우는 래퍼 (요구사항 10-B).
 *
 * 시연 중 서버가 흔들릴 때 첫 화면이 통째로 비는 것을 막는 게 목적이다.
 * 폴백이 일어났는지는 [OrganizationsResult.isFallback]으로 알 수 있고,
 * 화면은 그 값을 보고 "오프라인 데이터"임을 밝힌다.
 *
 * **이 클래스는 상태를 갖지 않는다.** 화면(메인 스레드)과 오버레이(서비스 스레드)가
 * 동시에 조회하므로, 폴백 여부를 공유 필드에 담으면 서로의 조회 결과가 섞인다.
 * 그래서 폴백 여부는 호출 결과에 실어 보내고, 상세 조회는 id로 갈 곳을 정한다.
 */
class FallbackOrganizationRepository(
    private val remote: OrganizationRepository,
    private val fallback: OrganizationRepository,
) : OrganizationRepository {

    override suspend fun getOrganizations(): OrganizationsResult = try {
        OrganizationsResult(remote.getOrganizations().organizations, isFallback = false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        OrganizationsResult(fallback.getOrganizations().organizations, isFallback = true)
    }

    /**
     * 상세는 **id가 갈 곳을 정한다.** mock과 서버의 id 체계가 겹치지 않기 때문이다 —
     * mock은 슬러그("kakaotalk"), 서버는 companyId 문자열("1"). 그래서 mock에서 찾히면
     * 그건 폴백 목록에서 나온 id이고, 안 찾히면 서버 id다.
     *
     * 이렇게 하면 "지금 폴백 중인가"라는 공유 상태 없이도 항상 맞는 곳으로 간다.
     * 서버 조회 실패는 그대로 호출자에게 전달한다 — 서버 id를 mock에서 찾을 수는 없으므로,
     * 여기서 삼키면 "기업을 찾을 수 없습니다"로 둔갑해 원인이 가려진다.
     */
    override suspend fun getOrganizationDetail(id: String): OrganizationDetail? =
        fallback.getOrganizationDetail(id) ?: remote.getOrganizationDetail(id)
}
