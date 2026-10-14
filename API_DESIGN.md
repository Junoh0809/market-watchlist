# MVP API 설계

최종 작성: 2026-10-14

이 문서는 `PLAN.md`의 무료 범위 전용 MVP를 위한 백엔드 API 초안이다. 실제 Spring Boot 구현과 공급자 연결 전에 API 경계와 응답 규칙을 고정하기 위한 문서이며, 구현 코드는 포함하지 않는다.

## 1. API 설계 원칙

- REST API와 JSON을 사용한다.
- 기본 경로는 `/api/v1`로 둔다.
- 클라이언트는 사용자별 관심 종목만 조회할 수 있다.
- 종목은 `market + symbol` 조합으로 식별한다. 예: `KRX:005930`, `US:AAPL`.
- 외부 공급자 응답을 그대로 노출하지 않고 내부 표준 모델로 변환한다.
- 무료 공급자가 제공하는 갱신 주기를 존중한다. 화면 polling 주기인 60초를 공급자 호출 주기로 해석하지 않는다.
- 모든 저장 시각과 원본 시각은 시간대 정보를 보존한다. 화면 표시 시각은 `Asia/Seoul`로 제공한다.
- 공급자 장애나 제공 주기 초과 시 마지막 정상 데이터를 반환하되 상태를 명시한다.
- MVP에서는 유료 API와 유료 데이터 라이선스를 사용하지 않는다.

## 2. 인증과 사용자 식별

이메일 가입만 지원한다. 보호된 API는 인증된 요청의 사용자 식별자에서 `userId`를 얻으며, 요청 본문의 `userId`를 신뢰하지 않는다.

### 인증 엔드포인트

| 메서드 | 경로 | 설명 | 인증 |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/signup` | 이메일 계정 생성 | 불필요 |
| `POST` | `/api/v1/auth/login` | 로그인 및 세션/토큰 발급 | 불필요 |
| `POST` | `/api/v1/auth/logout` | 현재 인증 세션 종료 | 필요 |
| `DELETE` | `/api/v1/me` | 현재 계정 삭제 | 필요 |

인증 방식의 구체적인 구현은 Spring Security 설정 단계에서 결정한다. API 계약상 보호된 요청은 `401 Unauthorized`, 인증은 되었으나 권한이 없거나 다른 사용자의 리소스에 접근하면 `403 Forbidden`을 반환한다.

## 3. 종목 검색

```http
GET /api/v1/securities?market=US&query=apple&limit=20
Authorization: Bearer <access-token>
```

`market`은 `KRX` 또는 `US`다. 검색 결과는 시장과 코드를 함께 반환해 같은 종목명이 충돌하지 않게 한다.

```json
{
  "items": [
    {
      "market": "US",
      "symbol": "AAPL",
      "name": "Apple Inc.",
      "currency": "USD"
    }
  ],
  "limit": 20
}
```

초기 목업에서는 고정 종목 목록을 사용한다. 실제 종목 마스터 공급자는 무료 이용 범위와 검색 데이터 제공 조건을 확인한 뒤 연결한다.

## 4. 관심 종목

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/me/watchlist` | 현재 사용자의 관심 종목 조회 |
| `POST` | `/api/v1/me/watchlist` | 관심 종목 등록 |
| `DELETE` | `/api/v1/me/watchlist/{market}/{symbol}` | 관심 종목 삭제 |

등록 요청:

```http
POST /api/v1/me/watchlist
Content-Type: application/json

{
  "market": "KRX",
  "symbol": "005930"
}
```

조회 응답:

```json
{
  "items": [
    {
      "market": "KRX",
      "symbol": "005930",
      "name": "삼성전자",
      "currency": "KRW",
      "createdAt": "2026-10-14T05:20:00Z"
    }
  ]
}
```

같은 사용자가 같은 종목을 다시 등록하면 `409 Conflict`를 반환하거나 멱등적으로 기존 항목을 반환하는 정책 중 하나를 구현 전에 선택한다. 삭제는 현재 사용자 소유의 항목만 대상으로 한다.

