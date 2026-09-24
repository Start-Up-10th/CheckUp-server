# CheckUp 서버 — 작업 진입점

공통 명세·지침·스킬은 `docs-harness/` 서브모듈([Start-Up-harness](https://github.com/Start-Up-10th/Start-Up-harness))에 있다.
서브모듈 문서 안의 상대 경로(`docs/...`, `.agents/...`, `server/...`)는 `docs-harness/` 기준으로 해석한다.

## 먼저 읽기

1. [docs-harness/AGENTS.md](docs-harness/AGENTS.md) — 공통 작업 지침
2. [docs-harness/server/AGENTS.md](docs-harness/server/AGENTS.md) — 백엔드 작업 지침
3. 작업 관련 명세·계획: `docs-harness/docs/spec/`, `docs-harness/docs/plans/`

## 스킬

스킬은 이 레포의 `.claude/skills`로 동기화하지 않는다. 필요한 스킬의 `SKILL.md`를 직접 읽고 따른다.

- 기능 구현: `docs-harness/.agents/skills/dorm-implement/SKILL.md`
- 검증: `docs-harness/.agents/skills/dorm-verify/SKILL.md`
- 정책 변경: `docs-harness/.agents/skills/dorm-spec-update/SKILL.md`
- Docker: `docs-harness/.agents/skills/dorm-docker/SKILL.md`

## 이 레포의 범위

- 이 레포는 Spring 서버 코드만 관리한다. 명세·계획·계약·스킬 수정은 Start-Up-harness 레포에 커밋한다.
- `docs-harness/` 안의 파일을 이 레포 커밋으로 수정하지 않는다. 서브모듈 갱신은 커밋 포인터만 올린다.
- 서버 검사: `./gradlew build`. 하네스 검사: `docs-harness/`에서 `npm run harness:check`.
