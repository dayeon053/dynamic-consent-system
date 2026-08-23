package com.dynamicconsent.data.repository

import android.content.res.AssetManager
import com.dynamicconsent.data.AppConfig
import com.dynamicconsent.data.remote.ApiClient

/**
 * ViewModel이 사용할 OrganizationRepository를 한 곳에서 결정한다.
 * [AppConfig.USE_REMOTE_API] 값에 따라 실 API 구현체 또는 mock 구현체를 반환한다.
 */
object RepositoryProvider {

    /** 실 API 사용 시에는 앱 전역에서 같은 인스턴스를 공유(캐시 재사용)한다. */
    private val apiRepository: ApiOrganizationRepository by lazy {
        ApiOrganizationRepository(ApiClient.create(AppConfig.BASE_URL))
    }

    private val apiNoticeRepository: ApiNoticeRepository by lazy {
        ApiNoticeRepository(ApiClient.create(AppConfig.BASE_URL))
    }

    fun organizationRepository(assets: AssetManager): OrganizationRepository =
        if (AppConfig.USE_REMOTE_API) apiRepository else DummyOrganizationRepository(assets)

    fun noticeRepository(assets: AssetManager): NoticeRepository =
        if (AppConfig.USE_REMOTE_API) apiNoticeRepository else DummyNoticeRepository(assets)

    /** 실 API 모드일 때 서버 동기화(PATCH)에 쓸 저장소. mock 모드면 null. */
    fun apiRepositoryOrNull(): ApiOrganizationRepository? =
        if (AppConfig.USE_REMOTE_API) apiRepository else null
}