## 5. 시세·지수·뉴스 조회

### 시세

```http
GET /api/v1/me/quotes
Authorization: Bearer <access-token>
```

관심 종목에 대한 시세만 반환한다. 서버는 사용자별로 공급자를 호출하지 않고, 수집된 공통 데이터를 관심 종목과 조합한다.

```json
{
  "items": [
    {
      "security": {
        "market": "US",
        "symbol": "AAPL",
        "name": "Apple Inc.",
        "currency": "USD"
      },
      "price": 225.12,
      "change": 1.42,
      "changeRate": 0.635,
      "source": "mock",
      "sourceTimestamp": "2026-10-14T05:18:00Z",
      "displayTimestamp": "2026-10-14T14:18:00+09:00",
      "displayTimezone": "Asia/Seoul",
      "retrievedAt": "2026-10-14T05:19:05Z",
      "status": "NORMAL"
    }
  ],
  "screenRefreshedAt": "2026-10-14T05:20:00+09:00",
  "displayTimezone": "Asia/Seoul"
}
```

상태 값은 다음과 같다.

- `NORMAL`: 공급자 데이터가 정상적으로 수집됐고 허용된 제공 주기 안에 있다.
- `LAST_KNOWN`: 최신 수집이 실패했거나 제공 주기를 초과해 마지막 정상 가격을 반환한다.
- `UPDATING`: 수집 작업이 진행 중이며 이전 가격을 함께 반환할 수 있다.

`LAST_KNOWN` 또는 `UPDATING`이어도 `sourceTimestamp`와 `retrievedAt`을 유지해 데이터가 언제 기준인지 확인할 수 있게 한다. 데이터가 한 번도 성공하지 않아 가격이 없으면 `price`는 `null`이며 상태는 `UPDATING`으로 반환한다.

### 지수

```http
GET /api/v1/market-indices
Authorization: Bearer <access-token>
```

초기 대상은 KOSPI, KOSDAQ 및 확정된 미국 주요 지수다. 응답의 시간·상태 규칙은 시세와 같다.

```json
{
  "items": [
    {
      "index": "KOSPI",
      "name": "KOSPI",
      "value": 2680.15,
      "change": -12.33,
      "changeRate": -0.458,
      "source": "mock",
      "sourceTimestamp": "2026-10-14T05:15:00Z",
      "displayTimestamp": "2026-10-14T14:15:00+09:00",
      "displayTimezone": "Asia/Seoul",
      "status": "NORMAL"
    }
  ],
  "screenRefreshedAt": "2026-10-14T05:20:00Z"
}
```

### 뉴스

```http
GET /api/v1/me/news?limit=20
Authorization: Bearer <access-token>
```

현재 사용자의 관심 종목과 연결된 기사만 반환한다.

```json
{
  "items": [
    {
      "id": "news-001",
      "market": "US",
      "symbol": "AAPL",
      "title": "Example headline",
      "source": "Example News",
      "publishedAt": "2026-10-14T04:50:00Z",
      "displayPublishedAt": "2026-10-14T13:50:00+09:00",
      "displayTimezone": "Asia/Seoul",
      "url": "https://example.com/article",
      "provider": "mock"
    }
  ],
  "screenRefreshedAt": "2026-10-14T05:20:00Z"
}
```

## 6. 공급자 어댑터와 목업 경계

백엔드의 도메인·API 계층은 공급자별 SDK나 응답 형식에 의존하지 않는다. 다음 경계를 둔다.

```text
API Controller
  -> Application Service
    -> Provider Port
      -> Mock Provider Adapter
      -> External Free Provider Adapter (후속)
```

공급자 포트는 최소한 다음 기능을 구분한다.

- `SecuritySearchProvider`: 종목 검색
- `QuoteProvider`: 종목 시세
- `MarketIndexProvider`: 시장 지수
- `NewsProvider`: 종목 관련 뉴스

