# 가계부 (budget-book) 설계 문서

> **버전**: v0.1 (2026-05-23)
> **상태**: 초안 — Phase 1 시작 전 합의용
> **소유자**: 부부 2인 (남편 / 아내)

---

## 1. 개요

### 1.1 목적
- 부부 2인이 일상에서 사용하는 가계부 웹 애플리케이션
- 부수 목적: Kotlin + Spring 백엔드 / GCP 인프라 학습 자산화

### 1.2 핵심 가치
1. **단순함** — 사용자 2명, 인프라 최소, 운영 부담 거의 0
2. **유연함** — 자유로운 태그, 3개 섹션(공용/남편/아내), 정기 거래
3. **학습 가치** — OAuth2, JPA, Flyway, Docker, Cloud Run 등 모던 백엔드 패턴 체득

### 1.3 비-목표 (Out of Scope, v1)
- 다중 통화 (KRW 단일 통화)
- **정기 거래 자동 생성** — 실제 결제 발생 시 부부가 직접 거래 등록
- 영수증 사진 첨부 / OCR
- 푸시 알림, 모바일 네이티브 앱
- 다국어 (한국어만)
- 다중 가구/팀 지원

---

## 2. 사용자 시나리오

### 2.1 페르소나
- **남편 / 아내**: 동일 권한. 자신의 거래·공용 거래를 등록/수정, 상대방 거래도 조회·수정 가능 (부부 공동 자산이라는 전제).

### 2.2 핵심 시나리오
1. **월급일** — 남편 섹션에 수입 등록, 태그 `급여`
2. **외식** — 공용 섹션 / 지출 / 태그 `식비`, `외식`
3. **통신비** — 매월 5일 자동 등록되도록 정기 지출 설정
4. **월말 정산** — 섹션별 / 태그별 통계 확인, 자산 추이 그래프 검토
5. **부모님 용돈** — 아내 섹션 수입, 태그 `엄마한테 받음`

---

## 3. 기능 요구사항

### 3.1 거래 (Transactions)
- 종류: `INCOME` (수입) / `EXPENSE` (지출)
- 필드: 금액(원, 양의 정수), 발생 일시(분 단위), 섹션, 태그(0~N), 메모(선택)
- 기능: CRUD, 일자/기간 필터, 섹션 필터, 태그 필터, 메모 키워드 검색

### 3.2 정기 지출/수입 정리 (Recurring Memo)
- 매월 반복되는 고정 지출/수입을 **메모용으로만** 모아두는 페이지 (자동 거래 생성 X)
- 필드: 이름, 섹션, 종류(INCOME/EXPENSE), 예상 금액, 매월 발생일(`day_of_month` 1~31, 선택), 메모, 참고 태그(0~N), 활성 여부
- 실제 결제/입금이 발생하면 부부가 직접 `Transaction` 등록 (정리 페이지의 "기록하기" 버튼이 등록 폼에 정보 prefill)
- 해지·중단된 항목은 `active = false`로 보관

### 3.3 태그 (Tags)
- 사용자가 자유롭게 생성/수정/삭제 (예: 급여, 엄마한테 받음, 식비, 병원비, 외식)
- 필드: 이름, 색상(hex), 종류(`INCOME` / `EXPENSE` / `BOTH`)
- 거래 1건에 1~N개 태그

### 3.4 섹션 (Sections)
- 고정 3개: `SHARED`(공용) / `HUSBAND`(남편) / `WIFE`(아내)
- 모든 거래는 정확히 한 섹션에 속함

### 3.5 자산 (Balance)
- 섹션별 **기초 자산** 1회 입력 (이후 수정 가능)
- **현재 잔액** = 기초 자산 + Σ(INCOME) − Σ(EXPENSE), 같은 섹션 · `occurred_at >= as_of_date`
- 일별/월별 자산 추이 그래프

### 3.6 통계 (Statistics)
- 기간 필터 (월별 기본, 임의 기간 지정 가능)
- 섹션별 수입/지출 합계
- 태그별 지출 TOP N (파이/막대)
- 월별 추이 (선 그래프)
- 자산 추이 (선 그래프)

### 3.7 접근성
- PC 브라우저: 데스크탑 레이아웃
- 모바일 브라우저: 반응형, 동일 URL
- (v2 후보) PWA 설치, 오프라인 캐시

