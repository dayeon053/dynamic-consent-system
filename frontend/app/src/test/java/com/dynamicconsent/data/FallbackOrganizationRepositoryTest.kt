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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 서버가 죽었을 때 mock으로 대신 채우되, 그 사실을 숨기지 않는지 검증한다 (요구사항 10-B).
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
        companyInfo = CompanyInfo(
            serviceName = name,
            legalName = name,
            privacyCertification = "-",
            privacyPolicyUrl = "",
        ),
    )

    /** 항상 실패하는 원격 저장소 */
    private class DeadRemote : OrganizationRepository {
        override suspend fun getOrganizations(): List<Organization> = throw IOException("server down")
        override suspend fun getOrganizationDetail(id: String): OrganizationDetail? =
            throw IOException("server down")
    }

    private inner class FakeRepository(
        private val organizations: List<Organization>,
        private val details: Map<String, OrganizationDetail> = emptyMap(),
    ) : OrganizationRepository {
        var detailCalls = 0
            private set

        override suspend fun getOrganizations(): List<Organization> = organizations
        override suspend fun getOrganizationDetail(id: String): OrganizationDetail? {
            detailCalls++
            return details[id]
        }
    }

    private val mockOrgs = listOf(organization("kakaotalk", "카카오톡"))
    private val serverOrgs = listOf(organization("1", "카카오"))

    @Test
    fun `서버가 살아있으면 서버 값을 쓰고 폴백 표시를 하지 않는다`() = runTest {
        val repository = FallbackOrganizationRepository(
            remote = FakeRepository(serverOrgs),
            fallback = FakeRepository(mockOrgs),
        )

        assertEquals(listOf("카카오"), repository.getOrganizations().map { it.name })
        assertFalse(repository.isFallback)
    }

    @Test
    fun `서버가 실패하면 mock으로 채우고 폴백을 표시한다`() = runTest {
        val repository = FallbackOrganizationRepository(
            remote = DeadRemote(),
            fallback = FakeRepository(mockOrgs),
        )

        assertEquals(listOf("카카오톡"), repository.getOrganizations().map { it.name })
        assertTrue(repository.isFallback)
    }

    @Test
    fun `서버가 다시 살아나면 폴백 표시가 꺼진다`() = runTest {
        var alive = false
        val flaky = object : OrganizationRepository {
            override suspend fun getOrganizations(): List<Organization> =
                if (alive) serverOrgs else throw IOException("server down")

            override suspend fun getOrganizationDetail(id: String): OrganizationDetail? = null
        }
        val repository = FallbackOrganizationRepository(flaky, FakeRepository(mockOrgs))

        repository.getOrganizations()
        assertTrue(repository.isFallback)

        alive = true
        repository.getOrganizations()
        assertFalse(repository.isFallback)
    }

    @Test
    fun `폴백 중에는 상세도 mock에서 읽는다`() = runTest {
        val fallback = FakeRepository(mockOrgs, mapOf("kakaotalk" to detail("kakaotalk", "카카오톡")))
        val repository = FallbackOrganizationRepository(DeadRemote(), fallback)

        repository.getOrganizations()
        val result = repository.getOrganizationDetail("kakaotalk")

        assertEquals("카카오톡", result?.organization?.name)
        assertEquals(1, fallback.detailCalls)
    }

    @Test
    fun `목록이 서버에서 왔으면 상세 실패는 mock으로 감추지 않는다`() = runTest {
        // id 체계가 서로 달라(서버 "1" vs mock "kakaotalk") mock으로 넘기면 엉뚱한 결과가 된다.
        // 그럴 바에는 오류를 그대로 알리고 재시도하게 두는 편이 정확하다.
        val fallback = FakeRepository(mockOrgs, mapOf("kakaotalk" to detail("kakaotalk", "카카오톡")))
        val flaky = object : OrganizationRepository {
            override suspend fun getOrganizations(): List<Organization> = serverOrgs
            override suspend fun getOrganizationDetail(id: String): OrganizationDetail? =
                throw IOException("server down")
        }
        val repository = FallbackOrganizationRepository(flaky, fallback)

        repository.getOrganizations()

        var thrown = false
        try {
            repository.getOrganizationDetail("1")
        } catch (e: IOException) {
            thrown = true
        }

        assertTrue("상세 실패는 호출자에게 전달되어야 한다", thrown)
        assertEquals("mock 상세를 건드리면 안 된다", 0, fallback.detailCalls)
    }

    @Test
    fun `서버가 빈 목록을 주면 폴백하지 않는다`() = runTest {
        // 빈 응답은 실패가 아니다. 이때 mock으로 채우면 없는 기업을 있는 것처럼 보여준다.
        val repository = FallbackOrganizationRepository(
            remote = FakeRepository(emptyList()),
            fallback = FakeRepository(mockOrgs),
        )

        assertTrue(repository.getOrganizations().isEmpty())
        assertFalse(repository.isFallback)
    }

    @Test
    fun `취소는 폴백으로 삼키지 않는다`() = runTest {
        val cancelling = object : OrganizationRepository {
            override suspend fun getOrganizations(): List<Organization> =
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
        assertFalse(repository.isFallback)
    }

    @Test
    fun `폴백이 없는 구현체는 항상 false다`() = runTest {
        val plain: OrganizationRepository = FakeRepository(serverOrgs)

        assertFalse(plain.isFallback)
        assertNull(plain.getOrganizationDetail("없는id"))
    }
}
