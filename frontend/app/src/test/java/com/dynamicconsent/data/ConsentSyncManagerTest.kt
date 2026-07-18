package com.dynamicconsent.data

import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.remote.dto.ConsentPatchResponse
import com.dynamicconsent.data.repository.ConsentSyncManager
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentSyncManagerTest {

    private fun response(score: Double) = ConsentPatchResponse(newRiskScore = score, grade = RiskGrade.MEDIUM)

    @Test
    fun `같은 항목을 연타하면 마지막 상태 하나만 전송된다`() = runTest {
        val sent = mutableListOf<Pair<Int, Boolean>>()
        val manager = ConsentSyncManager(scope = this) { id, enabled ->
            sent += id to enabled
            response(15.0)
        }

        manager.onToggle(1, false)
        manager.onToggle(1, true)
        manager.onToggle(1, false)
        advanceUntilIdle()

        assertEquals(listOf(1 to false), sent)
    }

    @Test
    fun `항목이 다르면 각각 전송된다`() = runTest {
        val sent = mutableListOf<Pair<Int, Boolean>>()
        val manager = ConsentSyncManager(scope = this) { id, enabled ->
            sent += id to enabled
            response(15.0)
        }

        manager.onToggle(1, false)
        manager.onToggle(2, true)
        advanceUntilIdle()

        assertEquals(setOf(1 to false, 2 to true), sent.toSet())
    }

    @Test
    fun `전송 성공 시 서버 응답이 콜백으로 전달된다`() = runTest {
        var received: ConsentPatchResponse? = null
        val manager = ConsentSyncManager(scope = this) { _, _ -> response(23.0) }

        manager.onToggle(1, false, onSuccess = { received = it })
        advanceUntilIdle()

        assertEquals(23.0, received!!.newRiskScore, 0.0)
    }

    @Test
    fun `전송 실패 시 에러 콜백이 호출된다`() = runTest {
        var failed = false
        val manager = ConsentSyncManager(scope = this) { _, _ -> throw IllegalStateException("network") }

        manager.onToggle(1, false, onError = { failed = true })
        advanceUntilIdle()

        assertTrue(failed)
    }

    @Test
    fun `cancelAll 이후에는 아무것도 전송되지 않는다`() = runTest {
        val sent = mutableListOf<Int>()
        val manager = ConsentSyncManager(scope = this) { id, _ ->
            sent += id
            response(15.0)
        }

        manager.onToggle(1, false)
        manager.cancelAll()
        advanceUntilIdle()

        assertTrue(sent.isEmpty())
    }
}
