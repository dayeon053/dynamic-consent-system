package com.dynamicconsent.monitor

import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.repository.OrganizationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 패키지명 → 기관 id 매핑이 데이터(mock/실 API)에서 올바르게 구성되는지 검증한다.
 * 기관 id 체계가 달라도(mock "kakaotalk" ↔ 실 API "1") 매핑이 항상 데이터와 일치해야 한다.
 */
class WatchedAppRegistryTest {

    private fun org(id: String, packageName: String?, name: String = "기관$id") = Organization(
        id = id,
        packageName = packageName,
        name = name,
        category = "기타",
        riskScore = 10.0,
        riskGrade = RiskGrade.LOW,
        logoText = name.take(1),
        logoColor = 0xFF000000L,
    )

    /** 지정한 목록을 돌려주는 저장소. 호출 횟수를 세고, 실패 모드를 흉내 낼 수 있다. */
    private class FakeRepository(
        var organizations: List<Organization> = emptyList(),
        var shouldFail: Boolean = false,
    ) : OrganizationRepository {
        var callCount = 0
            private set

        override suspend fun getOrganizations(): List<Organization> {
            callCount++
            if (shouldFail) throw IllegalStateException("network down")
            return organizations
        }

        override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = null
    }

    @Test
    fun `packageName이 있는 기업만 감시 대상으로 구성된다`() = runTest {
        val repo = FakeRepository(
            listOf(
                org("1", "com.kakao.talk"),
                org("2", "viva.republica.toss"),
                org("3", null), // 패키지명 미등록 → 제외
            ),
        )
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)

        assertTrue(registry.refresh())

        assertEquals(setOf("com.kakao.talk", "viva.republica.toss"), registry.watchedPackages)
        assertEquals("1", registry.orgIdFor("com.kakao.talk"))
        assertEquals("2", registry.orgIdFor("viva.republica.toss"))
    }

    @Test
    fun `mock 모드의 문자열 id도 그대로 매핑된다`() = runTest {
        val repo = FakeRepository(listOf(org("kakaotalk", "com.kakao.talk")))
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)

        registry.refresh()

        // 실 API("1")든 mock("kakaotalk")이든 데이터의 id를 그대로 쓴다
        assertEquals("kakaotalk", registry.orgIdFor("com.kakao.talk"))
    }

    @Test
    fun `감시 대상이 아닌 패키지는 null을 반환한다`() = runTest {
        val repo = FakeRepository(listOf(org("1", "com.kakao.talk")))
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)
        registry.refresh()

        assertNull(registry.orgIdFor("com.instagram.android"))
        // 액티비티 경로처럼 패키지명이 아닌 값도 매칭되지 않아야 한다
        assertNull(registry.orgIdFor("com.netflix.mediaclient.ui"))
    }

    @Test
    fun `빈 packageName 문자열은 감시 대상에서 제외된다`() = runTest {
        val repo = FakeRepository(listOf(org("1", "  "), org("2", "com.kakao.talk")))
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)

        registry.refresh()

        assertEquals(setOf("com.kakao.talk"), registry.watchedPackages)
    }

    @Test
    fun `조회 실패 시 직전 매핑을 유지한다`() = runTest {
        val repo = FakeRepository(listOf(org("1", "com.kakao.talk")))
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)
        registry.refresh()

        repo.shouldFail = true
        val result = registry.refresh(force = true)

        assertTrue("직전 매핑이 남아 있으면 사용 가능", result)
        assertEquals("1", registry.orgIdFor("com.kakao.talk"))
    }

    @Test
    fun `첫 조회부터 실패하면 매핑이 비어 감지를 무시한다`() = runTest {
        val repo = FakeRepository(shouldFail = true)
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)

        assertFalse(registry.refresh())

        assertTrue(registry.isEmpty)
        assertNull(registry.orgIdFor("com.kakao.talk"))
    }

    @Test
    fun `매핑이 비었을 때 재시도는 최소 간격을 지킨다`() = runTest {
        var now = 0L
        val repo = FakeRepository(shouldFail = true)
        val registry = WatchedAppRegistry(
            repository = repo,
            minRetryIntervalMillis = 60_000L,
            nowProvider = { now },
            logger = NO_LOG,
        )

        registry.refresh() // 1회차 시도(실패)
        assertEquals(1, repo.callCount)

        now = 30_000L
        registry.refresh() // 간격 미달 → 실제 호출 없음
        assertEquals(1, repo.callCount)

        now = 60_000L
        repo.shouldFail = false
        repo.organizations = listOf(org("1", "com.kakao.talk"))
        registry.refresh() // 간격 경과 → 재시도
        assertEquals(2, repo.callCount)
        assertEquals("1", registry.orgIdFor("com.kakao.talk"))
    }

    @Test
    fun `매핑이 이미 있으면 강제하지 않는 한 다시 호출하지 않는다`() = runTest {
        val repo = FakeRepository(listOf(org("1", "com.kakao.talk")))
        val registry = WatchedAppRegistry(repo, logger = NO_LOG)
        registry.refresh()
        assertEquals(1, repo.callCount)

        registry.refresh() // 이미 매핑 보유 → 호출 없음
        assertEquals(1, repo.callCount)

        registry.refresh(force = true) // 강제 갱신
        assertEquals(2, repo.callCount)
    }

    private companion object {
        /** android.util.Log는 단위 테스트에서 동작하지 않으므로 로그를 삼킨다. */
        val NO_LOG: (String, Throwable?) -> Unit = { _, _ -> }
    }
}
