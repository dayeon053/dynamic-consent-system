package com.consentradar.consentradar.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig가 실제로 적용됐는지 확인하는 통합 테스트.
 * {@code @WebMvcTest} 슬라이스로는 우리가 만든 커스텀 {@link SecurityConfig}(일반
 * {@code @Configuration})가 자동으로 로드되지 않아(직접 확인함 — AdminControllerTest는
 * 인증 없이도 통과했다) 보안 규칙이 실제로 걸려있는지 검증이 안 된다. 그래서 전체
 * 컨텍스트(@SpringBootTest)로 띄워서 확인한다. 기본 `./gradlew test`에서는 제외되고
 * `./gradlew integrationTest`로만 실행된다.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void existingApi_isAccessibleWithoutAuthentication() throws Exception {
        // GET /companies는 permitAll — 로그인 없는 PoC 상태 유지가 목적. 로컬 DB에 어떤 데이터가
        // 있는지에 따라 실제 상태 코드(200/그 외)는 달라질 수 있으니, "인증/인가로 막히지 않는다"
        // (401/403이 아니다)는 것만 확인한다.
        MvcResult result = mockMvc.perform(get("/companies").param("userId", "1")).andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status != 401 && status != 403,
                "permitAll 대상인 기존 API가 인증/인가로 막히면 안 된다 (실제 status=" + status + ")");
    }

    @Test
    void adminApi_rejectsRequestWithoutCredentials() throws Exception {
        mockMvc.perform(post("/admin/companies")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApi_rejectsRequestWithWrongCredentials() throws Exception {
        mockMvc.perform(post("/admin/companies")
                        .with(httpBasic("admin", "wrong-password"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminApi_acceptsRequestWithAdminCredentials() throws Exception {
        // 기본 admin 계정(admin/application.yml의 admin.security.password 기본값)으로 인증되면
        // 컨트롤러까지 도달한다 — 여기서는 인가(ROLE_ADMIN) 통과만 확인하면 되므로, 바디 검증
        // 실패(400)로 끝나도 401/403이 아니라는 것만으로 ROLE_ADMIN 통과를 증명할 수 있다.
        mockMvc.perform(delete("/admin/companies/999999")
                        .with(httpBasic("admin", "local-dev-only-CHANGE-ME")))
                .andExpect(status().isNotFound()); // 존재하지 않는 companyId -> 404 (401/403이 아님 == 인가 통과)
    }
}
