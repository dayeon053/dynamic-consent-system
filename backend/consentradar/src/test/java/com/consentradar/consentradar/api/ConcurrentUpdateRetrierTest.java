package com.consentradar.consentradar.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentUpdateRetrierTest {

    private final ConcurrentUpdateRetrier retrier = new ConcurrentUpdateRetrier();

    @Test
    void retry_returnsResult_whenActionSucceedsOnFirstTry() {
        String result = retrier.retry(() -> "ok");

        assertEquals("ok", result);
    }

    @Test
    void retry_retriesAfterUniqueConstraintViolation_andSucceedsOnNextAttempt() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = retrier.retry(() -> {
            if (callCount.incrementAndGet() == 1) {
                throw new DataIntegrityViolationException("동시 insert로 유니크 제약 위반 시뮬레이션");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, callCount.get(), "1번째 시도 실패 후 2번째 시도에서 성공해야 한다");
    }

    @Test
    void retry_retriesAfterOptimisticLockFailure_andSucceedsOnNextAttempt() {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = retrier.retry(() -> {
            if (callCount.incrementAndGet() <= 2) {
                throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
            }
            return "recovered-after-two-conflicts";
        });

        assertEquals("recovered-after-two-conflicts", result);
        assertEquals(3, callCount.get());
    }

    @Test
    void retry_givesUpAndThrows_whenConflictPersistsForAllAttempts() {
        assertThrows(IllegalStateException.class, () -> retrier.retry(() -> {
            throw new DataIntegrityViolationException("항상 실패");
        }));
    }

    @Test
    void retry_doesNotSwallowUnrelatedExceptions() {
        assertThrows(IllegalArgumentException.class, () -> retrier.retry(() -> {
            throw new IllegalArgumentException("경합과 무관한 에러는 재시도 없이 그대로 전파돼야 한다");
        }));
    }
}
