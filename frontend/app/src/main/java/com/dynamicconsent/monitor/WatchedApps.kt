package com.dynamicconsent.monitor

/**
 * 감시 대상 앱 정의.
 *
 * 패키지명 → 기관 ID 매핑이며, 기관 ID는 mock/organizations.json 의 id 와 일치해야 한다.
 * 추후 백엔드에서 감시 대상 목록을 내려주면 이 하드코딩을 API 응답으로 교체한다.
 */
object WatchedApps {

    /** 실제 카카오톡 패키지명 */
    const val KAKAOTALK_PACKAGE = "com.kakao.talk"

    val packageToOrgId: Map<String, String> = mapOf(
        KAKAOTALK_PACKAGE to "kakaotalk",
        "viva.republica.toss" to "toss",
        "com.netflix.mediaclient.ui" to "netflix",
        "com.netflix.mediaclient" to "netflix",
    )

    val watchedPackages: Set<String>
        get() = packageToOrgId.keys
}