어댑터가 반환할 표준 데이터에는 가격 또는 기사 데이터, 공급자명, 원본 기준 시각, 수집 시각, 성공·실패 상태가 포함된다. 실패 시 어댑터가 예외를 도메인 오류로 변환하고, 수집 계층이 마지막 정상 데이터를 유지한다. 목업 어댑터는 정상·지연·실패 fixture를 선택할 수 있어야 한다.

무료 공급자 조건이 다중 사용자 표시·재배포를 허용하지 않으면 해당 어댑터는 로컬·개발 프로파일에서만 활성화한다. 유료 플랜으로 우회하지 않는다.

## 7. 갱신과 시간 처리

- 프론트엔드는 API를 60초마다 polling한다.
- 60초 polling은 외부 공급자 호출 주기를 의미하지 않는다.
- 서버 수집 작업은 무료 공급자가 제공하는 주기와 호출 한도에 맞춘다.
- `sourceTimestamp`에는 공급자가 제공한 원본 기준 시각과 원본 시간대 정보를 보존한다.
- `displayTimestamp`와 `displayPublishedAt`은 `Asia/Seoul` 기준으로 생성한다.
- 미국 거래소 시간은 거래소 시간대 규칙과 서머타임을 적용해 변환한다.
- `retrievedAt`은 서버가 공급자 응답을 수집한 시각이다.

## 8. 오류 응답

모든 오류는 공통 형식을 사용한다.

```json
{
  "error": {
    "code": "WATCHLIST_ITEM_NOT_FOUND",
    "message": "관심 종목을 찾을 수 없습니다.",
    "requestId": "req-123"
  }
}
```

주요 상태 코드는 다음과 같다.

| HTTP | 코드 예시 | 의미 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 잘못된 query 또는 요청 본문 |
| `401` | `UNAUTHENTICATED` | 인증 정보 없음·만료 |
| `403` | `FORBIDDEN` | 사용자 소유가 아닌 데이터 접근 |
| `404` | `SECURITY_NOT_FOUND` | 등록되지 않은 종목 |
| `409` | `WATCHLIST_ALREADY_EXISTS` | 중복 등록 |
| `429` | `RATE_LIMITED` | 내부 또는 공급자 호출 한도 초과 |
| `500` | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 |
| `503` | `PROVIDER_UNAVAILABLE` | 최신 공급자 데이터 수집 불가 |

시세·지수 API는 공급자 장애를 전체 응답 실패로만 처리하지 않는다. 마지막 정상 데이터가 있으면 `200`과 함께 항목 상태 `LAST_KNOWN` 또는 `UPDATING`을 반환한다. 데이터가 전혀 없고 응답을 구성할 수 없을 때만 `503`을 사용한다.

## 9. 사용자별 데이터 격리

- 관심 종목과 뉴스 조회는 인증 토큰의 사용자 식별자로 필터링한다.
- 관심 종목 등록·삭제 시 URL의 시장·코드와 인증 사용자 소유권을 함께 검증한다.
- 다른 사용자의 리소스 존재 여부를 추측할 수 있도록 상세 오류를 노출하지 않는다.
- 시세·지수처럼 공통으로 수집된 시장 데이터는 공개 가능한 범위에서 공유할 수 있지만, 어떤 종목을 관심 등록했는지는 사용자별로만 반환한다.
- 통합 테스트에서 두 사용자 계정의 관심 종목과 관련 뉴스가 서로 섞이지 않는지 검증한다.

## 10. 구현 전 남은 결정

- 이메일 인증 메일과 비밀번호 재설정의 상세 정책
- 중복 관심 종목 등록 시 멱등 응답 또는 `409` 정책
- 초기 미국 주요 지수 목록
- 무료 공급자별 정확한 제공 주기·호출 한도·재배포 조건
- 실제 토큰 방식과 만료 정책

위 결정은 목업 기반 API 문서화와 기본 구현을 막지 않는다.
