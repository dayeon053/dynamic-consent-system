package com.dynamicconsent.monitor

import android.util.Log
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.repository.OrganizationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 감시 대상 앱 레지스트리.
 *
 * 기관 목록(mock 또는 실 API)에서 `packageName`이 있는 기업만 골라
 * `패키지명 → 기관 id` 매핑을 런타임에 구성한다. 하드코딩 맵을 쓰지 않으므로
 * 데이터 소스가 바뀌어도(mock의 "kakaotalk" ↔ 실 API의 "1") 매핑이 항상 실제 데이터와 일치한다.
 *
 * 갱신 실패 시에는 마지막으로 성공한 매핑을 그대로 유지하며,
 * 서버가 계속 죽어 있어도 폴링 루프가 매 초 재시도하지 않도록 최소 재시도 간격을 둔다.
 * (프로세스 재시작 시 매핑이 사라지는 문제는 영구 캐시로 별도 해결 예정)
 */
class WatchedAppRegistry(
    private val repository: OrganizationRepository,
    private val minRetryIntervalMillis: Long = DEFAULT_MIN_RETRY_INTERVAL_MILLIS,
    private val nowProvider: () -> Long = System::currentTimeMillis,
    /** 로그 출력. 단위 테스트에서는 android.util.Log를 쓸 수 없어 주입 가능하게 둔다. */
    private val logger: (message: String, error: Throwable?) -> Unit = { message, error ->
        if (error != null) Log.w(TAG, message, error) else Log.i(TAG, message)
    },
) {

    private val mutex = Mutex()

    @Volatile
    private var packageToOrgId: Map<String, String> = emptyMap()

    /** 마지막 갱신 시도 시각. 실패 후 과도한 재시도를 막는 데 쓴다. */
    private var lastAttemptAt: Long? = null

    /** 현재 감시해야 할 패키지 목록. 아직 매핑을 못 받았으면 비어 있다. */
    val watchedPackages: Set<String>
        get() = packageToOrgId.keys

    /** 매핑이 아직 없으면 true — 이 상태에서 감지된 앱은 무시된다. */
    val isEmpty: Boolean
        get() = packageToOrgId.isEmpty()

    /** 패키지명에 대응하는 기관 id. 감시 대상이 아니면 null. */
    fun orgIdFor(packageName: String): String? = packageToOrgId[packageName]

    /**
     * 기관 목록을 다시 받아와 매핑을 갱신한다.
     *
     * @param force true면 재시도 간격을 무시하고 즉시 시도한다(서비스 시작 시 등).
     * @return 갱신 후 매핑이 비어 있지 않으면 true
     */
    suspend fun refresh(force: Boolean = false): Boolean {
        if (!force && !shouldRetry()) return !isEmpty

        return mutex.withLock {
            lastAttemptAt = nowProvider()
            try {
                val organizations = repository.getOrganizations().organizations
                val mapping = buildMapping(organizations)
                if (mapping.isEmpty()) {
                    logger("감시 대상 없음: packageName이 등록된 기업이 하나도 없습니다", null)
                } else {
                    logger("감시 대상 갱신: ${mapping.size}개 (${mapping.keys.joinToString()})", null)
                }
                packageToOrgId = mapping
                mapping.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 실패해도 직전 매핑을 유지한다 — 감시가 통째로 멈추는 것보다 낫다.
                logger("감시 대상 갱신 실패, 기존 매핑 유지 (${packageToOrgId.size}개)", e)
                !isEmpty
            }
        }
    }

    /** 매핑이 비어 있고 재시도 간격이 지났을 때만 다시 시도한다. */
    private fun shouldRetry(): Boolean {
        if (!isEmpty) return false
        val last = lastAttemptAt ?: return true
        return nowProvider() - last >= minRetryIntervalMillis
    }

    private fun buildMapping(organizations: List<Organization>): Map<String, String> =
        organizations
            .mapNotNull { org -> org.packageName?.takeIf { it.isNotBlank() }?.let { it to org.id } }
            .toMap()

    companion object {
        private const val TAG = "WatchedAppRegistry"

        /** 매핑이 빈 상태에서 서버가 계속 실패할 때의 최소 재시도 간격 */
        const val DEFAULT_MIN_RETRY_INTERVAL_MILLIS = 60_000L
    }
}
