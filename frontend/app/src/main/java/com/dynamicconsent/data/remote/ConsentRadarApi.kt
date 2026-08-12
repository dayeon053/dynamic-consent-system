package com.dynamicconsent.data.remote

import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentHistoryResponse
import com.dynamicconsent.data.remote.dto.ConsentItemResponse
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
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
     * 이 사용자의 동의 변경 이력 전체 (모든 기업, 변경 시각 오름차순).
     * 기업별 필터는 클라이언트에서 한다 — 서버에 companyId 파라미터가 없다.
     */
    @GET("users/{userId}/consents/history")
    suspend fun getConsentHistory(
        @Path("userId") userId: Long,
    ): List<ConsentHistoryResponse>

    /**
     * 동의 체크 상태 토글. 요청 본문 없음 — 서버가 현재 상태를 반전시킨다.
     * 원하는 상태를 명시하는 방식(멱등)이 안전해 백엔드에 개선 제안 중.
     */
    @PATCH("users/{userId}/consents/{consentItemId}")
    suspend fun patchConsent(
        @Path("userId") userId: Long,
        @Path("consentItemId") consentItemId: Long,
    ): ConsentPatchResponse
}
