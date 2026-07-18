package com.dynamicconsent.data.remote

import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentPatchRequest
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 백엔드 REST API 정의 (Sprint 02 계획 명세 기준).
 * Controller 구현이 올라오면 경로·쿼리 이름을 대조해 확정한다.
 */
interface ConsentRadarApi {

    @GET("companies")
    suspend fun getCompanies(
        @Query("user_id") userId: Long,
        @Query("sort") sort: String = "risk_score_desc",
    ): List<CompanyResponse>

    @PATCH("users/{userId}/consents/{consentItemId}")
    suspend fun patchConsent(
        @Path("userId") userId: Long,
        @Path("consentItemId") consentItemId: Long,
        @Body body: ConsentPatchRequest,
    ): ConsentPatchResponse
}
