package com.consentradar.consentradar.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 동시 수정 경합(유니크 제약 위반 / 낙관적 락 실패)이 발생한 트랜잭션을 처음부터 다시
 * 시도한다.
 *
 * 반드시 "이미 끝난(커밋되었거나 롤백된) 트랜잭션 호출"을 재시도해야 한다 — 실패한
 * 트랜잭션의 세션을 이어서 재시도할 수는 없다({@code DataIntegrityViolationException}/
 * {@code ObjectOptimisticLockingFailureException} 발생 시 해당 Hibernate 세션은 더 이상
 * 사용할 수 없다). 그래서 이 클래스는 컨트롤러처럼 트랜잭션 경계 바깥에서, {@code @Transactional}
 * 서비스 메서드를 프록시를 통해 호출하는 supplier를 받아 실행한다 — 매 시도가 완전히 새
 * 트랜잭션이 된다.
 */
@Component
public class ConcurrentUpdateRetrier {

    private static final int MAX_ATTEMPTS = 5;

    public <T> T retry(Supplier<T> action) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new IllegalStateException(
                            "동시 수정 경합으로 " + MAX_ATTEMPTS + "회 연속 실패했습니다.", e);
                }
                // 다음 시도는 완전히 새 트랜잭션에서 경합 상대의 최신 커밋을 다시 읽는다.
            }
        }
        throw new IllegalStateException("도달할 수 없는 코드");
    }
}
