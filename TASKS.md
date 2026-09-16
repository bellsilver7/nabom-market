# 작업 목록

`nabom-market` 구현 순서입니다. 위에서부터 순서대로 진행하면 앞 단계가 뒷 단계의 전제가 됩니다.
각 단계 끝에는 **확인 방법**이 있습니다. 그게 통과해야 다음으로 넘어갑니다.

---

## Phase 0 — 정지작업

Initializr 생성 직후 상태를 개발 가능한 상태로 만듭니다.
코드를 한 줄도 안 쓰는 단계지만, 여기서 어긋나면 뒤에서 계속 걸립니다.

- [x] **git 초기화** — `git init`, 첫 커밋 (`chore: init project`)
- [x] **Gradle Kotlin DSL 전환** — `build.gradle` → `build.gradle.kts`, `settings.gradle` → `settings.gradle.kts`
- [x] **MyBatis 의존성 추가** — `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.1.0`
      (3.0.x는 Boot 3.2~3.5 전용이라 이 프로젝트에선 안 됨)
- [x] **springdoc 추가** — `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`
- [x] **`application.properties` → `application.yml`** 전환, MyBatis 설정과 SQL 로깅 추가
- [x] **SecurityConfig 작성** — 전 경로 `permitAll` (인증은 마지막에)
- [x] **`compose.yaml` 정리** — DB 이름 `mydatabase` → `nabom`

> **확인 (직접 실행 필요)** — `./gradlew bootRun` 후 `curl localhost:8080/actuator/health`가 `{"status":"UP"}`.
> MySQL 컨테이너가 자동으로 떠 있어야 합니다 (`docker ps`).

---

## Phase 1 — 스키마와 상품 도메인

첫 도메인을 끝까지 관통시켜 프로젝트의 골격을 만듭니다.
여기서 정한 계층 구조와 응답 포맷을 나머지 도메인이 그대로 따라갑니다.

**스키마**

- [x] `db/migration/V1__init_schema.sql` — 6개 테이블 생성
      (`member`, `product`, `cart`, `cart_item`, `orders`, `order_item`)
- [x] `db/migration/V2__insert_sample_data.sql` — 회원 1명, 상품 10개

**공통 기반**

- [x] `common/exception` — `BusinessException`, `ErrorCode` enum, `GlobalExceptionHandler`
- [x] `common/response` — 에러 응답 DTO (`code`, `message`, `timestamp`)

**상품**

- [x] `Product` 도메인 객체
- [x] 요청/응답 DTO — `ProductCreateRequest`, `ProductResponse`
- [x] `ProductMapper` 인터페이스 + `resources/mapper/ProductMapper.xml`
- [x] `ProductService`
- [x] `ProductController` — 목록/단건/등록/수정/삭제
- [x] `@Valid` 검증 적용 (가격·재고 음수 불가 등)

> **확인** — Swagger UI에서 상품 등록 → 목록 조회가 되고,
> 없는 ID 조회 시 정해둔 포맷의 404가 나옵니다.

---

## Phase 2 — 장바구니

MyBatis에서 1:N 관계를 처음 다루는 단계입니다.

- [x] `X-MEMBER-ID` 헤더에서 회원 ID를 꺼내는 `@LoginMember` 아규먼트 리졸버
      (또는 일단 컨트롤러 파라미터로 직접 받기)
- [x] `Cart`, `CartItem` 도메인 객체
- [x] `CartMapper` + XML — **`resultMap`의 `<collection>`으로 장바구니 + 항목을 한 번에 조회**
- [x] 담기 — 이미 담긴 상품이면 수량 증가 (`ON DUPLICATE KEY UPDATE` 고려)
- [x] 조회 / 수량 변경 / 항목 삭제

> **확인** — 같은 상품을 두 번 담았을 때 행이 늘지 않고 수량만 증가합니다.
> 장바구니 조회 시 쿼리가 **1번만** 나가는지 로그로 확인하세요.

---

## Phase 3 — 주문

이 프로젝트의 핵심입니다. 나머지는 여기에 도달하기 위한 준비였습니다.

