package com.dynamicconsent.data

import com.dynamicconsent.data.model.CompanyInfo
import com.dynamicconsent.data.model.ConsentDetail
import com.dynamicconsent.data.model.MaxEffect
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.RiskAnalysis
import com.dynamicconsent.data.model.RiskGrade
import com.dynamicconsent.data.repository.FallbackOrganizationRepository
import com.dynamicconsent.data.repository.OrganizationRepository
import com.dynamicconsent.data.repository.OrganizationsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 서버가 죽었을 때 mock으로 대신 채우되, 그 사실을 숨기지 않는지 검증한다 (요구사항 10-B).
 *
 * 폴백 여부를 공유 필드가 아니라 호출 결과로 돌려주므로, 화면과 오버레이가 동시에 조회해도
 * 서로의 결과가 섞이지 않아야 한다 — 마지막 두 테스트가 그것을 확인한다.
 */
class FallbackOrganizationRepositoryTest {

    private fun organization(id: String, name: String) = Organization(
        id = id,
        packageName = "com.example.$id",
        name = name,
        category = "SNS",
        riskScore = 20.0,
        riskGrade = RiskGrade.MEDIUM,
        logoText = name.take(1),
        logoColor = 0xFF000000L,
    )

    private fun detail(id: String, name: String) = OrganizationDetail(
        organization = organization(id, name),
        consentDetail = ConsentDetail(optionalConsents = emptyList(), requiredConsents = emptyList()),
        riskAnalysis = RiskAnalysis(
            riskScore = 20.0,
            riskGrade = RiskGrade.MEDIUM,
            formula = "",
            factors = emptyList(),
            withdrawalEffects = emptyList(),
            maxEffect = MaxEffect("", RiskGrade.MEDIUM, "", RiskGrade.MEDIUM, ""),
        ),
        companyInfo = CompanyInfo(name, name, "-", ""),
    )

    /** 항상 실패하는 원격 저장소 */
    private class DeadRemote : OrganizationRepository {
        override suspend fun getOrganizations(): OrganizationsResult = throw IOException("server down")
        override suspend fun getOrganizationDetail(id: String): OrganizationDetail? =
            throw IOException("server down")
    }

    private class FakeRepository(
        private val organizations: List<Organization>,
        private val details: Map<String, OrganizationDetail> = emptyMap(),
    ) : OrganizationRepository {
        var detailCalls = 0
            private set

        override suspend fun getOrganizations() = OrganizationsResult(organizations)
        override suspend fun getOrganizationDetail(id: String): OrganizationDetail? {
            detailCalls++
            return details[id]
        }
    }

    // mock은 슬러그 id, 서버는 companyId 문자열 — 두 체계는 겹치지 않는다.
    private val mockOrgs = listOf(organization("kakaotalk", "카카오톡"))
    private val mockDetails = mapOf("kakaotalk" to detail("kakaotalk", "카카오톡"))
    private val serverOrgs = listOf(organization("1", "카카오"))

    @Test
    fun `서버가 살아있으면 서버 값을 쓰고 폴백 표시를 하지 않는다`() = runTest {
        val repository = FallbackOrganizationRepository(
            remote = FakeRepository(serverOrgs),
            fallback = FakeRepository(mockOrgs),
        )

        val result = repository.getOrganizations()

        assertEquals(listOf("카카오"), result.organizations.map { it.name })
        assertFalse(result.isFallback)
    }

    @Test
    fun `서버가 실패하면 mock으로 채우고 폴백을 표시한다`() = runTest {
        val repository = FallbackOrganizationRepository(
            remote = DeadRemote(),
            fallback = FakeRepository(mockOrgs),
        )

        val result = repository.getOrganizations()

        assertEquals(listOf("카카오톡"), result.organizations.map { it.name })
        assertTrue(result.isFallback)
    }

    @Test
    fun `서버가 빈 목록을 주면 폴백하지 않는다`() = runTest {
        // 빈 응답은 실패가 아니다. 이때 mock으로 채우면 없는 기업을 있는 것처럼 보여준다.
        val repository = FallbackOrganizationRepository(
            remote = FakeRepository(emptyList()),
            fallback = FakeRepository(mockOrgs),
        )

        val result = repository.getOrganizations()

        assertTrue(result.organizations.isEmpty())
        assertFalse(result.isFallback)
    }

