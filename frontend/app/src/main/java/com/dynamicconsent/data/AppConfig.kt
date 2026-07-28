package com.dynamicconsent.data

/**
 * 데이터 소스 전환 설정.
 *
 * 백엔드 서버가 배포되거나 로컬에서 실행되면 [USE_REMOTE_API]를 true로 바꾸면 실 API를 사용한다.
 * 그 전까지는 assets mock JSON(Dummy)으로 동작해 데모·오프라인에서도 앱이 정상 구동된다.
 */
object AppConfig {

    /** true면 실 서버 API, false면 mock JSON. 서버 준비 후 이 한 줄만 바꾸면 된다. */
    const val USE_REMOTE_API = false

    /**
     * 백엔드 base URL.
     * - 에뮬레이터에서 호스트 PC의 localhost:8080 → 10.0.2.2:8080
     * - 배포 후에는 실제 서버 주소로 교체
     */
    const val BASE_URL = "http://10.0.2.2:8080/"
}
