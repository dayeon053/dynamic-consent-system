package com.dynamicconsent.data.remote

import com.dynamicconsent.data.remote.dto.CompanyResponse
import com.dynamicconsent.data.remote.dto.ConsentItemResponse
import com.dynamicconsent.data.remote.dto.ConsentPatchRequest
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import retrofit2.http.Body
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
     * 동의 체크 상태 변경. 본문의 `checked`에 **원하는 상태**를 명시해 멱등하게 저장한다.
     *
     * 서버는 본문을 생략하면 기존 반전(toggle) 방식으로도 동작하지만(하위호환),
     * 앱은 요청 유실·재시도 시 화면과 서버가 어긋나지 않도록 항상 본문을 보낸다.
     */
    @PATCH("users/{userId}/consents/{consentItemId}")
    suspend fun patchConsent(
        @Path("userId") userId: Long,
        @Path("consentItemId") consentItemId: Long,
        @Body body: ConsentPatchRequest,
    ): ConsentPatchResponse
}