- [x] `Order`, `OrderItem` 도메인 객체, `OrderStatus` enum
- [x] **재고 차감 SQL** — 조건부 UPDATE, 반환 행 수가 0이면 `OUT_OF_STOCK` 예외
- [x] **주문 생성** — `@Transactional` 안에서
      장바구니 조회 → 재고 차감 → 주문 저장 → 주문 항목 저장(`order_price` 복사) → 장바구니 비우기
- [x] 주문 목록 조회
- [x] 주문 상세 조회 — `<collection>`으로 항목까지
- [x] 주문 취소 — 상태 변경 + 재고 복구 (이미 취소된 주문이면 예외)

> **확인** — 재고 3개인 상품을 5개 주문하면 409가 나오고,
> **DB의 재고가 그대로여야 합니다.** (트랜잭션이 롤백됐다는 뜻)
>
> 그다음 `@Transactional`을 일부러 떼고 같은 요청을 보내보세요.
> 재고만 깎이고 주문은 없는 상태가 재현됩니다. 왜 필요한지 몸으로 아는 순간입니다.

---

## Phase 4 — 다듬기

- [ ] **N+1 관찰과 개선** — 주문 목록 조회를 nested select로 구현했다면 쿼리 수를 세고,
      join 기반 nested resultMap으로 바꾼 뒤 다시 세어 기록
- [ ] Testcontainers 기반 Mapper 통합 테스트 (재고 차감, 주문 생성)
- [ ] `MockMvc` 컨트롤러 테스트 몇 개
- [ ] Swagger 어노테이션 정리 (`@Operation`, `@Schema`)
- [ ] README의 "구현 현황" 체크, "기록해 둘 것" 채우기
- [ ] GitHub 저장소 생성 및 push

---

## Phase 5 — 리프레시 토큰과 로그아웃

지금은 액세스 토큰 1시간이 전부입니다. 한 시간마다 다시 로그인해야 하고,
로그아웃해도 서버가 할 수 있는 일이 없습니다. 발급한 토큰을 되돌릴 방법이 없기 때문입니다.
**서버가 기억하는 토큰을 하나 만들어** 그 둘을 해결합니다.

### 먼저 정해 둔 것

| 결정 | 이유 |
|---|---|
| 리프레시 토큰은 JWT 가 아니라 **난수 문자열** | 어차피 DB 를 조회해 폐기 여부를 확인해야 한다. 자기 검증이 되는 JWT 의 장점이 사라진다 |
| DB 에 **SHA-256 해시로** 저장 | 테이블이 새도 그 값으로 로그인할 수 없다. 조회 키로 써야 해서 솔트(BCrypt)는 못 쓴다 |
| 재발급할 때 **회전(rotation)** — 옛 토큰 폐기, 새 토큰 발급 | 유출된 토큰의 유효 기간이 한 번의 재발급까지로 줄어든다 |
| 폐기한 행을 **지우지 않고 `revoked_at` 기록** | "이미 쓴 토큰이 또 왔다" 를 탐지할 수 있다. 행이 아예 없으면 위조, 있는데 폐기됐으면 탈취 의심 |
| 저장소는 **MySQL** (Redis 아님) | Redis 는 아직 이 프로젝트에 없다. 인기 상품 캐싱을 붙일 때 같이 고민한다 |
| 실패는 전부 **401 `UNAUTHORIZED`** | 없는 토큰 / 만료된 토큰 / 폐기된 토큰을 응답으로 구분해 주지 않는다 |

**이미 준비된 것** — `V5__add_refresh_token.sql` (테이블), `application.yml` 의 `jwt.refresh-expiration-days: 14`.

### 순서

- [x] `RefreshToken` 도메인 + `RefreshTokenMapper` + `resources/mapper/RefreshTokenMapper.xml`
      — `insert`, `findByTokenHash`, `revokeByTokenHash`, `revokeAllByMemberId`
- [x] 토큰 생성 — `SecureRandom` 32바이트 → Base64 URL 인코딩, 저장은 SHA-256 hex 64자
      (`JwtProvider` 에 넣지 말고 `RefreshTokenService` 같은 자리를 따로 두는 편이 낫다.
      JWT 와는 서명도 수명도 성격이 다르다)
