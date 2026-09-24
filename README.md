# CheckUp Server

기숙사 출석 관리 서비스의 Spring Boot 백엔드입니다.

공통 명세·지침·스킬은 [Start-Up-harness](https://github.com/Start-Up-10th/Start-Up-harness)에 있으며, 이 레포에는 `docs-harness/` 서브모듈로 연결되어 있습니다.

## 시작하기

```bash
git clone --recurse-submodules https://github.com/Start-Up-10th/CheckUp-server.git

# 이미 clone했다면
git submodule update --init
```

`docs-harness/`가 비어 있다면 서브모듈을 받지 않은 상태입니다. 위 명령을 실행하세요.

## 하네스 최신화

서브모듈은 기록된 커밋에 고정되어 있어 자동으로 최신이 되지 않습니다.

```bash
git submodule update --remote docs-harness
git add docs-harness
git commit -m "chore: 하네스 최신화"
```

명세·계획·API 계약 수정은 이 레포가 아니라 Start-Up-harness 레포에서 커밋합니다.

## 빌드·테스트

- Java 25
- Docker 실행 중이어야 합니다. 테스트가 Testcontainers로 Postgres·Redis를 자동으로 띄웁니다.

```bash
./gradlew build
```

로컬에서 앱을 실행(`bootRun`)할 때는 `compose.yaml`로 DB·Redis를 띄우고 `.env.example`의 값을 환경변수로 넣습니다.

```bash
docker compose up -d --wait
set -a; source .env.example; set +a
./gradlew bootRun
```

하네스 검사는 서브모듈 안에서 실행합니다(Node.js 22 이상).

```bash
cd docs-harness && npm run harness:check
```

## 에이전트

Claude Code는 [CLAUDE.md](CLAUDE.md), Codex는 [AGENTS.md](AGENTS.md)에서 시작합니다.
두 파일은 `docs-harness/`의 공통 지침으로 연결되며, 안전 훅은 `docs-harness/harness/hooks.mjs`를 사용합니다.
