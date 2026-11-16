# 모드팩 기반 (계획 1/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** packwiz로 관리되는 1.21.1 NeoForge 모드팩을 만들고, 규칙 검증 도구·서버 로딩 테스트·기본 설정·한글 패치 뼈대·Prism 인스턴스 템플릿까지 갖춰 사용자가 바로 실행해 볼 수 있게 한다.

**Architecture:** 저장소 루트에 `pack/`(packwiz 루트), `tools/`(Python 검증·번역 도구와 테스트, 서버 스모크 테스트 스크립트), `prism/`(친구용 Prism 인스턴스 템플릿), `docs/`를 둔다. 모드 규칙(버전·개수·제외 목록·필수 목록·클라이언트 전용)은 `tools/pack_rules.py` 한 곳에 두고 `tools/validate_pack.py`가 검사한다. 실제 로딩 여부는 NeoForge 전용 서버를 띄워 확인한다.

**Tech Stack:** packwiz (Go, `~/go/bin/packwiz`), Python 3 + pytest, NeoForge 1.21.1 서버, packwiz-installer-bootstrap, yosbr-neo(첫 실행 기본 설정).

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md`

**이 계획의 범위:** 설계 9장의 1단계(모드팩 기반). 우리 RPG 모드(계획 2)와 나머지 유형·보스 재조정·퀘스트·번역 본작업(계획 3)은 다루지 않는다. Epic Fight의 회피·가드 외 스킬 차단은 우리 모드 코드에서 하므로 계획 2에서 다룬다.

## Global Constraints

- Minecraft `1.21.1`, 로더 NeoForge `21.1.x` (Epic Fight 최신 1.21.1 빌드가 요구하는 NeoForge 버전 이상, 최신 21.1.x 안정판).
- 모드 수(`pack/mods/*.pw.toml`) **200개 이하**, 목표 130~150.
- 모드는 Modrinth를 우선, 없으면 CurseForge. 수동 다운로드가 필요한 모드는 넣지 않는다.
- **넣지 않는 모드:** Iron's Spells 'n Spellbooks, Better Combat, Project MMO, Pufferfish's Skills(및 연동 애드온), The Aether, The Twilight Forest, World Bosses, Ultimate Bosses, Dimensions & Dungeons, The Bumblezone, Advent of Ascension, C2ME, Krypton Reno, 지도 모드 전부(Xaero's, JourneyMap, FTB Chunks 등).
- 모드 jar는 저장소에 커밋하지 않는다.
- 저장소는 공개. 다른 사람의 한글 패치를 넣지 않는다.
- 기본 설정(처음 한 번만 적용): 시야 10, 시뮬레이션 6, 엔티티 거리 0.75, 입자 "줄임", 쉐이더 꺼짐, Distant Horizons 꺼짐, 한글 리소스팩 켜짐, 언어 ko_kr.
- 메모리: 참가자 기본 6144MB, 월드 여는 사람 8192MB로 시작(가이드에서 안내).
- 친구용 업데이트 URL: `https://raw.githubusercontent.com/starday1474/mods/main/pack/pack.toml` (main 브랜치 기준).
- 문서·사용자에게 보이는 글은 한국어.

## Review Focus

1. **CurseForge 전용 모드가 packwiz로 받아지지 않는 경우** — 수동 다운로드가 필요하면 팩에 남지 않고 보고서에 "대체/제외"로 기록되어야 한다. (Task 3 테스트: `metadata:curseforge` 모드는 `validate_pack`이 경고 목록에 올림)
2. **의존 라이브러리가 빠진 상태** — 서버가 "missing dependency"로 뜨지 않아야 한다. (Task 7 스모크 테스트가 로그에서 `Missing or unsupported mandatory dependencies` 부재를 확인)
3. **클라이언트 전용 모드가 서버 쪽에 표시된 경우** — 서버 실행이 크래시하면 안 된다. (Task 2 규칙 `CLIENT_ONLY` + Task 7)
4. **친구가 바꾼 설정이 업데이트로 덮어써지는 경우** — 기본 설정은 `config/yosbr/` 아래에만 두고 실제 `options.txt`를 팩에 넣지 않는다. (Task 5 테스트: 팩 루트에 `options.txt` 없음)
5. **제외 모드가 다른 모드의 의존으로 몰래 들어오는 경우** — 검증기는 직접 추가한 것뿐 아니라 packwiz가 자동 추가한 의존도 검사한다. (Task 2 테스트 `test_denied_dependency_detected`)

---

## File Structure

```
pack/
  pack.toml, index.toml          # packwiz
  mods/*.pw.toml                 # 모드 메타데이터 (jar 없음)
  config/yosbr/options.txt       # 첫 실행 기본 설정
  config/yosbr/config/iris.properties
  config/yosbr/config/DistantHorizons.toml
  resourcepacks/rpg-korean/pack.mcmeta
  resourcepacks/rpg-korean/assets/.gitkeep
tools/
  pack_rules.py                  # 규칙 데이터 (한 곳)
  validate_pack.py               # 팩 검증 CLI
  fetch_jars.py                  # pw.toml → jar 캐시 다운로드(해시 검증)
  lang_report.py                 # en_us 대비 ko_kr 누락 키 보고
  smoke_server.sh                # NeoForge 서버 로딩 테스트
  requirements-dev.txt           # pytest
  tests/test_validate_pack.py
  tests/test_lang_report.py
  tests/test_fetch_jars.py
  tests/fixtures/...
prism/
  instance.cfg, mmc-pack.json    # 친구용 인스턴스 템플릿
  build_instance.py              # 템플릿 → 가져오기용 zip
  tests/test_build_instance.py
docs/
  translation/glossary.md        # 용어집
  translation/coverage.md        # lang_report 결과
  admin/modpack.md               # 관리자 문서 (모드 추가·검증·스모크 테스트)
  guide/install.md               # 설치 가이드 초안 (완성 시 최종 정리)
.gitignore                       # build/, .cache/, *.jar
```

---

### Task 1: 저장소 뼈대와 packwiz 초기화

**Files:**
- Create: `pack/pack.toml`, `pack/index.toml`, `.gitignore`, `tools/requirements-dev.txt`, `docs/admin/modpack.md`

**Interfaces:**
- Produces: `pack/` packwiz 루트. 이후 모든 모드 추가는 `cd pack && ~/go/bin/packwiz ...`.

- [ ] **Step 1:** NeoForge 버전 결정: `curl -s https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml`에서 `21.1.`로 시작하는 최신 안정판(베타 접미사 없는 것)을 고르고, Modrinth `epic-fight` 최신 1.21.1 neoforge 버전의 의존 요구치 이상인지 확인.
- [ ] **Step 2:** `cd pack && ~/go/bin/packwiz init --name "RPG 모드팩" --author starday1474 --version 0.1.0 --mc-version 1.21.1 --modloader neoforge --neoforge-version <Step 1 값> -r` 실행.
- [ ] **Step 3:** `.gitignore`에 `build/`, `.cache/`, `*.jar`, `__pycache__/` 추가. `tools/requirements-dev.txt`에 `pytest`. `pip install -r tools/requirements-dev.txt`.
- [ ] **Step 4:** `docs/admin/modpack.md`에 한국어로: packwiz 설치(`go install github.com/packwiz/packwiz@latest`), 모드 추가 명령(`packwiz mr add <slug>` / `packwiz cf add <slug>`), `packwiz refresh`, 검증·스모크 테스트 실행법(이후 Task에서 채움 표시 대신 명령을 바로 적는다).
- [ ] **Step 5:** 확인: `cd pack && ~/go/bin/packwiz refresh` 성공, `grep 'minecraft = "1.21.1"' pack/pack.toml`.
- [ ] **Step 6:** Commit `chore: init packwiz modpack (1.21.1 NeoForge)`.

### Task 2: 팩 규칙과 검증기

**Files:**
- Create: `tools/pack_rules.py`, `tools/validate_pack.py`, `tools/tests/test_validate_pack.py`, `tools/tests/fixtures/pack_ok/…`, `tools/tests/fixtures/pack_bad/…`

**Interfaces:**
- Produces:
  - `pack_rules.MC_VERSION = "1.21.1"`, `MAX_MODS = 200`
  - `pack_rules.DENIED: dict[str, str]` — 정규화된 이름(소문자, 영숫자만) → 사유. 키: `ironsspellsnspellbooks`, `bettercombat`, `projectmmo`, `pmmo`, `puffishskills`, `pufferfishsskills`, `aether`, `thetwilightforest`, `twilightforest`, `worldbosses`, `ultimatebosses`, `dimensionsanddungeons`, `thebumblezone`, `bumblezone`, `adventofascension`, `c2me`, `kryptonreno`, `xaerosminimap`, `xaerosworldmap`, `journeymap`, `ftbchunks`
  - `pack_rules.REQUIRED: set[str]` — 정규화 이름: `epicfight`, `essential`, `sodium`, `lithium`, `ferritecore`, `modernfix`, `lenderscataclysm`, `curios`, `geckolib`, `kubejs`, `yosbrneo`
  - `pack_rules.CLIENT_ONLY: set[str]` — `sodium`, `iris`, `immediatelyfast`, `entityculling`, `distanthorizons`
  - `validate_pack.normalize(name: str) -> str`
  - `validate_pack.validate(pack_dir: Path) -> Report` where `Report` is a dataclass `{errors: list[str], warnings: list[str], mod_count: int}`
  - CLI: `python3 tools/validate_pack.py pack [--allow-missing-required]` → 오류 있으면 exit 1, 경고·개수 출력. 플래그는 REQUIRED 누락만 경고로 낮춘다 (모드를 나눠 추가하는 Task 3용).
- 검사 대상 이름: 각 `mods/*.pw.toml`의 파일명 stem과 `name` 필드 둘 다 정규화해 검사.

- [ ] **Step 1: 실패하는 테스트 작성** (`tools/tests/test_validate_pack.py`) — fixture 팩을 `tmp_path`에 만드는 헬퍼 `make_pack(tmp_path, mods: dict[str, dict], mc="1.21.1")` 포함:
  - `test_ok_pack_passes`: REQUIRED 전부 + 정상 모드 → `errors == []`
  - `test_wrong_mc_version`: mc `"1.20.1"` → errors에 `"1.21.1"` 포함
  - `test_denied_mod_by_filename`: `irons-spells-n-spellbooks.pw.toml` → errors에 `"ironsspellsnspellbooks"` 포함
  - `test_denied_dependency_detected`: 파일명 `some-lib.pw.toml`, `name = "Better Combat"` → errors에 `"bettercombat"`
  - `test_too_many_mods`: 201개 → errors에 `"200"` 포함
  - `test_missing_required`: `essential` 없음 → errors에 `"essential"`
  - `test_client_only_side`: `sodium`의 `side = "both"` → errors에 `"sodium"`과 `"client"`
  - `test_curseforge_mod_warns`: `[update.curseforge]` 있는 모드 → warnings에 해당 이름 (수동 다운로드 가능성 점검용)
  - `test_mod_count_reported`: `mod_count`가 pw.toml 수와 같다
  - `test_allow_missing_required_flag`: `essential` 없음 + 플래그 → errors 비고 warnings에 `"essential"`
- [ ] **Step 2:** `python3 -m pytest tools/tests/test_validate_pack.py -v` → 전부 FAIL (모듈 없음).
- [ ] **Step 3:** `pack_rules.py`, `validate_pack.py` 구현. TOML은 `tomllib`.
- [ ] **Step 4:** 같은 명령 → 전부 PASS.
- [ ] **Step 5:** Commit `feat(tools): pack rules and validator`.

### Task 3: 모드 추가 — 전투·무기·보스·차원·던전

**Files:**
- Create: `pack/mods/*.pw.toml` (packwiz 생성), `docs/admin/mod-sources.md`

**Interfaces:**
- Consumes: `validate_pack` CLI (Task 2)

모드는 `cd pack && ~/go/bin/packwiz mr add <slug> -y`로 추가 (의존 자동 추가 수락). Modrinth에 1.21.1 NeoForge 버전이 없는 것만 `packwiz cf add <slug>`.

| 분류 | Modrinth slug | CurseForge (Modrinth에 없을 때) |
|---|---|---|
| 전투 | `epic-fight`, `epic-fight-mod-compat`, `epic-fight-weapons-compat` | |
| 무기·방어구 | `simply-swords`, `epic-knights-shields-armor-and-weapons` | |
| 보스 | `l_enders-cataclysm`, `bossesrise`, `bosses-of-mass-destruction-forge`, `mowzies-mobs`, `borninchaos`, `mutant-monsters`, `abyssal-corrupter`, `boss-ai-tweaks`, `multiplayerbosses` | `true-ending`, `brutal-bosses` |
| 보류(넣지 않음, 기록만) | Mo'Bosses, Alex's Caves | |
| 차원 | `eternal-starlight`, `the-undergarden`, `deeperdarker`, `unusual_end` | |
| 던전·구조물 | `when-dungeons-arise`, `yungs-better-dungeons`, `yungs-better-strongholds`, `yungs-better-mineshafts`, `yungs-better-desert-temples`, `yungs-better-ocean-monuments`, `yungs-better-nether-fortresses`, `yungs-better-witch-huts`, `yungs-better-jungle-temples`, `dungeons-and-taverns`, `towns-and-towers`, `lootr` | |

- [ ] **Step 1:** 표의 Modrinth 모드를 추가. 실패한 slug는 기록.
- [ ] **Step 2:** CurseForge 모드 추가 (`packwiz cf add true-ending`, `packwiz cf add brutal-bosses`). 1.21.1 NeoForge 파일이 없거나 "manual download"로 표시되면 추가하지 않는다. True Ending이 안 되면 대안 `savage-ender-dragon`(Modrinth→CF 순)을 시도.
- [ ] **Step 3:** `docs/admin/mod-sources.md`에 각 모드의 출처(Modrinth/CF), 버전, 그리고 넣지 못한 모드와 사유를 표로 기록.
- [ ] **Step 4:** `python3 tools/validate_pack.py pack --allow-missing-required` → exit 0 (나머지 필수 모드는 Task 4에서 추가).
- [ ] **Step 5:** Commit `feat(pack): combat, bosses, dimensions, dungeons`.

### Task 4: 모드 추가 — 장비·편의·멀티·그래픽·최적화

**Files:**
- Create: `pack/mods/*.pw.toml`
- Modify: `docs/admin/mod-sources.md`

| 분류 | Modrinth slug | CurseForge |
|---|---|---|
| 장비·편의 | `apotheosis`, `waystones`, `sophisticated-backpacks`, `artifacts`, `curios`, `corpse`, `jei`, `jade`, `simple-voice-chat`, `kubejs`, `geckolib` | `ftb-quests-forge` |
| 멀티 | `essential`, `e4mc` | |
| 그래픽 | `sodium`, `iris`, `distanthorizons` | |
| 최적화 | `lithium`, `ferrite-core`, `modernfix`, `entityculling`, `immediatelyfast`, `clumps`, `chunky`, `spark` | |
| 테스트 후 결정(넣지 않음, 기록만) | `noisiumed`, `async-locator-refined` | |
| 기본 설정 | `yosbr-neo` | |

- [ ] **Step 1:** 추가. CLIENT_ONLY 모드의 pw.toml에 `side = "client"`가 아니면 수정.
- [ ] **Step 2:** `python3 tools/validate_pack.py pack` → exit 0, 플래그 없이. 출력의 모드 수 기록 (200 이하 필수).
- [ ] **Step 3:** `mod-sources.md` 갱신.
- [ ] **Step 4:** Commit `feat(pack): gear, utility, multiplayer, graphics, performance mods`.

### Task 5: 첫 실행 기본 설정 (yosbr)

**Files:**
- Create: `pack/config/yosbr/options.txt`, `pack/config/yosbr/config/iris.properties`, `pack/config/yosbr/config/DistantHorizons.toml`
- Modify: `tools/validate_pack.py`, `tools/tests/test_validate_pack.py`

**Interfaces:**
- Produces: `validate_pack.check_defaults(pack_dir: Path) -> list[str]` (오류 목록), `validate()`가 호출.

- [ ] **Step 1: 실패하는 테스트**
  - `test_defaults_present_and_correct`: `config/yosbr/options.txt`에 `renderDistance:10`, `simulationDistance:6`, `entityDistanceScaling:0.75`, `particles:1`, `lang:ko_kr`, `resourcePacks:` 줄에 `"file/rpg-korean"` 포함 → 오류 없음
  - `test_defaults_wrong_value`: `renderDistance:12` → 오류에 `renderDistance`
  - `test_iris_disabled`: `iris.properties`에 `enableShaders=false` 필요
  - `test_dh_disabled`: `DistantHorizons.toml`에 `quickEnableRendering = false` 필요
  - `test_no_root_options_txt`: `pack/options.txt` 존재 시 오류 (Review Focus 4)
- [ ] **Step 2:** pytest → FAIL.
- [ ] **Step 3:** `check_defaults` 구현, 세 설정 파일 작성. DistantHorizons 키 이름은 DH 1.21.1 빌드의 기본 생성 config로 확인하고, 다르면 테스트·파일·`Global Constraints` 설명을 실제 키로 맞춘다.
- [ ] **Step 4:** pytest PASS, `python3 tools/validate_pack.py pack` exit 0, `cd pack && ~/go/bin/packwiz refresh`.
- [ ] **Step 5:** Commit `feat(pack): first-launch default settings`.

### Task 6: 한글 리소스팩 뼈대, jar 캐시, 번역 누락 보고

**Files:**
- Create: `pack/resourcepacks/rpg-korean/pack.mcmeta`, `pack/resourcepacks/rpg-korean/assets/.gitkeep`, `tools/fetch_jars.py`, `tools/lang_report.py`, `tools/tests/test_fetch_jars.py`, `tools/tests/test_lang_report.py`, `docs/translation/glossary.md`, `docs/translation/coverage.md`

**Interfaces:**
- Produces:
  - `fetch_jars.fetch(pack_dir: Path, cache_dir: Path) -> FetchResult` (`downloaded: list[Path]`, `skipped: list[str]` — URL 없는 CF 모드 등). sha512/sha1 해시를 pw.toml의 `[download]` 값과 대조, 불일치 시 파일 삭제 후 예외.
  - `lang_report.missing_keys(jar: Path, overlay_dir: Path) -> dict[str, list[str]]` — modid → ko_kr에 없는 en_us 키. ko_kr는 jar 내부 + `overlay_dir/assets/<modid>/lang/ko_kr.json` 합집합.
  - `lang_report.write_coverage(results, out: Path)` — 모드별 전체 키 수·누락 수·비율 표(한국어 머리글).
  - CLI: `python3 tools/fetch_jars.py pack .cache/jars`, `python3 tools/lang_report.py .cache/jars pack/resourcepacks/rpg-korean docs/translation/coverage.md`

- [ ] **Step 1: 실패하는 테스트**
  - `test_fetch_verifies_hash`: 로컬 `http.server`로 파일 제공, 올바른 sha512 → downloaded에 포함; 틀린 해시 → 예외, 캐시에 파일 없음
  - `test_fetch_skips_without_url`: `[download]`에 `url` 없음 → skipped에 이름
  - `test_missing_keys_basic`: fixture jar(zipfile로 생성) en_us `{a,b,c}`, ko_kr `{a}` → `{"modx": ["b","c"]}`
  - `test_overlay_fills_keys`: overlay에 `b` → `["c"]`
  - `test_no_en_us_ignored`: en_us 없는 jar → 결과에 없음
  - `test_coverage_table`: 출력 파일에 `| 모드 |`와 `modx`, `66.7%` 형태 누락 비율 포함
- [ ] **Step 2:** pytest → FAIL.
- [ ] **Step 3:** 구현. `pack.mcmeta`는 `{"pack": {"pack_format": 34, "description": "RPG 모드팩 한글 패치"}}`.
- [ ] **Step 4:** pytest PASS.
- [ ] **Step 5:** 실제 실행: fetch → lang_report → `docs/translation/coverage.md` 생성. 공식 ko_kr가 없는 모드 목록이 보이는지 확인.
- [ ] **Step 6:** `glossary.md`에 설계에서 정한 용어를 표로: 직업군(전위, 후위 딜러, 후위 서포터), 하위 유형 9개, 숙련도, 재능, 적성, 지식, 마력, 스킬, 여신, 여신의 서, 스크롤, 유물, 각인. 열: 한국어 / 영어 키 표기 / 설명.
- [ ] **Step 7:** `packwiz refresh`, Commit `feat(i18n): korean resource pack skeleton and coverage report`.

### Task 7: 서버 로딩 스모크 테스트

**Files:**
- Create: `tools/smoke_server.sh`
- Modify: `docs/admin/modpack.md`

**Interfaces:**
- Consumes: `pack/pack.toml`의 NeoForge 버전.
- Produces: `bash tools/smoke_server.sh` — 성공 시 exit 0, 실패 시 로그 경로 출력 후 exit 1.

동작 (스크립트가 할 일):
1. `build/server/`에 NeoForge 인스톨러(`https://maven.neoforged.net/releases/net/neoforged/neoforge/<ver>/neoforge-<ver>-installer.jar`)로 `--install-server`.
2. `packwiz-installer-bootstrap.jar`를 `https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar`에서 받는다 (실패하면 메시지와 함께 exit 2 — 네트워크 허용 필요 호스트를 출력).
3. 백그라운드로 `cd pack && packwiz serve --port 8085` 실행, `java -jar packwiz-installer-bootstrap.jar -g -s server http://localhost:8085/pack.toml`를 `build/server`에서 실행.
4. `eula=true`, `user_jvm_args.txt`에 `-Xmx6G`, `server.properties`에 `online-mode=false`, `level-seed=rpgsmoke`.
5. `./run.sh nogui`를 시간 제한 600초로 실행, 로그에 `Done (` 이 나오면 `stop` 입력.
6. 실패 조건: 시간 초과, `crash-reports/` 생성, 로그에 `Missing or unsupported mandatory dependencies`.

- [ ] **Step 1:** 스크립트 작성.
- [ ] **Step 2:** `bash tools/smoke_server.sh` 실행 → exit 0. 실패하면 로그를 읽고 원인 모드를 고친다(의존 추가, 클라이언트 전용 표시, 호환 안 되는 모드는 제외 후 `mod-sources.md`에 사유). 고칠 때마다 `validate_pack` 재실행.
- [ ] **Step 3:** 서버 메모리 사용량(`spark`이 로딩되었으면 `/spark heapsummary` 대신 로그의 JVM 정보면 충분)과 로딩 시간을 `docs/admin/modpack.md`에 기록.
- [ ] **Step 4:** Commit `test(pack): dedicated server smoke test`.

### Task 8: 친구용 Prism 인스턴스 템플릿과 설치 가이드 초안

**Files:**
- Create: `prism/instance.cfg`, `prism/mmc-pack.json`, `prism/build_instance.py`, `prism/tests/test_build_instance.py`, `docs/guide/install.md`

**Interfaces:**
- Produces: `build_instance.build(out_zip: Path, pack_url: str, neoforge_version: str) -> Path` — Prism에서 "가져오기" 가능한 zip (`instance.cfg`, `mmc-pack.json`, `.minecraft/packwiz-installer-bootstrap.jar`). CLI: `python3 prism/build_instance.py build/RPG-modpack.zip [--pack-url URL]` (기본은 Global Constraints의 main URL; 테스트용으로 브랜치 URL `https://raw.githubusercontent.com/starday1474/mods/claude/wizardly-gates-b1hzqk/pack/pack.toml` 지정).

- [ ] **Step 1: 실패하는 테스트**
  - `test_zip_contents`: zip에 `instance.cfg`, `mmc-pack.json`, `.minecraft/packwiz-installer-bootstrap.jar`
  - `test_prelaunch_command`: `instance.cfg`에 `PreLaunchCommand="$INST_JAVA" -jar packwiz-installer-bootstrap.jar <pack_url>`와 `OverrideCommands=true`
  - `test_memory_defaults`: `MaxMemAlloc=6144`, `OverrideMemory=true`
  - `test_neoforge_component`: `mmc-pack.json` components에 `net.neoforged` 버전 = 인자, `net.minecraft` = `1.21.1`
  - `test_jvm_args`: `JvmArgs`에 `-XX:+UseG1GC` 포함
  - `test_custom_pack_url`: `pack_url` 인자를 바꾸면 PreLaunchCommand에 그 URL
- [ ] **Step 2:** pytest → FAIL.
- [ ] **Step 3:** 구현. bootstrap jar는 Task 7이 받은 것을 재사용(없으면 같은 URL에서 받음). 기본 pack_url은 Global Constraints의 URL.
- [ ] **Step 4:** pytest PASS, CLI 실행해 `build/RPG-modpack.zip` 생성.
- [ ] **Step 5:** `docs/guide/install.md` 초안 (한국어, 윈도우): Prism Launcher 설치 → MS 계정 로그인 → zip 가져오기 → 첫 실행 시 자동 다운로드 창 → 월드 여는 사람은 메모리 8192MB로 변경하는 법 → Chunky로 미리 생성(`/chunky radius 2500`, `/chunky start`) → 쉐이더/DH 켜는 법 → 저사양/기본/영상 촬영 프리셋 표(설계 6.5) → 자주 생기는 문제. 각 단계에 "스크린샷 자리" 표시는 넣지 않고, 화면에서 누를 버튼 이름을 글로 정확히 적는다.
- [ ] **Step 6:** Commit `feat(prism): instance template and install guide draft`.

### Task 9: 마무리 검증과 사용자 테스트 안내

**Files:**
- Modify: `docs/admin/modpack.md`

- [ ] **Step 1:** 전체 테스트 `python3 -m pytest tools prism -v` PASS, `python3 tools/validate_pack.py pack` exit 0, `bash tools/smoke_server.sh` exit 0.
- [ ] **Step 2:** `docs/admin/modpack.md`에 "사용자 테스트 체크리스트" 추가 (설계 10장 중 클라이언트에서만 확인 가능한 것): Epic Fight + Iris, Essential 코스튬, Essential 2인 접속, 16GB PC 메모리·프레임, 각 보스와 Epic Fight 전투.
- [ ] **Step 3:** 브랜치 푸시 후 `python3 prism/build_instance.py build/RPG-modpack-test.zip --pack-url <브랜치 URL>`로 테스트용 zip을 만들고, 사용자가 이 zip으로 테스트하는 법을 문서에 적는다.
- [ ] **Step 4:** Commit `docs: admin checklist and test build instructions`.
