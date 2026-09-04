package com.consentradar.consentradar.entity;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [2026-08-27 회귀 테스트 — V9 마이그레이션 버그] {@code ConsentItem.active}의
 * {@code @Column(name="is_active", nullable=false)}에 {@code columnDefinition}이 없으면,
 * 이 프로젝트는 Flyway/Liquibase 없이 {@code ddl-auto: update}만으로 스키마를 반영하기
 * 때문에 Hibernate가 실제 운영 DB에 {@code ALTER TABLE consent_item ADD COLUMN is_active
 * bit(1) NOT NULL}(DEFAULT 없음)을 그대로 실행해버린다. 이미 row가 있는 테이블에 DEFAULT
 * 없는 NOT NULL 컬럼을 추가하면 MySQL이 기존 row를 그 타입의 암묵적 기본값(BIT는 0)으로
 * 채운다 — 즉 기존 동의 항목이 전부 {@code active=false}로 떨어지는 회귀다.
 * (로컬 MySQL(consentradar-mysql 컨테이너, 8.0.46)에서 실제 재현 확인: is_active 컬럼을
 * 지운 뒤 컬럼 정의를 원복하기 전 상태로 ddl-auto:update를 태우니 기존 24개 row가 전부
 * 0으로 바뀌었다. columnDefinition="BIT(1) DEFAULT 1" 추가 후 같은 절차로 재현하니 기존
 * row가 1로 유지됨을 확인했다.)
 *
 * 이 테스트는 그 재현 절차를 자동화한 것이다: Spring 컨텍스트를 띄우지 않고(공용
 * {@code consentradar} 개발 DB의 스키마/데이터에 영향을 주지 않기 위해) 별도 throwaway
 * 데이터베이스에 실제 {@link ConsentItem} 엔티티 클래스로 두 번 스키마를 빌드한다 — 1차는
 * {@code create}로 전체 스키마(is_active 포함)를 만든 뒤 is_active 컬럼만 지워 "V9 적용
 * 전" 상태 + 기존 데이터를 재현하고, 2차는 {@code update}로(=운영에서 쓰는 것과 동일한
 * 모드) 지금 소스 트리의 {@link ConsentItem} 매핑을 그대로 다시 반영시켜 컬럼을 되살린다.
 * {@code columnDefinition}이 빠진 채로 되돌아가면 이 테스트는 다시 실패한다.
 *
 * 기본 {@code ./gradlew test}에서는 제외되고 {@code ./gradlew integrationTest}로만
 * 실행된다(실제 로컬 MySQL 필요 + throwaway DB 생성/삭제 권한 필요).
 */
@Tag("integration")
class ConsentItemIsActiveMigrationIntegrationTest {

    private static final String SERVER_URL =
            "jdbc:mysql://localhost:3306/?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    private static final String TEST_DB = "consentradar_v9_migration_regression_test";
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/" + TEST_DB + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "1234";

    @BeforeAll
    static void createThrowawayDatabase() throws Exception {
        try (Connection conn = DriverManager.getConnection(SERVER_URL, DB_USER, DB_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
            st.execute("CREATE DATABASE " + TEST_DB + " CHARACTER SET utf8mb4");
        }
    }

    @AfterAll
    static void dropThrowawayDatabase() throws Exception {
        try (Connection conn = DriverManager.getConnection(SERVER_URL, DB_USER, DB_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + TEST_DB);
        }
    }

    @Test
    void ddlAutoUpdate_keepsExistingRowsActive_whenIsActiveColumnIsReaddedToAPopulatedTable() throws Exception {
        // 1단계: 실제 엔티티 매핑(현재 소스 트리의 ConsentItem 포함)으로 throwaway DB에
        // 전체 스키마를 만든다 — 이 시점에는 is_active 컬럼도 정상 생성된다.
        buildSessionFactory("create").close();

        long companyId;
        long firstItemId;
        long secondItemId;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // 2단계: "V9 적용 전부터 있던 기존 데이터"를 흉내낸다 — is_active가 아직 있는
            // 상태에서 active=true인 row 2건을 심어둔다.
            companyId = insertCompany(conn);
            firstItemId = insertConsentItem(conn, companyId, "서비스 이용을 위한 필수 개인정보 수집");
            secondItemId = insertConsentItem(conn, companyId, "마케팅 정보 수신 동의");

            // 3단계: V9 적용 전 상태로 되돌린다 — is_active 컬럼 제거.
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE consent_item DROP COLUMN is_active");
            }
        }

        // 4단계: ddl-auto:update와 동일한 모드로, 지금 소스 트리의 ConsentItem 매핑을
        // 다시 반영시킨다. columnDefinition에 DEFAULT가 없으면 여기서 기존 두 row가
        // active=false로 떨어진다(실제 운영 버그와 동일한 경로).
        buildSessionFactory("update").close();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT is_active FROM consent_item WHERE consentItemId = ?")) {
            assertTrue(readIsActive(ps, firstItemId),
                    "V9 적용 전부터 있던 기존 항목(" + firstItemId + ")이 재마이그레이션 후에도 "
                            + "active=true로 남아있어야 한다 — DEFAULT 없는 컬럼 추가로 인한 "
                            + "회귀가 재현되면 안 된다");
            assertTrue(readIsActive(ps, secondItemId),
                    "V9 적용 전부터 있던 기존 항목(" + secondItemId + ")이 재마이그레이션 후에도 "
                            + "active=true로 남아있어야 한다");
        }
    }

    private boolean readIsActive(PreparedStatement ps, long consentItemId) throws Exception {
        ps.setLong(1, consentItemId);
        try (ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "consent_item_id=" + consentItemId + " row가 존재해야 한다");
            return rs.getBoolean(1);
        }
    }

    private long insertCompany(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO company (companyName, legalName, category, privacyUrl, ismsCertified) "
                        + "VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "마이그레이션회귀테스트기업");
            ps.setString(2, "마이그레이션회귀테스트기업");
            ps.setString(3, "기타");
            ps.setString(4, "https://example.com/privacy");
            ps.setBoolean(5, false);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private long insertConsentItem(Connection conn, long companyId, String itemName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO consent_item "
                        + "(company_id, itemType, itemName, dsScore, esScore, tfScore, pcScore, aiScore, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, companyId);
            ps.setString(2, "REQUIRED");
            ps.setString(3, itemName);
            ps.setInt(4, 5);
            ps.setInt(5, 3);
            ps.setInt(6, 3);
            ps.setDouble(7, 1.0);
            ps.setDouble(8, 1.0);
            ps.setBoolean(9, true);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private SessionFactory buildSessionFactory(String hbm2ddlAuto) {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", DB_URL)
                .applySetting("hibernate.connection.username", DB_USER)
                .applySetting("hibernate.connection.password", DB_PASSWORD)
                .applySetting("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver")
                .applySetting("hibernate.hbm2ddl.auto", hbm2ddlAuto)
                .build();
        MetadataSources sources = new MetadataSources(registry)
                .addAnnotatedClass(Company.class)
                .addAnnotatedClass(ConsentItem.class)
                .addAnnotatedClass(PolicySnapshot.class)
                .addAnnotatedClass(RiskScore.class)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(UserConsentCheck.class);
        return sources.buildMetadata().buildSessionFactory();
    }
}
