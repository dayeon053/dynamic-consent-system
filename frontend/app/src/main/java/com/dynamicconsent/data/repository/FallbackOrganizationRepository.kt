package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import kotlinx.coroutines.CancellationException

/**
 * 실 서버 조회가 실패하면 mock 데이터로 대신 채우는 래퍼 (요구사항 10-B).
 *
 * 시연 중 서버가 흔들릴 때 첫 화면이 통째로 비는 것을 막는 게 목적이다.
 * 폴백이 일어났는지는 [isFallback]으로 알 수 있고, 화면은 이 값을 보고 "오프라인 데이터"임을 밝힌다.
 *
 * **상세 조회는 목록이 폴백된 경우에만 mock으로 간다.** 기관 id 체계가 두 소스에서 다르기 때문이다
 * (실 API는 companyId 문자열 "1", mock은 "kakaotalk"). 목록은 서버에서 받았는데 상세만 mock으로
 * 넘기면 id가 맞지 않아 "기업을 찾을 수 없습니다"가 뜬다 — 그 경우엔 차라리 오류를 그대로 알리고
 * 재시도하게 두는 편이 정확하다.
 */
class FallbackOrganizationRepository(
    private val remote: OrganizationRepository,
    private val fallback: OrganizationRepository,
) : OrganizationRepository {

    /** 오버레이(서비스 스레드)와 화면(메인 스레드)이 함께 읽으므로 @Volatile로 둔다. */
    @Volatile
    private var fallbackActive = false

    override val isFallback: Boolean get() = fallbackActive

    override suspend fun getOrganizations(): List<Organization> = try {
        remote.getOrganizations().also { fallbackActive = false }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fallbackActive = true
        fallback.getOrganizations()
    }

    override suspend fun getOrganizationDetail(id: String): OrganizationDetail? =
        if (fallbackActive) {
            fallback.getOrganizationDetail(id)
        } else {
            remote.getOrganizationDetail(id)
        }
}
