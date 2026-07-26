package com.consentradar.consentradar.admin;

/** company 등록/삭제가 상태 충돌로 불가능할 때(패키지명 중복, 연관 데이터 존재 등) 던진다. 컨트롤러에서 409로 매핑한다. */
public class CompanyConflictException extends RuntimeException {

    public CompanyConflictException(String message) {
        super(message);
    }
}