---

## 4. 비기능 요구사항

| 항목 | 요구 수준 | 비고 |
|---|---|---|
| 사용자 수 | 2명 | |
| 동시 접속 | ≤ 2 | |
| 가용성 | 99% | 개인용 수준 |
| 보안 | 화이트리스트 외 차단 | OAuth + email whitelist |
| 응답시간 | < 200ms (warm) | cold start 1~2초 허용 |
| 월 비용 목표 | $0–5 | 무료 한도 활용 |
| 백업 | 일 1회 | Supabase 기본 |
| 로그 보존 | 30일 | Cloud Logging 기본 |

---

## 5. 시스템 아키텍처

### 5.1 다이어그램

```
        ┌──────────────────────┐
        │  브라우저 (PC/모바일) │
        └─────────┬────────────┘
                  │ HTTPS
                  ▼
        ┌──────────────────────┐        ┌─────────────────┐
        │  Cloud Run           │ ─────► │  Google OAuth   │
        │  Spring Boot + Kotlin│        └─────────────────┘
        │  + Thymeleaf + HTMX  │
        └─────────┬────────────┘
                  │ JDBC (SSL)
                  ▼
        ┌──────────────────────┐
        │  Supabase PostgreSQL │
        │  (Free tier, 500 MB) │
        └──────────────────────┘
```

> 백그라운드 스케줄러 없음 — 정기 거래는 정리 페이지에서 수동 등록 (§9 참고).

### 5.2 기술 스택

#### Backend
| 분류 | 선택 | 이유 |
|---|---|---|
| 언어 | Kotlin 1.9+ | 사용자 익숙 |
| 프레임워크 | Spring Boot 3.3+ | 표준, 자료 풍부 |
| 빌드 | Gradle (Kotlin DSL) | |
| 웹 | Spring Web (MVC) + Thymeleaf + HTMX | SSR + 부분 갱신 |
| 보안 | Spring Security + OAuth2 Client | |
| ORM | Spring Data JPA / Hibernate | |
| 마이그레이션 | Flyway | 스키마 버전 관리 |
| 검증 | Jakarta Validation | |
| 테스트 | JUnit 5 + Testcontainers | 실 DB로 통합 테스트 |
| 로깅 | Logback + JSON encoder | Cloud Logging 친화 |

#### Frontend (Thymeleaf 안에서)
| 분류 | 선택 | 이유 |
|---|---|---|
| 템플릿 | Thymeleaf | SSR, Spring 표준 |
| 인터랙션 | HTMX | 부분 갱신, JS 거의 안 씀 |
| 스타일 | Tailwind CSS (CDN → 추후 빌드) | 빠른 반응형 |
| 차트 | Chart.js (CDN) | 가벼움, 학습 비용 낮음 |

#### Infra (GCP + Supabase)
| 분류 | 선택 |
|---|---|
| 호스팅 | GCP Cloud Run (asia-northeast3 / Seoul) |
| DB | Supabase PostgreSQL (Free tier) |
| 시크릿 | GCP Secret Manager |
| 컨테이너 레지스트리 | GCP Artifact Registry |
| CI/CD | GitHub Actions → gcloud run deploy |
| 도메인 (선택) | Cloudflare DNS + Cloud Run custom domain |
| 로그 | GCP Cloud Logging |

#### Local Dev
- JDK 21 (Temurin)
- Docker Compose (로컬 PostgreSQL 컨테이너)
- IntelliJ IDEA Community

---

## 6. 도메인 모델

### 6.1 ERD (텍스트)

