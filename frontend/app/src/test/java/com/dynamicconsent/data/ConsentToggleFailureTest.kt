package com.dynamicconsent.data

import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import com.dynamicconsent.data.repository.ConsentStateStore
import com.dynamicconsent.data.repository.ConsentSyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * 토글을 서버에 저장하다 실패하거나, 서버가 요청과 다른 상태를 돌려줬을 때의 보정을 검증한다.
 * OrgDetailViewModel은 Android 의존성이 있어 유닛 테스트에서 만들 수 없으므로,
 * ViewModel이 ConsentSyncManager 콜백에서 하는 일과 같은 조합으로 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsentToggleFailureTest {

    private val orgId = "1"
    private val consentId = 12

    @Before
    fun setUp() = ConsentStateStore.reset()

    @After
    fun tearDown() = ConsentStateStore.reset()

    /** 전송이 실패하면 화면 상태를 되돌리고 안내 문구를 만든다 (ViewModel의 onError와 같은 동작). */
    private fun revertOnError(enabled: Boolean): String {
        ConsentStateStore.correctConsent(orgId, consentId, !enabled)
        return "동의 변경을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."
    }

    @Test
    fun `전송 실패하면 화면 상태를 원래대로 되돌린다`() = runTest {
        ConsentStateStore.initialize(orgId, setOf(consentId))
        var message: String? = null
        val sync = ConsentSyncManager(scope = this) { _, _ -> throw IOException("network down") }

        // 사용자가 껐다 → 화면은 즉시 꺼짐
        ConsentStateStore.setConsent(orgId, consentId, false, "맞춤형 광고 동의")
        assertTrue(consentId !in ConsentStateStore.enabledConsents.value.getValue(orgId))

        sync.onToggle(consentId, enabled = false, onError = { message = revertOnError(false) })
        advanceUntilIdle()

        // 서버는 여전히 켜진 상태이므로 화면도 되돌아와야 한다
        assertTrue(consentId in ConsentStateStore.enabledConsents.value.getValue(orgId))
        assertEquals("동의 변경을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.", message)
    }

    @Test
    fun `되돌릴 때는 변경 이력을 남기지 않는다`() = runTest {
        ConsentStateStore.initialize(orgId, setOf(consentId))
        val sync = ConsentSyncManager(scope = this) { _, _ -> throw IOException("network down") }

        ConsentStateStore.setConsent(orgId, consentId, false, "맞춤형 광고 동의")
        sync.onToggle(consentId, enabled = false, onError = { revertOnError(false) })
        advanceUntilIdle()

        // 사용자가 누른 1건만 남고, 되돌린 것은 이력에 없다
        val history = ConsentStateStore.changeHistory.value.getValue(orgId)
        assertEquals(1, history.size)
        assertEquals("맞춤형 광고 동의", history.single().consentTitle)
    }

    @Test
    fun `서버가 요청과 다른 상태를 주면 서버 값으로 맞춘다`() = runTest {
        ConsentStateStore.initialize(orgId, setOf(consentId))
        var message: String? = null
        // 요청은 끄기(false)인데 서버는 켜짐(true)을 돌려주는 상황
        val sync = ConsentSyncManager(scope = this) { itemId, _ ->
            ConsentPatchResponse(consentItemId = itemId.toLong(), checked = true)
        }

        ConsentStateStore.setConsent(orgId, consentId, false, "맞춤형 광고 동의")
        sync.onToggle(
            consentId,
            enabled = false,
            onSuccess = { response ->
                if (response.checked != false) {
                    ConsentStateStore.correctConsent(orgId, consentId, response.checked)
                    message = "서버에 반영된 상태로 맞췄습니다."
                }
            },
        )
        advanceUntilIdle()

        assertTrue(consentId in ConsentStateStore.enabledConsents.value.getValue(orgId))
        assertEquals("서버에 반영된 상태로 맞췄습니다.", message)
    }

    @Test
    fun `서버 응답이 요청과 같으면 아무것도 보정하지 않는다`() = runTest {
        ConsentStateStore.initialize(orgId, setOf(consentId))
        var message: String? = null
        val sync = ConsentSyncManager(scope = this) { itemId, enabled ->
            ConsentPatchResponse(consentItemId = itemId.toLong(), checked = enabled)
        }

        ConsentStateStore.setConsent(orgId, consentId, false, "맞춤형 광고 동의")
        sync.onToggle(
            consentId,
            enabled = false,
            onSuccess = { response ->
                if (response.checked != false) {
                    ConsentStateStore.correctConsent(orgId, consentId, response.checked)
                    message = "서버에 반영된 상태로 맞췄습니다."
                }
            },
        )
        advanceUntilIdle()

        assertTrue(consentId !in ConsentStateStore.enabledConsents.value.getValue(orgId))
        assertNull(message)
    }

    @Test
    fun `correctConsent는 이력을 남기지 않고 상태만 바꾼다`() {
        ConsentStateStore.initialize(orgId, emptySet())

        ConsentStateStore.correctConsent(orgId, consentId, enabled = true)

        assertTrue(consentId in ConsentStateStore.enabledConsents.value.getValue(orgId))
        assertTrue(ConsentStateStore.changeHistory.value[orgId].isNullOrEmpty())
    }
}
