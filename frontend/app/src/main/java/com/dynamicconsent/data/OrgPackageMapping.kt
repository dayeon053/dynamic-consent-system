package com.dynamicconsent.data

/**
 * 백그라운드 앱 실행 감지 결과(안드로이드 패키지명)를 우리 데이터의 기관 id로 변환하는 매핑.
 * 오버레이 팝업 파트(프론트 B)가 감지한 앱의 위험도를 조회하고 딥링크를 만들 때 사용한다.
 * TODO: 실제 API 연동 시 서버가 내려주는 기관-패키지 매핑으로 교체
 */
object OrgPackageMapping {

    private val packageToOrgId = mapOf(
        "com.kakao.talk" to "kakaotalk",
        "viva.republica.toss" to "toss",
        "com.netflix.mediaclient" to "netflix",
    )

    /** 감지 대상 앱이면 기관 id를, 아니면 null을 반환한다. */
    fun orgIdFor(packageName: String): String? = packageToOrgId[packageName]

    /** 감지 대상 패키지 목록 (UsageStats 폴링 필터용). */
    val monitoredPackages: Set<String> = packageToOrgId.keys
}
