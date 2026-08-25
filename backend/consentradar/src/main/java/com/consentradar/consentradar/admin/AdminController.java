package com.consentradar.consentradar.admin;

import com.consentradar.consentradar.admin.dto.CompanyResponse;
import com.consentradar.consentradar.admin.dto.CreateCompanyRequest;
import com.consentradar.consentradar.common.ErrorResponse;
import com.consentradar.consentradar.entity.Company;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 1-9 크롤링 대상 기업 관리(관리자) API.
 *
 * PoC 단계라 이 클래스 자체엔 인증/권한 체크가 없다 — SecurityConfig의 /admin/** 규칙으로
 * 막는다(태스크 B). 응답은 이 프로젝트의 실제 컨벤션(ConsentApiController 등)을 따라
 * ResponseEntity&lt;DTO&gt;를 래핑 없이 그대로 반환한다.
 */
@RestController
public class AdminController {

    private final AdminCompanyService adminCompanyService;

    public AdminController(AdminCompanyService adminCompanyService) {
        this.adminCompanyService = adminCompanyService;
    }

    /** POST /admin/companies */
    @PostMapping("/admin/companies")
    public ResponseEntity<?> createCompany(@RequestBody CreateCompanyRequest request) {
        try {
            Company company = adminCompanyService.createCompany(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(new CompanyResponse(company));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        } catch (CompanyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
        }
    }

    /** DELETE /admin/companies/{companyId} */
    @DeleteMapping("/admin/companies/{companyId}")
    public ResponseEntity<?> deleteCompany(@PathVariable Long companyId) {
        try {
            adminCompanyService.deleteCompany(companyId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (CompanyConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
        }
    }
}
