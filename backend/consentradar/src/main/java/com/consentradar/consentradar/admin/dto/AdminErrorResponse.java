package com.consentradar.consentradar.admin.dto;

/** admin API 에러 응답(400/404/409 공용) */
public class AdminErrorResponse {

    private final String message;

    public AdminErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
}
