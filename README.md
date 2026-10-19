# Stock Management MVP

국내·미국 관심 종목을 관리하고 목업 시세·지수·뉴스를 확인하는 초기 MVP입니다.

## 실행

### Docker Compose

Docker Compose로 프론트엔드, 백엔드, PostgreSQL을 함께 실행합니다.

```bash
docker compose up --build
```

- 프론트엔드: `http://localhost:3000`
- 백엔드 API: `http://localhost:8080`
- 로컬 PostgreSQL: `127.0.0.1:5433` (Docker 내부에서는 `db:5432`)

계정·세션·관심 종목은 PostgreSQL 볼륨에 저장되어 컨테이너를 재시작해도 유지됩니다. 처음 실행할 때 Flyway가 스키마를 생성합니다. 기본 DB 비밀번호는 로컬 개발용 `stocks`이며, 필요하면 실행 전에 `STOCK_DB_PASSWORD`를 설정하세요. 기존 인메모리 데이터는 이전할 저장소가 없어 새 DB로 옮길 수 없습니다.

프론트엔드는 `/api/v1` 요청을 같은 주소의 백엔드 프록시로 전달하므로 다른 컴퓨터에서 접속할 때도 브라우저의 `localhost`를 백엔드 주소로 사용하지 않습니다.

종료하려면 다음을 실행합니다. 데이터 볼륨은 보존됩니다.

```bash
docker compose down
```

Compose 없이 `run-docker.sh`를 사용한 경우에는 다음으로 종료합니다.

```bash
docker stop stock-management-frontend stock-management-backend stock-management-db
```

Compose와 `run-docker.sh`는 서로 다른 PostgreSQL 볼륨을 사용하므로 실행 방식을 바꾸면 기존 계정이 보이지 않습니다. 같은 데이터를 계속 사용하려면 한 방식을 유지하세요.

`docker compose`를 찾을 수 없는 Docker CLI라면 Docker Compose 플러그인을 설치하거나, Compose 없이 다음 스크립트를 사용합니다.

```bash
./run-docker.sh
```

`Cannot connect to the Docker daemon` 오류가 나오면 Docker Desktop을 먼저 실행한 뒤 다시 시도합니다. 실행 여부는 다음 명령으로 확인할 수 있습니다.

```bash
docker info
```

macOS에서 `docker-credential-desktop not found` 오류가 나오면 `run-docker.sh`가 Docker Desktop의 credential helper 경로를 자동으로 추가합니다. 스크립트를 최신 상태로 받은 뒤 다시 실행하세요.

Docker Desktop을 사용한다면 최신 버전 설치 후 `docker compose version`으로 Compose 플러그인을 확인할 수 있습니다.

### 백엔드

Java 21과 Maven이 필요합니다. 먼저 `docker compose up -d db`로 DB를 실행하세요. 기본 연결은 `localhost:5433/stocks`이며 `STOCK_DB_URL`, `STOCK_DB_USER`, `STOCK_DB_PASSWORD`로 변경할 수 있습니다.

```bash
mvn spring-boot:run
```

기본 주소는 `http://localhost:8080`입니다.

### 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

브라우저에서 Vite가 출력한 주소를 엽니다. 개발 서버는 `/api/v1` 요청을 `http://localhost:8080`으로 전달합니다. 다른 API 주소가 필요하면 `VITE_API_URL`로 변경할 수 있습니다.

### 검증

```bash
mvn test
cd frontend && npm test && npm run build
```

## 현재 범위

- 이메일 회원가입·로그인·로그아웃·계정 삭제
- Spring Security Bearer 인증과 7일 유효 세션
- 사용자별 관심 종목 등록·조회·삭제
- 중복 등록 방지
- 계정 삭제 시 세션과 관심 종목 제거
- 목업 시세·KOSPI/KOSDAQ 지수·관련 뉴스
- 종목별 공급자 수집 상태 API (`GET /api/v1/provider-status`)
- 60초 프론트엔드 polling
- `Asia/Seoul` 표시 시각, UTC 원본 시각, 거래소 시간대·서머타임 변환 필드

계정·세션·관심 종목은 PostgreSQL에 저장됩니다. 세션 토큰은 SHA-256 해시로 저장하며, 로그아웃과 계정 삭제 시 무효화됩니다. 검색 가능한 종목은 국내 3개와 미국 4개의 목업 목록입니다. 시세·지수·뉴스도 목업 데이터이므로 실제 투자 판단에 사용할 수 없습니다. 실제 데이터 공급자 연결은 아직 구현되지 않았습니다. 외부 공급자는 이용 조건과 재배포 권한을 확인한 후에 연결해야 합니다.

미구현 기능과 이후 착수 기준은 [PLAN.md의 후속 작업 진행 현황](PLAN.md#후속-작업-진행-현황-2026-10-17)에 기록했습니다.

목업 시세의 시작 상태는 `STOCK_MOCK_QUOTE_SCENARIO` 환경변수로 `NORMAL`(기본값), `DELAYED`, `FAILURE`, `UPDATING` 중 선택할 수 있습니다. 처음부터 `FAILURE`면 가격 없이 `UPDATING`을 반환합니다. 정상 수집 후 실패하면 마지막 가격과 `LAST_KNOWN`을 반환하는 흐름은 자동화 테스트에서 시나리오를 전환해 검증합니다.
