# 캐릭터 만들기로 교체 (계획 3c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 여신 질의를 없애고 첫 접속 때 "캐릭터 만들기"를 넣는다. 무기를 고르면 하위 유형이 정해지고, 재능·적성 별 12개를 4칸에 직접 나눠 넣는다. 여신의 서는 모험가의 서로 이름을 바꾸고, 게임 안 재선택 아이템 "재각성의 결정"을 추가한다.

**Architecture:**
- 서버 쪽 질의 흐름(`inquiry/`)을 캐릭터 만들기 흐름(`creation/`)으로 바꾼다. 클라이언트는 화면 하나에서 두 단계(무기 카드 → 별 분배)를 보여 주고, 확정 요청 한 번을 보낸다.
- 서버는 받은 값을 전부 다시 검증한다.
- 결과 저장은 기존 `RpgPlayerData.withResult(RpgClass, String, StarResult, List<Integer>, GrowthTuning)`을 그대로 쓴다. 별 분배를 `StarResult`로 만들어 넘긴다.

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md` 5.1, 5.8 (2026-11-22 개정)

## Global Constraints

- **무기 → 하위 유형 매핑은 스펙 5.1 표 그대로다.**
  - 마궁(`spellbow`)은 목록에서 뺀다.
  - `available:false`인 하위 유형은 "준비 중"으로 보이고 고를 수 없다. 지금은 `balanced`만 고를 수 있다.
- **분배 규칙:** 4칸(첫 분야 재능, 첫 분야 적성, 둘째 분야 재능, 둘째 분야 적성)이고, 각 칸은 ★1~★5다. 합은 정확히 **12**여야 확정된다. 값은 `data/goddessrpg/tuning/allocation.json` `{"budget":12,"min":1,"max":5}`에 둔다.
- 결과는 영구적이다. 다시 고르는 방법은 두 가지뿐이다.
  - 관리자 아이템: 기존 `goddess_reroll`을 유지한다. 크리에이티브나 OP 2가 필요하다.
  - **재각성의 결정** `goddessrpg:reawakening_crystal`: 쓰면 캐릭터 만들기를 다시 연다. 숙련도, 배운 스킬, 슬롯, 쿨타임을 초기화하고 지식과 고서는 유지한다. 소모품이다.
    - 제작법(초안, 사용자 검토): 네더의 별 1, 메아리 조각 4, 다이아몬드 블록 4. 모양은 가운데 네더의 별, 모서리 다이아몬드 블록, 변 메아리 조각이다.
- **아이템 ID는 저장 호환을 위해 유지한다.** `goddessrpg:goddess_tome`과 `goddess_reroll`의 ID는 그대로이고 표시 이름만 바꾼다(모험가의 서, 관리자용 재선택의 깃).
- 여신 관련 문구(질문, 답, 여신 평가, 여신 인사)는 lang과 데이터에서 삭제한다. 모험가의 서 4페이지는 "나의 선택"이고, 고른 무기·하위 유형·별 분배를 보여 준다.
- 플레이어에게 보이는 글은 번역 키(ko_kr 기본, en_us)로 쓴다. 무기 카드 설명은 짧은 한국어 2문장이다(사용자 검토).

## Review Focus

1. **조작된 확정 요청**(합이 12가 아님, 범위 밖 별, 준비 중이거나 존재하지 않는 하위 유형, 이미 만든 캐릭터): 거절하고 데이터는 바뀌지 않는다. (Task 2 `forgedCreationRejected`)
2. **만들기 화면에서 나가거나 접속을 끊은 경우:** 다음 접속 때 다시 열린다. 반쯤 만든 상태는 저장하지 않는다. (Task 2 `creationReopensAfterRelog`)
3. **만들기 중 죽거나 맞는 경우:** 무적이고, 부활하면 화면이 다시 열린다. 기존 질의 동작을 유지한다. (Task 2 `creationInvulnerableAndResent`)
4. **이전 버전(0.2.x) 세이브에서 질의를 이미 끝낸 플레이어:** 그대로 플레이할 수 있다. `inquiryAnswers`가 남아 있어도 문제가 없어야 하고, "나의 선택" 페이지는 기록이 없다고 보여 준다. (Task 2 `legacySaveStillPlayable`)
5. **재각성의 결정을 쓴 뒤:** 숙련도, 스킬, 슬롯, 쿨타임이 초기화되고 지식은 남는다. 결정은 1개 소모되고, 만들기를 마치면 새 시작 레벨이 적용된다. (Task 2 `reawakeningResetsAndConsumes`)

---

### Task 1: 데이터와 순수 규칙

**Files:**
- Modify: `data/goddessrpg/subtypes/*.json`
  - `"weapon":"<무기 키>"`, `"icon":"<아이템 id>"`, `"order":<int>`를 추가한다.
  - 무기 키: `sword_shield`, `shield_mace`, `greatsword_dual`, `longbow`, `staff`, `summon_stone`, `holy_symbol`, `relic`
  - `spellbow.json`에는 `"hidden":true`를 넣는다.
- Create:
  - `data/goddessrpg/tuning/allocation.json`
  - `rules/AllocationRules.java`
  - `rules/CreationChoice.java`
- Modify: `rules/InquiryData.Subtype`. 새 필드는 선택이고 기본값을 둔다.
- Test: `mod/src/test/java/dev/goddessrpg/rules/AllocationRulesTest.java`, `SubtypeFilesTest.java`

**Interfaces:**
- Produces:
  - `record Allocation(int talentFirst, int aptitudeFirst, int talentSecond, int aptitudeSecond)` (`rules/CreationChoice.java` 안)
  - `record CreationChoice(String subtype, Allocation allocation)`. 두 레코드 모두 `Codec`과 `StreamCodec`을 둔다.
  - `record AllocationTuning(int budget, int min, int max)`와 `CODEC`, `DEFAULT = (12,1,5)`
  - `AllocationRules.validate(AllocationTuning t, Allocation a) -> Optional<String>`
    - 오류가 있으면 번역 키를 돌려준다: `goddessrpg.create.error.sum`, `goddessrpg.create.error.range`
    - 문제가 없으면 empty다.
  - `AllocationRules.toStars(RpgClass c, Allocation a) -> StarResult`. `c.first()`/`c.second()`에 맞춰 talent/aptitude 맵을 만든다.
  - `Subtype`에 `weapon`, `icon`, `order`, `hidden` 필드를 추가한다(코덱 기본값 포함).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `validate`
    - (3,3,3,3)은 empty다.
    - (5,5,1,1)은 empty다.
    - (5,5,5,5)는 `...sum`이다.
    - (6,2,2,2)는 `...range`다.
    - (0,4,4,4)는 `...range`다.
  - `toStars(VANGUARD,(4,2,3,3))`의 결과는 talent{sword:4, defense:3}, aptitude{sword:2, defense:3}이다.
  - `SubtypeFilesTest`
    - 9개 파일 모두 `weapon`, `icon`, `order`가 있다.
    - `spellbow`만 `hidden`이다.
    - 숨기지 않은 8개의 `weapon` 값이 서로 다르다.
    - `allocation.json`은 `DEFAULT`와 같다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현한 뒤, 통과를 확인한다. 명령: `cd mod && ./gradlew test`
- [ ] **Step 5:** 커밋한다. 메시지: `feat(mod): creation choice data, allocation rules, subtype weapon metadata`

### Task 2: 서버 흐름 — 캐릭터 만들기, 재각성의 결정

**Files:**
- Create:
  - `creation/CreationService.java`: `InquiryService`를 대체하고, 같은 수명주기 훅(로그인, 로그아웃, 부활, 차원 이동, 무적)을 옮긴다.
  - `creation/CreationPayloads.java`
  - `item/ReawakeningCrystalItem.java`
  - 레시피 `data/goddessrpg/recipe/reawakening_crystal.json`
- Delete:
  - `inquiry/InquiryService.java`, `inquiry/InquiryPayloads.java`, `rules/InquiryScorer.java`와 테스트
  - `data/goddessrpg/inquiry/**`
  - 질문, 답, 여신 문구 lang 키
  - 클라이언트 질의 화면(Task 3에서 교체)
- Modify:
  - `item/RerollItem.java`: `CreationService.reopen`을 쓴다.
  - `command/RpgCommand.java`: `reset`이 `CreationService.reopen`을 쓴다.
  - `RpgDataLoader`: inquiry 로딩을 제거하고 allocation을 로딩한다.
  - `RpgPlayerData`: `inquiryAnswers`는 코덱 호환을 위해 남기되 쓰지 않는다. 새 필드 `Optional<CreationChoice> creation`을 추가한다(코덱 기본값 없음).
  - `SkillPayloads`, `TomeRef`: 질문 키 전송을 빼고 choice를 쓴다.
- Test: `gametest/.../CreationGameTest.java`. `InquiryGameTest`는 삭제하고, 필요한 경우만 이식한다.

**Interfaces:**
- Consumes: Task 1, `RpgPlayerData.withResult`, `SkillService.grantMetProficiencySkills`, `SkillCooldowns`의 초기화, `ManaAttachment.refill`
- Produces:
  - `OpenCreationPayload(List<SubtypeCard> cards, AllocationTuning tuning)`: 서버 → 클라이언트
    - `SubtypeCard(String subtype, RpgClass rpgClass, String weapon, String icon, boolean available)`
    - 숨긴 하위 유형은 빼고 `order` 순으로 보낸다.
  - `SubmitCreationPayload(CreationChoice choice)`: 클라이언트 → 서버
  - `CreationService.submit(ServerPlayer, CreationChoice) -> boolean`
    - 다음 경우는 거절한다: 이미 만듦, 존재하지 않거나 숨겨졌거나 준비 중인 하위 유형, `validate` 실패
    - 통과하면 `withResult(class, subtype, toStars(...), List.of(), tuning)`로 저장하고 `creation`을 기록한다.
    - 그다음 모험가의 서를 지급하고(이미 있으면 생략), `grantMetProficiencySkills`를 호출하고, 마력을 채운다.
  - `CreationService.reopen(ServerPlayer, boolean resetProgress)`
    - 관리자 아이템과 `/goddessrpg reset`은 `true`를 쓴다.
    - 재각성의 결정도 `true`다. 지식을 남기는 기존 `resetKeepKnowledge`에 쿨타임 초기화를 더한다.
  - `ReawakeningCrystalItem`
    - 캐릭터를 만든 플레이어만 쓸 수 있다.
    - 쓰기 전에 확인을 받는다: 한 번 우클릭하면 액션바에 "한 번 더 사용하면 초기화됩니다"를 띄우고, 5초 안에 다시 쓰면 실행한다.
    - 1개를 소모한다.

- [ ] **Step 1: 실패하는 GameTest 작성**
  - `creationGivesBalancedAndTome`: `(balanced,(4,2,3,3))`로 확정하면 다음이 맞다.
    - 클래스 VANGUARD, 하위 유형 balanced
    - 검술 시작 레벨이 `talentStartLevel[4]`와 같다
    - 모험가의 서 1개
  - `forgedCreationRejected`: 다음은 모두 `false`이고 데이터는 `EMPTY` 그대로다.
    - 합이 13인 분배
    - ★6
    - `tank`(준비 중)
    - `spellbow`(숨김)
    - `nope`(존재하지 않음)
    - 확정한 뒤 다시 보낸 요청
  - `creationReopensAfterRelog`: 확정 전에 다시 로그인하면 열린 상태 그대로이고 데이터는 `EMPTY`다.
  - `creationInvulnerableAndResent`: 만들기 중에는 피해가 취소되고, 부활하거나 차원을 옮기면 화면을 다시 보낸다.
  - `legacySaveStillPlayable`: `inquiryDone=true`, `creation` 없음, `inquiryAnswers=[0,1,2]`인 데이터로 로그인하면 화면이 열리지 않고 스킬 시전 경로도 정상이다.
  - `reawakeningResetsAndConsumes`: 검술 20, 스킬 3개, 쿨타임, 지식 0.1인 상태에서 결정을 두 번 쓴다. 그러면 결정 1개가 줄고, 숙련도·스킬·슬롯·쿨타임이 비고, 지식은 0.1이고, 만들기 화면이 열린다.
  - `rerollNeedsPermission`: 기존 테스트를 이식한다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현한 뒤, 통과를 확인한다. 명령: `./gradlew test runGameTestServer`. 로그에 "All N required tests passed"가 있는지 확인한다.
- [ ] **Step 5:** 커밋한다. 메시지: `feat(mod): character creation replaces the goddess inquiry; reawakening crystal`

### Task 3: 클라이언트 화면과 이름 변경

**Files:**
- Create:
  - `creation/client/CreationScreen.java`
  - `creation/client/CreationLayout.java`: 순수 함수이고 JUnit으로 시험한다.
- Delete: `inquiry/client/*`
- Modify:
  - `tome/client/pages/GoddessPage.java` → `ChoicePage.java`("나의 선택")
  - `TomeScreen`
  - lang: 아이템 이름을 "모험가의 서"와 "관리자용 재선택의 깃"으로 바꾸고, 무기 카드 이름과 설명, 분배 화면 문구, 오류 문구를 넣는다.
  - 결정 아이템 모델과 16×16 아이콘(Pillow로 생성)
- Test: `mod/src/test/java/dev/goddessrpg/creation/CreationLayoutTest.java`

**Interfaces:**
- Consumes: Task 2의 `OpenCreationPayload`와 `SubmitCreationPayload`
- Produces:
  - `CreationScreen`. 두 단계로 구성하고, ESC로 닫을 수 없다(기존 규칙).
    - 1단계: 무기 카드 8장을 격자로 보여 준다.
      - 카드마다 아이콘, 무기 이름, 하위 유형 이름, 설명 2줄이 있다.
      - 준비 중인 카드는 흐리게 보이고 "준비 중"이라고 표시하며 고를 수 없다.
      - 카드를 고르면 다음으로 넘어간다.
    - 2단계: 그 직업군의 두 분야 이름으로 4칸을 보여 준다.
      - 칸마다 ★ 5개, −/+ 버튼, 남은 별 수가 있다.
      - 재능은 "시작 레벨", 적성은 "성장 속도"라는 한 줄 설명을 붙인다.
      - 각 칸이 ★ 몇 개면 시작 레벨이 몇인지, 성장 배율이 얼마인지 미리 보여 준다. 숫자로 보여 주는데, 포인트 분배를 고른 이상 숫자 공개가 맞다고 본 빌더 결정이다.
      - "뒤로"와 "확정" 버튼이 있다. 확정은 합이 12일 때만 누를 수 있다.
    - 확정하면 `SubmitCreationPayload`를 보낸다. 서버가 거절하면 오류 문구를 보여 준다.
  - `CreationLayout.cards(int w, int h, int n) -> List<Rect>`, `CreationLayout.allocation(int w, int h) -> List<Rect>`
  - `ChoicePage`: 기록된 `creation`을 표시한다(무기, 하위 유형, 별 4칸). 기록이 없으면 "기록 없음 (이전 버전에서 만든 캐릭터)"이라고 표시한다.

- [ ] **Step 1: 실패하는 테스트 작성.** `CreationLayoutTest`: 320×240과 1920×1080에서 카드 8장과 분배 칸 4개가 화면 안에 있고 서로 겹치지 않는다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현한 뒤, 통과를 확인한다. 데디케이티드 서버 안전성도 확인한다. 클라이언트 클래스는 핸들러 람다나 `isClientSide` 안에서만 참조해야 한다.
- [ ] **Step 5:** 커밋한다. 메시지: `feat(mod): character creation screen; rename to Adventurer's Tome`

### Task 4: 배포 0.3.0과 안내

- [ ] **Step 1:** 모든 검사를 실행한다: `./gradlew test runGameTestServer build`, `pytest tools/anim`, `pytest tools prism`, `build_anims --check`.
- [ ] **Step 2:** `mod/version.txt`를 `0.3.0`으로 올리고 푸시한다. 릴리스가 성공하는지 확인한다.
- [ ] **Step 3:** 팩을 업데이트한다. sha256을 검증하고 `validate_pack`과 `smoke_server`를 실행한다. 시험용 zip을 다시 만든다.
- [ ] **Step 4:** `docs/admin/modpack.md`의 "0.2.x 시험" 절을 "0.3.x 시험"으로 바꾸고 다음 내용으로 고친다.
  - 질의 답 경로를 지우고 "한손검+방패 카드 선택 → 별 분배 예시 (검술 재능 ★4, 검술 적성 ★2, 방어술 재능 ★3, 방어술 적성 ★3)"로 바꾼다.
  - 자동 습득 시험에 맞는 분배를 적는다. 방어술 재능 ★1이면 시작 레벨이 0이다.
  - 재각성의 결정 제작법과 사용법(두 번 우클릭)을 적는다.
  - 모험가의 서 이름과 4페이지 "나의 선택"을 반영한다.
  - 문서 전반의 여신 관련 표현을 정리한다.
- [ ] **Step 5:** 커밋하고 푸시한다. 메시지: `feat(pack): goddessrpg 0.3.0 (character creation)`
