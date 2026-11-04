@AGENTS.md

# 프로젝트 구조

```
src/main/java/com/oznwoo/gopssl/
├── GopsslApplication.java
├── global/
│   ├── config/      # SecurityConfig
│   ├── exception/   # GlobalExceptionHandler, ApiError ({ "detail": "메시지" })
│   ├── health/      # GET /health
│   └── tenant/      # 테넌트 처리 자리
├── auth/            # 도메인마다 controller/service/repository/dto/entity
├── user/
├── customer/
├── sale/
└── treatment/
src/main/resources/
├── application.yml  # 공통 + local/dev/prod 프로파일
└── db/migration/    # Flyway (V1__baseline.sql)
src/test/java/...    # Testcontainers 통합 테스트
```