```
users
  id                 BIGINT  PK
  email              TEXT    UQ NOT NULL
  display_name       TEXT    NOT NULL
  role               TEXT    NOT NULL          -- 'HUSBAND' | 'WIFE'
  created_at         TIMESTAMPTZ DEFAULT now()

tags
  id                 BIGINT  PK
  name               TEXT    NOT NULL
  color              TEXT    NOT NULL          -- '#RRGGBB'
  type               TEXT    NOT NULL          -- 'INCOME' | 'EXPENSE' | 'BOTH'
  created_at         TIMESTAMPTZ DEFAULT now()
  UNIQUE(name, type)

transactions
  id                          BIGINT  PK
  section                     TEXT    NOT NULL  -- 'SHARED' | 'HUSBAND' | 'WIFE'
  type                        TEXT    NOT NULL  -- 'INCOME' | 'EXPENSE'
  amount                      BIGINT  NOT NULL CHECK (amount > 0)
  occurred_at                 TIMESTAMPTZ NOT NULL
  memo                        TEXT
  created_by_user_id          BIGINT  FK → users(id) NOT NULL
  created_at                  TIMESTAMPTZ DEFAULT now()
  updated_at                  TIMESTAMPTZ DEFAULT now()
  -- 인덱스: (occurred_at DESC), (section, occurred_at DESC)

transaction_tags
  transaction_id     BIGINT  FK → transactions(id)
  tag_id             BIGINT  FK → tags(id)
  PRIMARY KEY (transaction_id, tag_id)

recurring_transactions          -- 정기 지출/수입 메모용 (자동 거래 생성 안 함)
  id                 BIGINT  PK
  name               TEXT    NOT NULL          -- '넷플릭스', '월세', '정기 적금' 등
  section            TEXT    NOT NULL
  type               TEXT    NOT NULL          -- 'INCOME' | 'EXPENSE'
  amount             BIGINT  NOT NULL CHECK (amount > 0)   -- 예상 금액
  day_of_month       SMALLINT                  -- 1~31, 매월 발생일 (선택)
  memo               TEXT
  active             BOOLEAN NOT NULL DEFAULT true
  created_by_user_id BIGINT  FK → users(id)
  created_at         TIMESTAMPTZ DEFAULT now()
  updated_at         TIMESTAMPTZ DEFAULT now()

recurring_transaction_tags
  recurring_transaction_id  BIGINT FK
  tag_id                    BIGINT FK
  PRIMARY KEY (recurring_transaction_id, tag_id)

initial_balances
  section            TEXT    PK                -- 'SHARED' | 'HUSBAND' | 'WIFE'
  amount             BIGINT  NOT NULL
  as_of_date         DATE    NOT NULL
  updated_at         TIMESTAMPTZ DEFAULT now()
```

### 6.2 핵심 규칙
- `transactions.amount`는 항상 **양의 정수**. 부호는 `type`으로 표현
- `transactions ↔ recurring_transactions` 사이에 FK 없음 (느슨한 연결, 이름·태그로 사후 추적)
- 태그는 부부 공유 자원 (둘 다 동일한 풀)
- 섹션은 enum (DB에서는 문자열, 앱에서는 `enum class Section`)

### 6.3 잔액 계산 (Section S 기준)
```
current_balance(S) =
    initial_balances[S].amount
  + SUM(amount) WHERE section = S AND type = 'INCOME'  AND occurred_at >= as_of_date
  − SUM(amount) WHERE section = S AND type = 'EXPENSE' AND occurred_at >= as_of_date
```
구현은 한 쿼리에서 `CASE` 또는 두 번 집계 후 차감.

---

## 7. URL / 페이지 설계 (Thymeleaf SSR)

| Path | Method | 설명 |
|---|---|---|
| `/` | GET | 대시보드 (이달 요약, 최근 10건, 섹션별 잔액) |
| `/oauth2/authorization/google` | GET | OAuth 시작 (Spring Security 제공) |
| `/logout` | POST | 로그아웃 |
| `/transactions` | GET | 거래 목록 (쿼리 필터: `from`, `to`, `section`, `tagId`, `q`) |
| `/transactions/new` | GET / POST | 등록 폼 / 등록 |
| `/transactions/{id}` | GET | 상세/수정 폼 |
| `/transactions/{id}` | POST | 수정 (`_method=PUT`) |
| `/transactions/{id}` | POST | 삭제 (`_method=DELETE`) |
| `/recurring` | GET | 정기 지출/수입 정리 목록 (자동 생성 X, 메모) |
| `/recurring/new` | GET / POST | 항목 추가 |
| `/recurring/{id}` | GET / POST | 수정/삭제, 활성 토글 |
| `/recurring/{id}/log` | GET | "기록하기" — `/transactions/new`로 정보 prefill 후 이동 |
| `/tags` | GET / POST | 태그 목록 + 추가 |
| `/tags/{id}` | POST | 수정/삭제 |
| `/balances` | GET / POST | 기초 자산 조회/수정 |
| `/stats` | GET | 통계 페이지 |

