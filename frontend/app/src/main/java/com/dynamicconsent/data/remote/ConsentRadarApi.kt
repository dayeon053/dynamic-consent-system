package com.dynamicconsent.data.remote

import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 백엔드 REST API 정의 (PR #18 ConsentApiController 기준 확정).
 */
interface ConsentRadarApi {

    @GET("companies")
    suspend fun getCompanies(
        @Query("userId") userId: Long,
        @Query("sort") sort: String = "risk_score_desc",
    ): List<CompanyResponse>

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
