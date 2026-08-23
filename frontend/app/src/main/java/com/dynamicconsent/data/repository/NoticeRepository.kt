package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Notice

/**
 * 약관 변경 알림(공지사항) 데이터 소스.
 * [AppConfig.USE_REMOTE_API]에 따라 [ApiNoticeRepository] 또는 [DummyNoticeRepository]가 주입된다.
 */
interface NoticeRepository {
    /** 확인 시각 내림차순 목록. [page]는 0부터 시작한다. */
    suspend fun getNotices(page: Int = 0, size: Int = DEFAULT_PAGE_SIZE): List<Notice>

    companion object {
        /** 백엔드 기본값과 동일 (GET /notices의 size 기본값 20) */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