HTMX 활용 포인트:
- 거래 등록 후 테이블 `<tbody>` 만 swap
- 태그 입력 자동완성 (`hx-get="/tags/search"`)
- 통계 페이지 기간 필터 변경 시 차트 영역만 갱신

---

## 8. 보안

### 8.1 인증 — Google OAuth + 이메일 화이트리스트
```yaml
# application.yml (prod 예시)
app:
  whitelist:
    - husband@example.com
    - wife@example.com

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id:     ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, email, profile
```

흐름:
1. 미인증 → `/oauth2/authorization/google` 으로 자동 리다이렉트
2. Google 로그인 성공 → `OAuth2UserService`에서 email 검사
3. 화이트리스트 불일치 시 즉시 `AccessDeniedException`
4. 일치 시 `users` 테이블에 upsert (없으면 생성), 세션 생성

세션:
- HttpSession + 쿠키 (HttpOnly, Secure, SameSite=Lax)
- 유효기간 14일 (`server.servlet.session.timeout=14d`)

### 8.2 인가
- 모든 경로 인증 필수
- 공개: `/login`, `/oauth2/**`, `/css/**`, `/js/**`
- 데이터 격리는 약함 (요구사항대로 부부 공동). 단 `created_by_user_id`는 audit 기록

### 8.3 통신 / 저장
- Cloud Run HTTPS 강제 (기본)
- Supabase JDBC SSL 강제 (`?sslmode=require`)
- 시크릿: Secret Manager → Cloud Run `--set-secrets`로 환경 변수 주입
- CSRF: Spring Security 기본 활성. HTMX는 `<meta name="_csrf">` 읽어 헤더 전송

### 8.4 운영 보안
- Cloud Run 엔드포인트는 공개되지만 OAuth + whitelist로 사실상 차단
- (옵션) Cloudflare Access 앞단 추가 시 이중 방어
- 의존성 취약점: Dependabot + `./gradlew dependencyCheckAnalyze`

---

## 9. 정기 지출/수입 정리 페이지

### 9.1 목적
- 매월 반복되는 고정 지출/수입을 **목록으로만 보관** (예: 넷플릭스 9,500 / 매월 13일, 월세 / 매월 25일)
- **자동 거래 생성·알림 없음** — 실제 결제 발생 시 부부가 직접 `Transaction` 등록
- "이번 달 이 항목 결제했나?" 확인용 체크리스트 + 다음 달 예상 지출 가시화

### 9.2 화면 동작
1. `/recurring` — 정기 항목 목록
   - 컬럼: 이름, 섹션, 종류, 예상 금액, 매월 발생일, 활성 여부, 참고 태그
   - 기본 정렬: `day_of_month` 오름차순 (이번 달 남은 일정 한눈에)
   - (옵션) 같은 `(section, name)` Transaction이 이번 달에 있는지 표시 → 이름·메모 기반 LIKE 매칭, 단순 보조 표시
2. `/recurring/new`, `/recurring/{id}` — CRUD, 활성 토글
3. `/recurring/{id}/log` — 클릭 시 `/transactions/new`로 redirect, query string으로 prefill
   - 예: `/transactions/new?section=SHARED&type=EXPENSE&amount=9500&memo=넷플릭스&tagIds=3,7`
   - 사용자가 일자/금액/태그 최종 확인 후 저장

### 9.3 시사점
- **Cloud Scheduler / 백그라운드 작업 완전히 불필요** — 인프라 더 단순, OIDC 토큰 검증 코드도 없음
- `recurring_transactions`는 사실상 즐겨찾기/체크리스트 성격
- 향후 자동 알림(메일)이 필요해지면 그 시점에 Cloud Scheduler 추가 (현재 결정 보류)

---

## 10. 배포 / CI-CD

### 10.1 Dockerfile (스케치)
```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
```