    @Test
    fun `취소는 폴백으로 삼키지 않는다`() = runTest {
        val cancelling = object : OrganizationRepository {
            override suspend fun getOrganizations(): OrganizationsResult =
                throw CancellationException("화면 이탈")

            override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = null
        }
        val repository = FallbackOrganizationRepository(cancelling, FakeRepository(mockOrgs))

        var thrown = false
        try {
            repository.getOrganizations()
        } catch (e: CancellationException) {
            thrown = true
        }

        assertTrue("코루틴 취소는 그대로 전파되어야 한다", thrown)
    }

    @Test
    fun `mock id로 들어온 상세는 mock에서 읽는다`() = runTest {
        val fallback = FakeRepository(mockOrgs, mockDetails)
        val repository = FallbackOrganizationRepository(DeadRemote(), fallback)

        val result = repository.getOrganizationDetail("kakaotalk")

        assertEquals("카카오톡", result?.organization?.name)
    }

    @Test
    fun `서버 id로 들어온 상세는 서버로 간다`() = runTest {
        val fallback = FakeRepository(mockOrgs, mockDetails)
        val remote = FakeRepository(serverOrgs, mapOf("1" to detail("1", "카카오")))
        val repository = FallbackOrganizationRepository(remote, fallback)

        val result = repository.getOrganizationDetail("1")

        assertEquals("카카오", result?.organization?.name)
        assertEquals(1, remote.detailCalls)
    }

    @Test
    fun `서버 id 상세가 실패하면 mock으로 감추지 않고 그대로 알린다`() = runTest {
        // mock에 없는 id를 mock 결과(null)로 돌려주면 "기업을 찾을 수 없습니다"로 둔갑해 원인이 가려진다.
        val repository = FallbackOrganizationRepository(DeadRemote(), FakeRepository(mockOrgs, mockDetails))

        var thrown = false
        try {
            repository.getOrganizationDetail("1")
        } catch (e: IOException) {
            thrown = true
        }

        assertTrue("서버 조회 실패는 호출자에게 전달되어야 한다", thrown)
    }

    @Test
    fun `한쪽 조회가 실패해도 다른 쪽 조회 결과는 영향을 받지 않는다`() = runTest {
        // 화면은 서버로, 오버레이는 실패해 mock으로 — 같은 저장소를 써도 결과가 섞이면 안 된다.
        var failNext = false
        val flaky = object : OrganizationRepository {
            override suspend fun getOrganizations(): OrganizationsResult =
                if (failNext) throw IOException("server down") else OrganizationsResult(serverOrgs)

            override suspend fun getOrganizationDetail(id: String) =
                detail("1", "카카오")
        }
        val repository = FallbackOrganizationRepository(flaky, FakeRepository(mockOrgs, mockDetails))

        val live = repository.getOrganizations()
        failNext = true
        val fellBack = repository.getOrganizations()
        failNext = false
        val liveAgain = repository.getOrganizations()

        assertFalse("서버에서 받은 결과", live.isFallback)
        assertTrue("mock으로 떨어진 결과", fellBack.isFallback)
        assertFalse("다시 서버에서 받은 결과", liveAgain.isFallback)
    }

    @Test
    fun `폴백이 일어난 뒤에도 서버 id 상세는 서버로 간다`() = runTest {
        // 예전 구현은 전역 플래그가 켜지면 서버 id까지 mock으로 보내 null을 돌려줬다.
        val remote = FakeRepository(serverOrgs, mapOf("1" to detail("1", "카카오")))
        val failingOnce = object : OrganizationRepository {
            var listCalls = 0
            override suspend fun getOrganizations(): OrganizationsResult {
                listCalls++
                if (listCalls == 1) throw IOException("server down")
                return remote.getOrganizations()
            }

            override suspend fun getOrganizationDetail(id: String) = remote.getOrganizationDetail(id)
        }
        val repository = FallbackOrganizationRepository(failingOnce, FakeRepository(mockOrgs, mockDetails))

        assertTrue(repository.getOrganizations().isFallback)

        val result = repository.getOrganizationDetail("1")

        assertEquals("카카오", result?.organization?.name)
    }
}
