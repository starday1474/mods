# 전 직업 전투 동작 라이브러리 v1 (계획 2.5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 9개 하위 유형이 쓸 전투 동작을 한꺼번에 만든다. 대상은 근접, 궁수 활, 마법 시전, 소환, 치유, 유물 동작이다. 사용자가 나중에 휴대폰으로 한 번에 볼 수 있도록 미리보기 갤러리 페이지도 만든다.

**Architecture:**
- 계획 2의 동작 도구(`tools/anim/efanim.py`, `preview.py`, `build_anims.py`)를 확장한다.
- 키프레임 원본은 `mod/anim/*.json`에 두고, 생성물은 `mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat/`에 쓴다.
- Java 등록(스킬·무기에 연결)은 각 유형을 구현하는 계획(3·4)에서 한다. 이 계획은 **동작 파일, 미리보기, 갤러리**까지만 다룬다.

**Tech Stack:** Python 3.13 + numpy + matplotlib + pillow + pytest, Epic Fight 21.17.3.1 armature, claude.ai Artifact(갤러리).

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md` (5.2 하위 유형, 5.7.1 마법, 5.13 유물, 5.14 궁수 활)

## Global Constraints

- **좌표 규칙** (계획 2에서 확정):
  - `local = base @ (Rx@Ry@Rz)`, 각도는 도(°), 관절 로컬 축 기준 내재 회전
  - 위쪽은 +Z, 앞쪽은 +Y
  - 검날은 `Tool_R` 로컬 −Z
  - Root 이동은 미터 단위
- **공격 동작에서 Root 앞 이동은 되돌리지 않는다.** Epic Fight가 Root 이동을 실제 플레이어 이동으로 바꾸기 때문이다. 뒤로 물러나는 동작(백스텝 사격 등)만 원본에 `"root_back": true`를 명시한다.
- **동작은 전부 새로 디자인한다.** 다른 게임의 특정 동작을 베끼지 않는다. 궁수는 "몬헌 월드 활의 조작 리듬"을 손맛 기준으로만 삼는다(스펙 5.14).
- 원본 파일 이름: `<그룹>_<동작>.json`
  - 그룹: `balanced`, `tank`, `infight`, `archer`, `mage`, `summoner`, `healer`, `relic`, `common`
  - 원본 필드 `"group"`, `"desc_ko"`(한국어 한 줄 설명)는 필수다.
- 반복 동작(대기, 유지 시전)은 `"loop": true`. 첫 키와 마지막 키의 자세가 같아야 한다.
- 길이: 공격·시전 0.3~1.2초, 반복 동작 0.8~2.0초. 키 5~10개.
- 기준 자세(`base`): 그 무기에 맞는 EF 동작(`epicfight:biped/living/...`)이나 `rest`. EF jar에 실제로 있는 경로만 쓴다.
- 완성 기준: 동작마다 GIF와 접촉 시트가 있고, 구현자가 접촉 시트를 직접 보고 확인한다. 확인 항목은 관절 꺾임 없음, 의도한 궤적, 시작·끝 연결이다.

## Review Focus

1. **관절이 비틀리거나 몸 안을 관통하는 자세:** 접촉 시트로 확인한다. 팔꿈치·무릎은 한 방향으로만 굽어야 한다. (각 작업의 시각 확인 단계)
2. **반복 동작의 끝과 시작이 튐:** `loop` 동작은 첫 키와 마지막 키가 같아야 한다. (Task 1 `test_loop_closes`)
3. **Root가 의도치 않게 뒤로 감:** `root_back`이 없는데 뒤로 가면 실패한다. (Task 1 테스트 확장)
4. **EF jar에 없는 base 경로:** 빌드가 실패해야 한다. (Task 1 `test_missing_base_fails`)
5. **갤러리에서 설명과 동작이 맞지 않음:** `desc_ko`가 실제 동작을 설명하는지 리뷰어가 접촉 시트와 대조한다.

---

### Task 1: 도구 확장 (반복 동작, 뒤 이동 허용, 메타데이터, 갤러리 데이터)

**Files:**
- Modify: `tools/anim/build_anims.py`, `tools/anim/tests/test_build_anims.py`, 기존 `mod/anim/balanced_auto{1,2,3}.json` (`group`, `desc_ko` 추가)
- Create: `tools/anim/gallery.py`, `tools/anim/tests/test_gallery.py`

**Interfaces:**
- Produces:
  - 원본 필드 추가:
    - `group`, `desc_ko`: 필수
    - `loop`: 선택, 기본 false
    - `root_back`: 선택, 기본 false
    - `phase`, `trail`: 공격 동작만. 없어도 된다.
  - `build_anims.py`: `phase`나 `trail`이 없으면 trail data 파일을 쓰지 않는다. 지금 동작은 그대로 유지한다.
  - `gallery.py SRC_DIR PREVIEW_DIR OUT_DIR`:
    - `OUT_DIR/manifest.json`을 만든다. 형식은 `[{"name","group","desc_ko","length","loop","gif":"<name>.gif"}]`이고 group 순서·이름 순으로 정렬한다.
    - GIF를 `OUT_DIR/`에 복사한다.

- [ ] **Step 1: 테스트 작성**
  - `test_loop_closes`: `loop` 원본의 첫 키와 마지막 키가 다르면 빌드가 실패한다.
  - `test_root_back_flag`: `root_back` 없이 Root가 뒤로 가면 실패하고, 있으면 통과한다. 기존 `test_root_never_moves_back`를 확장한다.
  - `test_missing_meta_fails`: `group`이나 `desc_ko`가 없으면 실패한다.
  - `test_missing_base_fails`: 없는 `epicfight:` 경로를 쓰면 실패한다.
  - `test_no_phase_no_trail_file`: `phase`와 `trail`이 없으면 trail data 파일이 생기지 않는다.
  - `test_gallery_manifest`: 원본 3개와 GIF 3개로 실행하면 manifest에 항목 3개가 정렬된 순서로 들어 있다.
- [ ] **Step 2:** 실패를 확인한다. 그다음 구현하고, `python3 -m pytest tools/anim -q && python3 -m pytest tools prism -q`가 통과하는지 확인한다. 이어서 `build_anims.py ... --check`도 통과해야 한다(밸런스 3개에 메타를 넣은 뒤 다시 생성).
- [ ] **Step 3:** 커밋한다: `feat(tools): loop/back-step/meta support and gallery manifest for animations`

### Task 2: 전위 동작 (밸런스형 추가, 탱킹형, 인파이트)

**Files:** `mod/anim/{balanced,tank,infight}_*.json`과 생성물

동작 목록(이름은 고정, 내용은 구현자가 디자인):
- **balanced:**
  - `balanced_dash`: 돌진 베기
  - `balanced_shield_bash`: 방패 밀치기
  - `balanced_counter`: 가드 후 반격 베기
  - `balanced_idle`: loop, 검과 방패 대기 자세
- **tank:**
  - `tank_auto1`, `tank_auto2`: 방패를 앞세운 짧은 베기 2연격
  - `tank_shield_slam`: 방패 내려찍기
  - `tank_taunt`: 방패를 두드리며 도발
  - `tank_fortress`: loop, 낮게 버티는 철벽 자세
- **infight** (대검·쌍검, 공격적):
  - `infight_gs_auto1`, `infight_gs_auto2`, `infight_gs_auto3`: 대검 3연격
  - `infight_dual_auto1`, `infight_dual_auto2`, `infight_dual_auto3`, `infight_dual_auto4`: 쌍검 4연격
  - `infight_leap_slam`: 도약 내려찍기
  - `infight_whirlwind`: 회전 베기

- [ ] **Step 1:** 원본을 작성하고 빌드한다. `--preview build/anim-preview`로 미리보기도 만든다.
- [ ] **Step 2:** 접촉 시트를 직접 보고 고친다. Global Constraints의 확인 항목을 기준으로 한다.
- [ ] **Step 3:** `pytest tools/anim`과 `--check`가 통과하는지 확인한다. 그다음 커밋한다: `feat(anim): vanguard motion set`

### Task 3: 궁수 활 동작 (스펙 5.14)

**Files:** `mod/anim/archer_*.json`과 생성물

- 기준 자세는 EF 활 관련 동작에서 찾는다. 활은 오른손(주 손)에 든다.

동작 목록:
- `archer_draw`: loop, 당긴 채 조준 유지
- `archer_charge_shot`: 차지 사격 발사
- `archer_follow_shot`: 연속 사격. 쏜 직후 빠르게 다시 당겨 쏜다.
- `archer_power_shot`: 강사격. 발을 넓게 버티고 다발로 쏜다.
- `archer_arc_shot`: 곡사. 하늘을 향해 쏜다.
- `archer_pierce_stance`: 관통 일격. 크게 버티는 자세로 길게 당겼다 쏜다. 반동으로 약간 밀린다(`root_back`).
- `archer_quick_shot`: 이동 중 빠른 사격
- `archer_charge_step`: 옆으로 짧게 미끄러지며 시위를 다시 당긴다. 차지 스텝용.
- `archer_backstep_shot`: 뒤로 뛰며 쏜다(`root_back`).

- [ ] **Step 1~3:** Task 2와 같다. 커밋: `feat(anim): archer bow motion set`

### Task 4: 마법사 시전 동작 (스펙 5.7.1)

**Files:** `mod/anim/mage_*.json`과 생성물

- 지팡이는 오른손에 든다. 왼손은 마법을 다루는 손이다.

동작 목록:
- `mage_idle`: loop
- `mage_cast_instant`: 손을 뻗어 즉시 시전
- `mage_cast_long_start`, `mage_cast_long_loop`(loop), `mage_cast_long_release`: 영창. 두 손을 모아 들어 올린다.
- `mage_channel_loop`: loop, 유지 시전. 손을 앞으로 뻗은 채 버틴다.
- `mage_charge_hold`(loop), `mage_charge_release`: 차지 시전
- `mage_staff_auto1`, `mage_staff_auto2`: 지팡이 근접 2연격
- `mage_circle_raise`: 지팡이를 세워 발밑에 마법진을 펼친다. 높은 서클 마법용.

- [ ] **Step 1~3:** Task 2와 같다. 커밋: `feat(anim): mage casting motion set`

### Task 5: 소환사·치유사 동작

**Files:** `mod/anim/{summoner,healer}_*.json`과 생성물

동작 목록:
- **summoner:**
  - `summoner_summon`: 땅에 손을 뻗어 소환
  - `summoner_command`: 손가락으로 대상을 지정
  - `summoner_empower`: 두 팔을 벌려 소환수 강화
  - `summoner_idle`: loop
- **healer:**
  - `healer_heal_single`: 한 손을 대상에게 뻗는다
  - `healer_heal_area`: 지팡이를 높이 들어 범위 회복
  - `healer_blessing`: 두 손을 모아 기도
  - `healer_spirit_call`: 회복 정령 부르기
  - `healer_idle`: loop

- [ ] **Step 1~3:** Task 2와 같다. 커밋: `feat(anim): summoner and healer motion sets`

### Task 6: 유물사 동작 (스펙 5.13)

**Files:** `mod/anim/relic_*.json`과 생성물

동작 목록:
- `relic_needle_flick`: 천의 바늘. 손가락을 튕겨 바늘 하나를 날린다.
- `relic_needle_volley`: 두 팔을 휘둘러 바늘 다발을 날린다.
- `relic_needle_guard`: loop, 손을 펼쳐 바늘 방벽을 유지한다.
- `relic_ring_flame_throw`: 불의 반지. 주먹을 내질러 화염을 던진다.
- `relic_ring_flame_sweep`: 팔을 크게 휘둘러 불길을 쓸어 낸다.
- `relic_activate`: 장신구 유물을 손에 쥐고 깨운다.
- `relic_idle`: loop

- [ ] **Step 1~3:** Task 2와 같다. 커밋: `feat(anim): relicist motion set`

### Task 7: 갤러리 페이지와 문서

**Files:**
- Create: `docs/design/motion-gallery.md` (동작 목록과 설명, 사용자 검토용)
- 갤러리 Artifact: claude.ai, 컨트롤러가 직접 게시한다.

- [ ] **Step 1:** `python3 tools/anim/gallery.py mod/anim build/anim-preview build/gallery`로 manifest를 만든다.
- [ ] **Step 2:** `docs/design/motion-gallery.md`를 쓴다. 그룹별 표(이름, 설명, 길이, 반복 여부)를 만들고, 끝에 "사용자 검토 필요"를 붙인다. 커밋: `docs: motion gallery list for review`
- [ ] **Step 3 (컨트롤러):** manifest와 GIF로 그룹별 탭이 있는 갤러리 Artifact를 게시하고 링크를 사용자에게 준다.
