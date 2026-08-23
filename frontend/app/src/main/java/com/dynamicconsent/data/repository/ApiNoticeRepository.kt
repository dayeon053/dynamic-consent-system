package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.data.remote.ConsentRadarApi
import com.dynamicconsent.data.remote.NoticeMapper

/** GET /notices 기반 구현체. */
class ApiNoticeRepository(
    private val api: ConsentRadarApi,
) : NoticeRepository {

    override suspend fun getNotices(page: Int, size: Int): List<Notice> =
        NoticeMapper.toNotices(api.getNotices(page = page, size = size))
}
