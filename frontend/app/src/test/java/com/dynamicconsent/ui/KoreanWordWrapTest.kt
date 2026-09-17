package com.dynamicconsent.ui

import com.dynamicconsent.ui.common.keepWordsWhole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KoreanWordWrapTest {

    /** 폭이 0인 word joiner (U+2060) */
    private val joiner = '⁠'

    @Test
    fun `어절 안쪽 글자 사이에만 결합 문자를 넣는다`() {
        assertEquals("제${joiner}공 동${joiner}의", keepWordsWhole("제공 동의"))
    }

    @Test
    fun `공백 자리에는 넣지 않아 그 자리에서만 줄이 바뀐다`() {
        val wrapped = keepWordsWhole("광고 파트너 제3자 제공 동의")

        assertEquals(4, wrapped.count { it == ' ' })
        assertFalse(wrapped.contains("$joiner "))
        assertFalse(wrapped.contains(" $joiner"))
    }

    @Test
    fun `결합 문자를 빼면 원래 문자열과 같다`() {
        val original = "서비스 개선을 위한 통계·분석 활용 동의"

        assertEquals(original, keepWordsWhole(original).replace(joiner.toString(), ""))
    }

    @Test
    fun `빈 문자열과 한 글자는 그대로 둔다`() {
        assertEquals("", keepWordsWhole(""))
        assertEquals("점", keepWordsWhole("점"))
    }

    @Test
    fun `줄바꿈도 공백으로 보고 건너뛴다`() {
        val newline = System.lineSeparator()

        assertEquals("가${joiner}나" + newline + "다${joiner}라", keepWordsWhole("가나" + newline + "다라"))
    }
}
