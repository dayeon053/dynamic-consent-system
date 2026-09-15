package com.dynamicconsent.domain

/**
 * 서버 카테고리를 사용자에게 보여줄 묶음으로 정리한다.
 *
 * 서버는 `금융(은행)`, `금융(증권)`, `금융(생명보험)`처럼 괄호로 세부 분류를 붙여 내려준다.
 * 40개 기업 기준 22종이라 그대로 바로가기로 만들면 한 줄에 다 들어가지 않고,
 * 사용자 입장에서도 "금융"을 9번 따로 고르는 건 의미가 없다. 괄호 앞 이름으로 묶는다.
 */
object CategoryGroups {

    /** 예: "금융(증권)" → "금융", "여가/숙박" → "여가/숙박" */
    fun groupOf(category: String): String =
        category.substringBefore('(').trim().ifEmpty { category.trim() }

    /**
     * 카테고리 목록을 묶음 이름으로 정리하고, 기업이 많은 묶음부터 앞에 둔다.
     * 개수가 같으면 처음 나온 순서를 지켜 화면이 새로고침마다 뒤바뀌지 않게 한다.
     */
    fun groupsByPopularity(categories: List<String>): List<String> =
        categories
            .map(::groupOf)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
}
