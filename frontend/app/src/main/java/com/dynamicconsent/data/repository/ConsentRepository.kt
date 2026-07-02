package com.dynamicconsent.data.repository

import com.dynamicconsent.data.model.ConsentItem

/**
 * 동의 항목 데이터 소스 인터페이스.
 * 지금은 [DummyConsentRepository]만 존재하지만, 추후 실제 API 연동 구현체로 교체한다.
 */
interface ConsentRepository {
    suspend fun getConsentItems(): List<ConsentItem>

    suspend fun getConsentItem(id: String): ConsentItem?
}