### 10.2 GitHub Actions 흐름
```yaml
# .github/workflows/deploy.yml (개요만)
on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - run: ./gradlew clean test bootJar
      - uses: google-github-actions/auth@v2
        with:
          workload_identity_provider: ${{ secrets.WIF_PROVIDER }}
          service_account:            ${{ secrets.DEPLOY_SA }}
      - run: |
          gcloud auth configure-docker asia-northeast3-docker.pkg.dev
          docker build -t asia-northeast3-docker.pkg.dev/$PROJECT/budget/app:${{ github.sha }} .
          docker push asia-northeast3-docker.pkg.dev/$PROJECT/budget/app:${{ github.sha }}
          gcloud run deploy budget-book \
            --image asia-northeast3-docker.pkg.dev/$PROJECT/budget/app:${{ github.sha }} \
            --region asia-northeast3 \
            --allow-unauthenticated \
            --max-instances 2 --min-instances 0 \
            --memory 512Mi --cpu 1 \
            --set-env-vars SPRING_PROFILES_ACTIVE=prod \
            --set-secrets "DB_URL=db-url:latest,DB_USER=db-user:latest,DB_PASSWORD=db-pw:latest,GOOGLE_CLIENT_ID=goog-cid:latest,GOOGLE_CLIENT_SECRET=goog-cs:latest"
```

### 10.3 환경 / 프로파일
- `local` — Docker Compose Postgres, OAuth 비활성(개발 모의 사용자)
- `prod` — Supabase + 실제 OAuth
- `application-{profile}.yml` 분리

---

## 11. 비용 예상 (월, 2인 사용 가정)

| 항목 | 사양 | 예상 |
|---|---|---|
| Cloud Run | min 0, max 2, 512MiB | $0 (무료 한도 내) |
| Artifact Registry | 이미지 ≤ 500 MB | $0 (0.5 GB 무료) |
| Secret Manager | secret 5개 내 | $0 |
| Cloud Logging | < 50 GiB/월 | $0 (50 GiB 무료) |
| Supabase | Free tier | $0 |
| 도메인 (선택) | .com 도메인 | $1 ~ $2 |
| **합계** | | **$0 ~ $3 / 월** |

> 트래픽이 거의 없으므로 Cloud Run 무료 한도(2M req, 360k GB-s memory)를 넘길 가능성 매우 낮음.

---

## 12. 학습 포인트 (스터디 가치 정리)

- OAuth2 Authorization Code Flow 실제 구성
- Spring Security Filter Chain, `OAuth2UserService` 커스터마이징
- JPA 연관관계: M:N (`transaction` ↔ `tag`), nullable FK
- Flyway 마이그레이션 버전 관리
- Thymeleaf + HTMX 패턴 (모던 SSR — JS 없이 SPA 같은 UX)
- Docker 멀티 스테이지 빌드 / Distroless 이미지 (v2)
- GCP: Cloud Run, Secret Manager, Artifact Registry, Workload Identity Federation
- GitHub Actions CI/CD (Workload Identity Federation으로 키 없는 인증)
- Testcontainers로 실 DB 통합 테스트

---

## 13. 개발 로드맵

### Phase 1 — MVP (목표 2~3주)
- [ ] 프로젝트 부트스트랩 (Spring Boot + Kotlin + Gradle)
- [ ] Docker Compose 로컬 Postgres
- [ ] Flyway 초기 스키마 (`V1__init.sql`)
- [ ] Google OAuth + 화이트리스트
- [ ] `Transaction` CRUD (목록/등록/수정/삭제, 필터)
- [ ] `Tag` CRUD
- [ ] 첫 Cloud Run 배포 + Supabase 연결
- [ ] HTTPS 도메인 (옵션)

### Phase 2 — 정기 거래 + 자산 + 통계 (목표 +2주)
- [ ] 정기 지출/수입 정리 페이지 CRUD + "기록하기" prefill 흐름
- [ ] 기초 자산 설정 / 잔액 계산
- [ ] 통계 페이지 (월/태그/섹션, Chart.js)
- [ ] 대시보드 (이달 요약, 현재 잔액)

### Phase 3 — 폴리싱 (목표 +1~2주)
- [ ] HTMX 본격 적용 (등록/수정 UX 개선)
- [ ] 반응형 정돈 (모바일)
- [ ] 도메인 단위 테스트 + 핵심 컨트롤러 통합 테스트
- [ ] CSV export
- [ ] 로깅 / 알림 정리

### Phase 4 (선택) — Nice to have
- [ ] PWA (모바일 홈 화면 추가)
- [ ] 영수증 사진 첨부 (Cloud Storage)
- [ ] 월말 요약 메일

