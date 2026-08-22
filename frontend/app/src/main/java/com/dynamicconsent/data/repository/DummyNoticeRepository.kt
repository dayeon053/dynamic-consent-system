package com.dynamicconsent.data.repository

import android.content.res.AssetManager
import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.data.remote.NoticeMapper
import com.dynamicconsent.data.remote.dto.NoticeResponse
import kotlinx.serialization.json.Json

/**
 * mock 모드용 공지사항 데이터 소스.
 *
 * 다른 mock과 달리 화면 모델이 아니라 **서버 응답 DTO**([NoticeResponse]) 형태로 JSON을 두고
 * 실 API와 같은 [NoticeMapper]를 태운다. 덕분에 mock 시연에서도 시각 해석·정렬 로직이 함께 검증된다.
 */
class DummyNoticeRepository(
    private val assets: AssetManager,
) : NoticeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val notices: List<Notice> by lazy {
        val text = assets.open(NOTICES_ASSET).bufferedReader().use { it.readText() }
        NoticeMapper.toNotices(json.decodeFromString<List<NoticeResponse>>(text))
    }

    override suspend fun getNotices(page: Int, size: Int): List<Notice> =
        notices.drop(page * size).take(size)

    private companion object {
        const val NOTICES_ASSET = "mock/notices.json"
    }
}
