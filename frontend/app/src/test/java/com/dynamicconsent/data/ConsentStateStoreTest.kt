package com.dynamicconsent.data

import com.dynamicconsent.data.repository.ConsentStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConsentStateStoreTest {

    @Before
    fun setUp() = ConsentStateStore.reset()

    @After
    fun tearDown() = ConsentStateStore.reset()

    @Test
    fun `initialize는 이미 상태가 있는 기관을 덮어쓰지 않는다`() {
        ConsentStateStore.initialize("kakaotalk", setOf(1, 2))
        ConsentStateStore.initialize("kakaotalk", setOf(3))

        assertEquals(setOf(1, 2), ConsentStateStore.enabledConsents.value.getValue("kakaotalk"))
    }

    @Test
    fun `setConsent는 동의 상태를 갱신하고 변경 기록을 최신순으로 쌓는다`() {
        ConsentStateStore.initialize("kakaotalk", setOf(1, 2))

        ConsentStateStore.setConsent("kakaotalk", 2, false, "이벤트 및 마케팅 활용 동의")
        ConsentStateStore.setConsent("kakaotalk", 3, true, "위치정보 수집 및 이용 동의")

        assertEquals(setOf(1, 3), ConsentStateStore.enabledConsents.value.getValue("kakaotalk"))

        val history = ConsentStateStore.changeHistory.value.getValue("kakaotalk")
        assertEquals(2, history.size)
        assertEquals("위치정보 수집 및 이용 동의", history[0].consentTitle)
        assertTrue(history[0].enabled)
        assertEquals("이벤트 및 마케팅 활용 동의", history[1].consentTitle)
        assertFalse(history[1].enabled)
    }

    @Test
    fun `변경 기록은 기관별로 분리되어 저장된다`() {
        ConsentStateStore.setConsent("kakaotalk", 1, false, "프로필정보 추가 수집 동의")
        ConsentStateStore.setConsent("toss", 1, false, "마케팅 정보 수신 동의")

        assertEquals(1, ConsentStateStore.changeHistory.value.getValue("kakaotalk").size)
        assertEquals(1, ConsentStateStore.changeHistory.value.getValue("toss").size)
    }
}