---

## 14. 디렉토리 구조 (제안)

```
budget-book/
├── README.md
├── docs/
│   └── design.md                    # 이 문서
├── docker-compose.yml               # 로컬 Postgres
├── Dockerfile
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/budget/
│   │   │   ├── BudgetApplication.kt
│   │   │   ├── config/              # Security, Web, Scheduler
│   │   │   ├── auth/                # OAuth handler, whitelist
│   │   │   ├── transaction/         # Controller, Service, Repository, Entity
│   │   │   ├── recurring/
│   │   │   ├── tag/
│   │   │   ├── balance/
│   │   │   ├── stats/
│   │   │   └── common/              # Section enum, error handler 등
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-prod.yml
│   │       ├── db/migration/        # Flyway
│   │       │   └── V1__init.sql
│   │       ├── templates/           # Thymeleaf
│   │       │   ├── layout/
│   │       │   ├── transaction/
│   │       │   ├── tag/
│   │       │   ├── recurring/
│   │       │   ├── balance/
│   │       │   ├── stats/
│   │       │   └── dashboard.html
│   │       └── static/
│   │           ├── css/
│   │           └── js/
│   └── test/
│       └── kotlin/com/example/budget/
└── .github/
    └── workflows/
        └── deploy.yml
```

---

## 15. 결정 로그 (Decisions)

| # | 결정 | 대안 | 이유 |
|---|---|---|---|
| D1 | FE는 Thymeleaf SSR + HTMX | React/Vue SPA | 사용자 FE 미숙, 단일 배포 단순, 학습 부담 최소 |
| D2 | 인증: Google OAuth + email whitelist | ID/PW, magic link | 비번 관리 책임 회피, 화이트리스트 2명으로 충분 |
| D3 | 호스팅: Cloud Run | Compute Engine VM | 스케일 0 가능, 무료 한도 내, 운영 부담 거의 0 |
| D4 | DB: Supabase PostgreSQL | Cloud SQL | Cloud SQL 최소 인스턴스도 월 $7~. Supabase free tier 충분 |
| D5 | 정기 거래는 **자동 생성 X** — 정리/메모 페이지로만 운영 | Cloud Scheduler 자동 생성, 앱 내 `@Scheduled` | 운영 단순화. 실제 결제 시점에 사용자가 직접 등록하면서 태그·일자 자유롭게 부여 |
| D6 | 통화: KRW 단일 | 다중 통화 | v1 범위 축소 |
| D7 | 데이터 격리: 약 (부부 공유) | 사용자별 분리 | 요구사항 (부부 공동 자산) |
| D8 | `transactions ↔ recurring_transactions` FK 없음 | recurring_id 컬럼 유지 | 자동 생성 안 하므로 강결합 불필요. 이름·태그 기반 느슨한 연결 |

---

## 16. 미정 / 추후 결정 (Open Questions)

- **도메인** — 무료 Cloud Run URL로 충분한지, 커스텀 도메인 필요한지
- **시간대** — 입력/표시 모두 `Asia/Seoul` 가정. 해외 거주 시 변경 필요 여부?
- **삭제 정책** — 거래 삭제는 hard delete? soft delete? (v1은 hard, 필요시 soft 전환)
- **태그 계층** — 카테고리 → 서브태그 (예: 식비 > 외식) 필요한가? (v1은 단층)
- **공유 자산 분담률** — 공용 섹션 내에서 남편/아내 분담 비율 추적 필요? (현재는 분담 없음)
- **다국어** — 한국어 only로 시작, 코드는 i18n 친화적으로 작성

---

## 17. 다음 액션

1. 이 문서 검토 → 수정/추가 요청 반영
2. GCP 프로젝트 / Supabase 프로젝트 생성 (필요 시 가이드)
3. Google OAuth 클라이언트 생성 (Authorized redirect URI: `https://<cloud-run-url>/login/oauth2/code/google`)
4. Phase 1 부트스트랩: Spring Initializr → Kotlin + Spring Boot 3.x + Web + Security + OAuth2 Client + Data JPA + Thymeleaf + Validation + PostgreSQL + Flyway
5. 로컬에서 OAuth + 화이트리스트 동작까지 확인 후 첫 Cloud Run 배포
