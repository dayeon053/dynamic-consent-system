package com.dynamicconsent.data.remote

import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentItemResponse
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import com.dynamicconsent.data.remote.dto.NoticeResponse
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 백엔드 REST API 정의 (ConsentApiController 기준 확정).
 */
interface ConsentRadarApi {

    @GET("companies")
    suspend fun getCompanies(
        @Query("userId") userId: Long,
        @Query("sort") sort: String = "risk_score_desc",
    ): List<CompanyResponse>

    /** 기업의 필수/선택 동의 항목 전체 (5대 변수 + 사용자 체크 상태) */
    @GET("companies/{companyId}/consent-items")
    suspend fun getConsentItems(
        @Path("companyId") companyId: Long,
        @Query("userId") userId: Long,
    ): List<ConsentItemResponse>

    /**
     * 동의 체크 상태 토글. 요청 본문 없음 — 서버가 현재 상태를 반전시킨다.
     * 원하는 상태를 명시하는 방식(멱등)이 안전해 백엔드에 개선 제안 중.
     */
    @PATCH("users/{userId}/consents/{consentItemId}")
    suspend fun patchConsent(
        @Path("userId") userId: Long,
        @Path("consentItemId") consentItemId: Long,
    ): ConsentPatchResponse

    /**
     * 전체 기업의 약관 확인 기록 (공지사항 탭). 확인 시각 내림차순, offset 페이징.
     * 사용자별이 아니라 전체 공통이라 userId를 받지 않는다.
     */
    @GET("notices")
    suspend fun getNotices(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): List<NoticeResponse>
}
