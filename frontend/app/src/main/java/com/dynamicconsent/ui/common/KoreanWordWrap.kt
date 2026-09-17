package com.dynamicconsent.ui.common

/** 폭이 0인 결합 문자. 앞뒤 글자 사이에서 줄이 바뀌지 않게 막는다. */
private const val WORD_JOINER = '\u2060'

/**
 * 어절이 중간에서 잘리지 않도록 어절 안쪽 글자들을 묶는다.
 *
 * 한글은 줄바꿈 기준이 글자 단위라, 좁은 칸에서는 "제3자 제공 동의"가
 * "제3자 제공 동 / 의"처럼 단어 한가운데서 끊긴다. Compose의
 * `LineBreak`(WordBreak.Phrase)가 이 용도지만 API 33 미만에서는 무시되고
 * 이 앱의 minSdk는 26이라, 공백을 뺀 글자 사이마다 word joiner를 넣어
 * **공백에서만** 줄이 바뀌게 한다.
 *
 * 어절 하나가 한 줄보다 길면 플랫폼이 원래대로 글자 단위로 끊으므로 글자가 잘려 사라지지는 않는다.
 * 넣는 문자는 폭이 0이라 화면에 보이지 않고, 복사하면 따라붙으므로 **표시용 문자열에만** 쓴다.
 */
fun keepWordsWhole(text: String): String = buildString(text.length * 2) {
    text.forEachIndexed { index, char ->
        if (index > 0 && !char.isWhitespace() && !text[index - 1].isWhitespace()) {
            append(WORD_JOINER)
        }
        append(char)
    }
}
