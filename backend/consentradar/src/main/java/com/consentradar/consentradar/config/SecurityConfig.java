package com.consentradar.consentradar.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * PoC 단계 임시 관리자 인증/인가 설정.
 *
 * [설계 의도 — 실제 인증(User 테이블 + 로그인) 붙기 전까지의 임시 방식]
 * 이 프로젝트의 User 테이블은 아직 프론트 팀과 스키마를 확정 중이라, 여기서 로그인
 * 자체(토큰 발급, 세션 등)를 구현하지 않는다. 대신 "관리자 여부"만 최소한으로 확인하기
 * 위해 Spring Security의 HTTP Basic + 고정 in-memory 계정(ROLE_ADMIN) 하나를 둔다.
 * - 계정 정보는 하드코딩하지 않고 application.yml(admin.security.*)에서 읽는다. DB 접속
 *   정보(spring.datasource.*)와 같은 방식 — 기본값은 로컬 개발 전용 placeholder이고,
 *   배포 환경에서는 ADMIN_USERNAME/ADMIN_PASSWORD 환경변수로 반드시 교체해야 한다.
 *   이 프로젝트는 아직 profile(local/prod 등) 분리를 안 쓰고 application.yml 하나로
 *   가는 구조라, 강제로 막는(fail-fast) 대신 기본값이 그대로면 시작 시 WARN 로그만 남긴다
 *   (profile 분리로 완전히 강제하는 방향은 별도 논의 필요 — 지금 범위 아님).
 * - 나중에 User 테이블 기반 실제 로그인이 붙으면, 이 InMemoryUserDetailsManager 빈을
 *   User 리포지토리를 조회하는 UserDetailsService 구현으로 교체하고, 로그인 성공 시
 *   ROLE_ADMIN 권한을 실제 User.role(또는 별도 관리자 테이블)에서 부여하도록 바꾸면 된다.
 *   컨트롤러/서비스 코드는 건드릴 필요 없이 이 클래스만 교체하도록 의도했다.
 * - `/admin/**`(company 등록/삭제, 크롤링 수동 트리거 등)만 ROLE_ADMIN을 요구하고, 그 외
 *   기존 API는 로그인 자체가 없는 PoC 상태를 유지하기 위해 permitAll로 둔다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** application.yml의 admin.security.password 기본값과 반드시 같은 문자열로 유지할 것. */
    private static final String LOCAL_DEV_ONLY_PASSWORD_PLACEHOLDER = "local-dev-only-CHANGE-ME";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 브라우저 세션/폼 로그인이 없는 순수 REST API라 CSRF 토큰을 발급/검증할 대상이 없다.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.security.username}") String adminUsername,
            @Value("${admin.security.password}") String adminPassword,
            PasswordEncoder passwordEncoder) {
        if (LOCAL_DEV_ONLY_PASSWORD_PLACEHOLDER.equals(adminPassword)) {
            log.warn("[SecurityConfig] admin.security.password가 로컬 개발용 기본값({})입니다. "
                            + "배포 환경이라면 ADMIN_PASSWORD 환경변수로 반드시 교체하세요.",
                    LOCAL_DEV_ONLY_PASSWORD_PLACEHOLDER);
        }

        UserDetails admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
