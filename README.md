# CheckUp Server

광주소프트웨어마이스터고등학교 기숙사 출석 관리 서비스 **CheckUp**의 Spring Boot 백엔드입니다.

기숙사 자치위원이 학생을 한 명씩 확인하던 입소·자습실 출석을 얼굴 인식과 QR 인증으로 대신합니다.
기숙사생 약 200명, 출입구 3곳을 대상으로 하며 학생과 관리자 모두 웹으로 사용합니다.

> 현재는 프로젝트 골격 단계입니다. 아래 기능은 구현 목표이며 아직 구현되지 않았습니다.

## 서버가 맡는 일

- **인증·권한**: DataGSM OAuth 로그인, 개인정보 동의, 관리자·본인·본인 호실 범위를 서버에서 검증
- **출석**: 얼굴 인식·QR 결과를 받아 학생·용도(기숙사 입소 / 자습실)·운영일 단위로 출석 확정, 중복 방지, 관리자 수동 수정
- **QR 세션**: 관리자 페이지별 독립 세션과 15분마다 교체되는 토큰
- **운영일**: 매일 08:00(KST) 기준 당일 기록 정리
- **공지·봉사**: 공지와 학생 알림, 봉사 횟수 관리

얼굴 검출·임베딩 대조는 FastAPI(AI 서버)가 맡고, 출석 확정은 이 서버가 합니다.

```text
브라우저 (Next.js)
      │
      ▼
Spring API (이 레포) ── DataGSM OAuth
   ├── PostgreSQL: 학생·동의·출석·공지·봉사
   ├── Redis: 세션·QR·일시 상태
   └── FastAPI: 얼굴 검출·신원 대조
```

## 기술 스택

- Java 25, Spring Boot 4.1
- Spring Web MVC, Spring Security (OAuth2 Client), Spring Data JPA, Spring Data Redis
- PostgreSQL 17, Redis 7, Flyway
- 테스트: JUnit 5
- CI: GitHub Actions

## 시작하기

```bash
git clone --recurse-submodules https://github.com/Start-Up-10th/CheckUp-server.git

# 이미 clone했다면
git submodule update --init
```

`docs-harness/`가 비어 있다면 서브모듈을 받지 않은 상태입니다. 위 명령을 실행하세요.

### 로컬 DB 띄우기

서버 실행과 테스트 모두 Postgres·Redis가 필요합니다. `compose.yaml`로 띄웁니다.

```bash
docker compose up -d
```

`application.yaml`의 기본값이 `compose.yaml`과 맞춰져 있어 별도 설정 없이 접속됩니다.

### 빌드·테스트·실행

- Java 25
- 로컬 DB가 떠 있어야 합니다.

```bash
./gradlew build
./gradlew bootRun
```

### 개인 설정

값을 바꾸고 싶으면 `src/main/resources/application-local.yaml`을 만들어 덮어씁니다.
이 파일은 커밋되지 않습니다. OAuth Secret 같은 비밀값도 여기에 둡니다.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/checkup
```

운영 서버에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` 환경변수로 값을 넣습니다.

## 명세·문서

공통 명세·지침·스킬은 [Start-Up-harness](https://github.com/Start-Up-10th/Start-Up-harness)에 있으며, 이 레포에는 `docs-harness/` 서브모듈로 연결되어 있습니다.

- 제품 명세: `docs-harness/docs/spec/`
- 아키텍처·기술 결정: `docs-harness/docs/architecture.md`, `docs-harness/docs/decisions.md`
- 백엔드 작업 지침: `docs-harness/server/AGENTS.md`

명세·계획·API 계약 수정은 이 레포가 아니라 Start-Up-harness 레포에서 커밋합니다.

### 하네스 최신화

서브모듈은 기록된 커밋에 고정되어 있어 자동으로 최신이 되지 않습니다.

```bash
git submodule update --remote docs-harness
git add docs-harness
git commit -m "chore: 하네스 최신화"
```

## 브랜치

- 기능 PR은 `develop`으로 보냅니다. approve 1개와 CI 통과가 필요합니다.
- `main`은 배포할 때만 `develop`에서 머지합니다.
