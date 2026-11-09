# gopssl-backend-spring
Gopssl v2 백엔드 (Spring Boot)

Java 21, Gradle(Kotlin DSL), Spring Boot 4.1, PostgreSQL 16, Flyway.

## 로컬 실행

Docker가 필요하다. JDK 21이 없어도 Gradle이 자동으로 내려받아 빌드에 쓴다 (Gradle 실행에는 Java 17 이상 필요, 시스템 기본 Java는 바뀌지 않는다).

```bash
cp .env.example .env              # 값을 바꿔서 쓴다. 커밋되지 않는다.
docker compose up -d              # PostgreSQL 16, localhost:5433
./gradlew bootRun --args='--spring.profiles.active=local'
```

- 상태 확인: `curl localhost:8080/health` → `{"status":"ok"}`
- Swagger UI: http://localhost:8080/swagger-ui/index.html (`/swagger-ui.html`은 인증 대상이라 401이다)

`local` 프로파일은 프로젝트 루트의 `.env`를 읽는다. 개인 설정은 `src/main/resources/application-local.yml`에 두면 된다 (커밋되지 않는다).

## 환경변수

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `DB_URL` | 예 | JDBC URL. 로컬: `jdbc:postgresql://localhost:5433/gopssl` |
| `DB_USERNAME` | 예 | DB 사용자. `docker compose`도 이 값으로 DB를 만든다 |
| `DB_PASSWORD` | 예 | DB 비밀번호. `docker compose`도 이 값으로 DB를 만든다 |
| `JWT_SECRET` | 아직 아니오 | JWT 서명 키 자리. 인증 구현 전이라 지금은 읽지 않는다 |

## 프로파일

| 프로파일 | 설명 |
| --- | --- |
| `local` | `.env`를 읽는다. Flyway는 V1부터 실행한다 |
| `dev`, `prod` | 환경변수를 직접 받는다. `baseline-on-migrate=true`, `baseline-version=1`: 스키마가 이미 있으면 V1을 실행하지 않고 기록만 남긴다 |

## 테스트

```bash
./gradlew test    # Docker 필요 (Testcontainers)
./gradlew build   # CI와 같은 명령
```

## 엔티티 ↔ 테이블

| 엔티티 | 테이블 |
| --- | --- |
| `user.entity.User` | `users` |
| `customer.entity.Customer` | `customers` |
| `sale.entity.Sale` | `sales` |
| `sale.entity.SaleTreatment` | `sale_treatments` |
| `treatment.entity.Treatment` | `treatments` |
| `treatment.entity.TreatmentCategory` | `treatment_categories` |
