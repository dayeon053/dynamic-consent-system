package com.consentradar.consentradar.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A-2 (api_spec_v2_final.md 확정 사항 5번): 존재하지 않는 userId로 PATCH 호출 시
 * {@link com.consentradar.consentradar.common.GlobalExceptionHandler}가
 * {@link IllegalArgumentException}을 HTTP 404 + {"message": "..."}로 변환하는지 검증한다.
 *
 * {@link ConcurrentUpdateRetrier}는 실제 재시도 로직 없이 supplier를 그대로 한 번 실행하도록
 * 목업한다 — 재시도 자체는 {@code ConcurrentUpdateRetrierTest}가 이미 검증하므로 여기서는
 * "서비스가 던진 예외가 결국 컨트롤러 밖으로 어떤 HTTP 응답으로 나가는지"만 확인한다.
 */
@WebMvcTest(ConsentApiController.class)
class ConsentApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentApiService consentApiService;

    @MockitoBean
    private ConcurrentUpdateRetrier retrier;

    @Test
    void toggleConsent_returns404WithSharedErrorFormat_whenUserNotFound() throws Exception {
        when(retrier.retry(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(consentApiService.toggleConsent(eq(99999L), eq(1L), isNull()))
                .thenThrow(new IllegalArgumentException("존재하지 않는 userId: 99999"));

        mockMvc.perform(patch("/users/99999/consents/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 userId: 99999"));
    }

    @Test
    void toggleConsent_returns404WithSharedErrorFormat_whenConsentItemNotFound() throws Exception {
        when(retrier.retry(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(consentApiService.toggleConsent(eq(1L), eq(88888L), isNull()))
                .thenThrow(new IllegalArgumentException("존재하지 않는 consentItemId: 88888"));

        mockMvc.perform(patch("/users/1/consents/88888"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("존재하지 않는 consentItemId: 88888"));
    }
}
