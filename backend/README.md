# Backend

담당: 팀원1 (노가현), 팀원3 (황다연)

## 작업 브랜치 규칙
- 백엔드 기능: `feature/be-기능명`
- 위험도 모듈: `feature/risk-기능명`

## 폴더 구조
> 팀원1이 기술 스택 확정 후 업데이트 예정

## 테스트 실행
- 기본 테스트: `./gradlew.bat test` (H2 인메모리 DB 사용, MySQL 설치/실행 불필요)
- 통합 테스트: `./gradlew.bat integrationTest`
  - `ConsentItemBatchServiceIntegrationTest` 등 트랜잭션 롤백을 실제 DB로 검증하는 테스트가 포함되어 있어, 로컬에 MySQL이 떠 있고 `consentradar` 데이터베이스가 존재해야 한다.
  - 접속 정보는 `src/main/resources/application.yml` 참고 (기본: `localhost:3306`, `root`/`1234`).

## 관리자(admin) 계정
- `/admin/**`는 ROLE_ADMIN이 필요하다(`SecurityConfig` 참고). DB 접속 정보와 같은 방식으로, `application.yml`의 기본값(`admin`/`local-dev-only-CHANGE-ME`)은 로컬 개발 전용이고 배포 환경에서는 `ADMIN_USERNAME`/`ADMIN_PASSWORD` 환경변수로 반드시 교체해야 한다.
