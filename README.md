# budget-book

부부 2인이 사용하는 가계부 웹 애플리케이션 + Kotlin/Spring 학습용 프로젝트.

전체 설계는 [docs/design.md](docs/design.md) 참고.

## Stack

- **Backend**: Kotlin 1.9 / Spring Boot 3.5 / JPA / Flyway
- **Frontend**: Thymeleaf SSR + HTMX + Tailwind CSS (CDN) + Chart.js (CDN)
- **DB (local)**: H2 in-memory (MODE=PostgreSQL)
- **DB (prod)**: Supabase PostgreSQL
- **Auth**: Google OAuth + 이메일 화이트리스트 (prod) / 자동 로그인 (local)
- **Host**: GCP Cloud Run (예정)

## Local 개발

요구사항: JDK 17

### 실행

```bash
./gradlew bootRun
```

브라우저: <http://localhost:8080> — 자동 로그인 (남편 dev 계정).

H2 콘솔: <http://localhost:8080/h2-console>
- JDBC URL: `jdbc:h2:mem:budget`
- User: `sa`, Password: (비움)

### 테스트

```bash
./gradlew test
```

### 빌드

```bash
./gradlew bootJar
```

## 프로파일

| 프로파일 | 설명 | 자동 로그인 | DB |
|---|---|---|---|
| `local` (기본) | 로컬 개발용 | ON (`husband@example.com`) | H2 메모리 |
| `prod` | 배포용 | OFF (Google OAuth + whitelist) | PostgreSQL (env로 주입) |

`local`에서 사용자 전환은 `application-local.yml`의 `app.dev-auth.email/role`을 수정.

## 디렉토리

```
src/main/kotlin/com/budget/
  ├── BudgetBookApplication.kt
  ├── auth/        # User, OAuth, DevAuthFilter
  ├── balance/     # 기초 자산 / 잔액 계산
  ├── common/      # enums, CurrentUserAdvice/Resolver
  ├── config/      # SecurityConfig, AppProperties
  ├── dashboard/   # /
  ├── recurring/   # 정기 지출/수입 메모
  ├── stats/       # /stats
  ├── tag/         # 태그 관리
  └── transaction/ # 거래 CRUD

src/main/resources/
  ├── application.yml / application-local.yml / application-prod.yml
  ├── db/migration/   # Flyway
  └── templates/      # Thymeleaf
```

## 다음 단계

[docs/design.md §13 로드맵](docs/design.md#13-개발-로드맵) 참고.
