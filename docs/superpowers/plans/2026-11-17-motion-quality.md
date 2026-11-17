# 동작 품질 개선 (A+B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자 피드백 "동작이 전체적으로 엉성하다"(2026-11-17)를 해결한다.
- **(A)** 근접 직업은 모드팩에 이미 있는 전문가 제작 동작(Epic Fight, EpicFight-Extra, Weapons of Miracles)을 게임 안에서 불러 쓰는 구성으로 바꾼다.
- **(B)** 직접 만들 수밖에 없는 동작(궁수·마법사·소환사·치유사·유물사)은 도구 개선과 품질 보강으로 다듬는다.

**Architecture:**
- `build_anims.py`에 가속·감속(이징)과 촘촘한 중간 키 생성(베이크)을 추가한다. EF는 키 사이를 일정한 속도로 잇기 때문에, 이징은 중간 키를 미리 채워 넣는 방식으로만 표현할 수 있다.
- 전문가 동작을 분석해 만든 기준 문서로 품질 보강의 방향을 잡는다.
- 근접은 다른 모드의 동작 ID를 참조하는 "구성표"로 정리한다. 다른 모드의 파일은 복사하지 않는다.

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md`
**선행 작업:** `docs/superpowers/plans/2026-11-16-motion-library.md` (동작 57개, `mod/anim/`)

## Global Constraints

- 다른 모드의 동작 파일은 **복사하지 않는다**. 게임 안에서 그 모드의 동작을 ID로 불러 쓰는 방식만 쓴다. 분석과 미리보기 렌더링은 로컬에서만 하고, 저장소에 남기는 것은 렌더 이미지·구성표·기준 문서뿐이다.
- 좌표와 동작 규칙은 이전 계획과 같다. 내재 XYZ 회전, +Z 위, +Y 앞, Root 앞 이동 되돌리지 않기, `loop`, `root_back`, `offhand`(`none`/`shield`/`sword`/`bow`), `trail_joint`, `layer`를 그대로 쓴다.
- 기존 동작 이름과 파일명은 유지한다. Java의 `RpgAnimations`가 `balanced_auto1~3`을 참조하고 있다.
- 미리보기 렌더는 30fps로 올린다. GIF 크기는 동작당 600KB 이하를 목표로 한다.
- 모든 변경 후 `pytest tools/anim`, `pytest tools prism`, `build_anims --check`가 통과해야 한다.

## Review Focus

1. **이징 베이크 후 반복 동작의 이음매:** 마지막 키와 첫 키 사이가 튀면 안 된다. (Task 1 테스트)
2. **베이크된 키 수 폭증으로 asset이 과대해짐:** 동작당 키는 30Hz 상한을 넘지 않고, 원본에 `bake`가 없으면 결과가 이전과 똑같아야 한다. (Task 1 테스트)
3. **품질 보강 중 기존 연결이 깨짐:** 시작·끝 자세(대기 자세 복귀, 연계 이음매)를 유지해야 한다. (Task 3~4 검증)
4. **구성표가 존재하지 않는 동작 ID를 참조함:** jar 안에 실제로 있는 경로만 써야 한다. (Task 2 검사 스크립트)

---

### Task 1: 이징과 베이크

**Files:** `tools/anim/build_anims.py`, `tools/anim/preview.py`, `tools/anim/tests/`

원본 필드를 추가한다.
- 키별 `ease`: `linear`(기본), `in`, `out`, `inout`, `back`(살짝 넘쳤다 돌아오기)
- 원본 전체에 `bake`: 초당 키 수, 예 `30`

`bake`가 있으면 원본 키 사이를 해당 빈도로 샘플링해 중간 키를 생성한다. 회전은 쿼터니언 slerp에 이징 곡선을 적용하고, 이동은 선형 보간에 이징 곡선을 적용한다. 원본 키 시각은 정확히 보존한다.

미리보기 기본 fps는 30으로 바꾼다.

테스트:
- `bake` 없음: 결과가 이전과 같다(기존 asset `--check` 통과).
- `bake` 있음: 원본 키 시각의 자세가 같고, 중간 키 수가 정확하다.
- `ease in`: 구간 중앙의 진행도가 선형보다 작다.
- `back`: 진행도가 목표를 살짝 넘는다.
- 반복 동작: 베이크해도 첫 키와 마지막 키가 같다.

### Task 2: 전문가 동작 분석과 근접 구성표 (A)

**Files:** `docs/design/motion-reference.md`, `docs/design/vanguard-moveset.md`, `tools/anim/catalog.py` (+ 테스트), `build/ref-preview/` (gitignored)

- `catalog.py`는 EF·EF-Extra·WOM jar의 동작 목록을 뽑는다. 결과는 경로, 길이, 키 수, 움직이는 관절 수이고, 지정한 동작은 미리보기를 렌더링한다.
- **`motion-reference.md`:** 전문가 동작 10개 이상을 분석해 우리 기준을 문서로 만든다. 다룰 항목은 키 밀도, 예비 동작·타격·회복의 시간 비율, 관절별 참여도(허리·머리·다리), 넘침·여운이다.
- **`vanguard-moveset.md`:** 밸런스형, 탱킹형, 인파이트(대검·쌍검)마다 평타, 대시 공격, 공중 공격, 스킬 후보를 표로 만든다. 열은 동작 ID, 출처 모드, 길이, 선택 이유다. 모든 ID는 jar에 실제로 있어야 하고, 검사 스크립트로 확인한다.
- 근접 동작 중 우리가 직접 만든 것은 구성표에서 "대체 후보 있음/없음"으로 표시한다. 대체 후보가 없는 것(예: 방패 밀치기, 도발)은 Task 3에서 다듬는다.
- 구성표 후보의 미리보기를 렌더링해 둔다(갤러리용).

### Task 3: 근접 자작 동작 품질 보강 (B, 대체 후보 없는 것만)

**Files:** `mod/anim/{balanced,tank,infight}_*.json` 중 Task 2에서 "대체 없음"으로 표시된 것과 `balanced_auto1~3`

- `motion-reference.md` 기준과 Task 1의 이징·베이크로 다시 다듬는다.
- 보강할 것: 키 추가, 예비 동작, 타격 순간의 가속, 여운과 넘침, 허리·골반 비틀기, 체중 이동, 머리 따라가기.
- 동작마다 전후 비교 시트를 만든다: `build/anim-preview/compare/<name>_before.png`, `_after.png`.

### Task 4: 비근접 동작 품질 보강 (B)

**Files:** `mod/anim/{archer,mage,summoner,healer,relic}_*.json`

Task 3과 같은 방식으로 보강하고 전후 비교 시트를 만든다. 그룹별 커밋 5개로 나눈다.

리뷰 지적 사항도 함께 반영한다.
- 궁수: 조준 유지 동작의 호흡이 거의 안 보임, 강사격의 유지 시간이 짧음, 연속 사격 3종의 발사 자세가 비슷함
- 마법사: 마법진 펼치기의 마무리
- 유물사: 방벽 동작의 손 움직임

### Task 5: 갤러리 갱신

- 갤러리 Artifact에 다음을 추가한다.
  - 직업 탭마다 "전/후" 토글
  - "근접 구성표" 탭: 다른 모드 동작의 렌더 미리보기와 출처 표시
- `docs/design/motion-gallery.md`도 갱신한다.
