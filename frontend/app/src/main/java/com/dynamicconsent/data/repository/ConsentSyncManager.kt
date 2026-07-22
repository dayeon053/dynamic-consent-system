package com.dynamicconsent.data.repository

import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 동의 스위치 토글을 서버에 저장할 때 연타를 흡수하는 디바운스 관리자.
 *
 * 사용자가 같은 스위치를 빠르게 여러 번 토글하면 마지막 상태 하나만 PATCH로 전송한다
 * (중간 상태는 취소). 항목이 다르면 서로 독립적으로 전송된다.
 * 화면은 클라이언트 재계산으로 즉시 반응하고, 서버 응답(new_risk_score)은 도착 시 보정용으로 쓴다.
 */
class ConsentSyncManager(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val patch: suspend (consentItemId: Int, enabled: Boolean) -> ConsentPatchResponse,
) {

    private val pendingJobs = mutableMapOf<Int, Job>()

    fun onToggle(
        consentItemId: Int,
        enabled: Boolean,
        onSuccess: (ConsentPatchResponse) -> Unit = {},
        onError: (Throwable) -> Unit = {},
    ) {
        pendingJobs[consentItemId]?.cancel()
        pendingJobs[consentItemId] = scope.launch {
            delay(debounceMillis)
            try {
                onSuccess(patch(consentItemId, enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** 화면 종료 등으로 더 이상 전송이 필요 없을 때 대기 중인 전송을 모두 취소한다. */
    fun cancelAll() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