- [x] `TokenResponse` 에 `refreshToken` 추가 — 로그인이 액세스 + 리프레시를 함께 발급
- [x] `POST /api/v1/auth/refresh` — 본문 `{"refreshToken": "..."}` → 새 액세스 + 새 리프레시
- [x] `POST /api/v1/auth/logout` — 받은 리프레시 토큰을 폐기, `204 No Content`
- [x] 재사용 탐지 — 이미 폐기된 토큰이 오면 그 회원의 리프레시 토큰을 **전부** 폐기
- [x] `AuthApiTest` 에 `@Nested` 두 개 (재발급 / 로그아웃) 추가
- [x] 문서 — README 의 API 표(인증), 구현 현황, 데이터 모델 ERD, "앞으로" 체크박스

> **확인**
> 1. 로그인 → `refresh` → 돌려받은 두 토큰이 모두 **이전과 다른 값**이다.
> 2. 방금 쓴 리프레시 토큰으로 다시 `refresh` → `401`.
>    그리고 **그 직후 발급받았던 새 리프레시 토큰도 401** 이다 (재사용 탐지가 전부 폐기했으므로).
> 3. `logout` 후 그 리프레시 토큰으로 `refresh` → `401`.
> 4. `DELETE FROM member WHERE id = ?` 로 회원을 지우면 리프레시 토큰 행도 같이 사라진다
>    (`ON DELETE CASCADE`).

### 이건 못 고친다 — 로그아웃해도 액세스 토큰은 살아 있다

서명만 맞으면 통과하는 것이 액세스 토큰이라, 로그아웃 시점에 서버가 회수할 방법이 없습니다.
남은 만료 시간까지는 그대로 동작합니다. 선택지는 셋이고, 셋 다 대가가 있습니다.

1. **수명을 줄인다** (60분 → 15~30분). 노출 창이 좁아지는 대신 재발급이 잦아집니다.
2. **폐기 목록을 둔다.** 정확하지만 매 요청마다 조회가 붙어 무상태가 아니게 됩니다.
3. **그대로 두고 문서에 적는다.** 이 프로젝트의 규모에서는 이 선택도 정직합니다.

무엇을 고르든 README 에 남기세요. 모르고 넘어간 것과 알고 고른 것은 다릅니다.

---

## 이후 (선택)

- [ ] Spring Security + JWT 인증 — `X-MEMBER-ID` 제거
- [ ] 동시 주문 부하 테스트로 재고 정합성 검증
- [ ] 상품 검색 — 동적 쿼리 `<if>`, `<foreach>`
- [ ] 인기 상품 Redis 캐싱
- [ ] GitHub Actions CI

---

## 커밋 컨벤션

제목은 **이모지 + 한글**로 씁니다. 본문은 필요할 때만, 무엇을 왜 바꿨는지 적습니다.

```
🗃️ 초기 스키마와 샘플 데이터 마이그레이션 추가

- cart_item(cart_id, product_id) UNIQUE — 중복 담기를 한 문장으로 처리하기 위함
- order_item.order_price는 주문 시점 단가 스냅샷
```

| 이모지 | 용도 |
|---|---|
| 🎉 | 프로젝트 초기화 |
| ✨ | 새 기능 |
| 🐛 | 버그 수정 |
| 🗃️ | 스키마 · 마이그레이션 |
| 🔧 | 설정 변경 |
| ♻️ | 리팩터링 |
| ✅ | 테스트 추가 · 수정 |
| 📝 | 문서 |
| 🔥 | 코드 · 파일 제거 |

제목에 마침표는 찍지 않고, "~했음"보다 "~추가", "~수정"처럼 명사형으로 끝냅니다.

---

## 진행하면서 주의할 것

**Boot 4 기준으로 검색하세요.** 인터넷 예제 대부분이 Boot 3 기준입니다.
스타터 이름부터 다르고(`spring-boot-starter-web` → `webmvc`), 라이브러리 버전도 따로입니다.
`implementation` 좌표를 복붙하기 전에 Boot 4 대응 버전인지 확인하세요.

**Security를 마지막에 붙이는 이유.** 처음부터 JWT를 넣으면 컨트롤러 하나 테스트할 때마다
토큰을 발급받아야 해서 개발 속도가 크게 떨어집니다.
`X-MEMBER-ID` 헤더로 가다가 마지막에 교체하는 편이 낫습니다.

**커밋은 Phase 단위보다 잘게.** 도메인 하나가 컨트롤러까지 통했을 때가 적당한 단위입니다.
