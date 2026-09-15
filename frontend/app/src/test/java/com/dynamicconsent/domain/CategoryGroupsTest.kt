package com.dynamicconsent.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGroupsTest {

    @Test
    fun `괄호 앞 이름으로 묶는다`() {
        assertEquals("금융", CategoryGroups.groupOf("금융(증권)"))
        assertEquals("금융", CategoryGroups.groupOf("금융(생명보험)"))
        assertEquals("통신", CategoryGroups.groupOf("통신(인터넷/IPTV)"))
        assertEquals("OTT", CategoryGroups.groupOf("OTT(동영상)"))
    }

    @Test
    fun `괄호가 없으면 그대로 둔다`() {
        assertEquals("SNS", CategoryGroups.groupOf("SNS"))
        assertEquals("여가/숙박", CategoryGroups.groupOf("여가/숙박"))
        assertEquals("패션이커머스", CategoryGroups.groupOf("패션이커머스"))
    }

    @Test
    fun `앞뒤 공백은 정리하고 괄호로 시작하면 원래 이름을 쓴다`() {
        assertEquals("금융", CategoryGroups.groupOf(" 금융 (은행)"))
        assertEquals("(기타)", CategoryGroups.groupOf("(기타)"))
    }

    @Test
    fun `기업이 많은 묶음부터 정렬하고 개수가 같으면 처음 나온 순서를 지킨다`() {
        val categories = listOf(
            "SNS", "금융", "포털", "금융(은행)", "금융(증권)", "이커머스", "이커머스(해외직구)", "배달",
        )

        assertEquals(
            listOf("금융", "이커머스", "SNS", "포털", "배달"),
            CategoryGroups.groupsByPopularity(categories),
        )
    }

    @Test
    fun `40개 기업의 22종 카테고리가 13개 묶음으로 줄어든다`() {
        // 2026-09-11 develop 시드(V6·V12~V14) 기준 실제 카테고리
        val categories = listOf(
            "SNS", "포털", "배달", "금융", "중고거래",
            "금융(간편결제)", "SNS", "이커머스", "이커머스", "이커머스", "이커머스", "금융(은행)",
            "배달", "이커머스", "모빌리티", "OTT(동영상)", "커뮤니티", "여가/숙박", "패션이커머스",
            "이커머스(해외직구)", "금융(은행)", "금융(은행)", "금융(증권)", "통신(인터넷/IPTV)",
            "금융(생명보험)", "금융(증권)", "금융(증권)", "금융(생명보험)", "금융(손해보험)", "금융(카드)",
            "금융(증권)", "금융(증권)", "금융(인터넷은행)", "금융(은행)", "금융(은행)", "금융(생명보험)",
            "금융(손해보험)", "금융(상호금융)", "금융(간편결제)", "교통(철도)",
        )

        val groups = CategoryGroups.groupsByPopularity(categories)

        assertEquals(13, groups.size)
        assertEquals("금융", groups.first())
    }
}
