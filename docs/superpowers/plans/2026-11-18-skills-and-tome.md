# 스킬 체계·여신의 서·밸런스형 완성 (계획 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 스킬 체계와 마력, 여신의 서 책 UI(능력치 / 스킬 마인드맵 / 지식 / 여신의 말씀)를 만들고, 그 위에 밸런스형 전위의 스킬 세트와 평타 구성을 얹어 밸런스형을 게임에서 처음부터 끝까지 플레이할 수 있게 한다.

**Architecture:**
- **스킬 정의는 데이터 파일이다.** `data/goddessrpg/skills/<id>.json`에 두고, 순수 규칙(`rules/SkillRules`)이 배울 수 있는지, 쿨타임, 비용을 판정한다.
- **서버가 판정하고 클라이언트는 키 입력과 UI만 맡는다.** 스킬 효과는 정해진 "효과 종류" 몇 가지를 Java로 구현하고, 데이터가 그 종류를 골라 수치를 준다.
- **평타 구성도 데이터다.** `data/goddessrpg/movesets/<id>.json`에서 Epic Fight 동작 ID를 읽어 무기 종류를 만든다. 따라서 다른 모드 동작과 우리 동작을 데이터만 바꿔 교체할 수 있다.

**Tech Stack:** NeoForge 21.1.266, Java 21, Epic Fight 21.17.3.1 (EF-Extra·WOM 동작 ID 참조), JUnit 5, GameTest, Python 도구는 기존 그대로.

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md` (5.4 스킬, 5.5 다른 직업군, 5.7 마력, 5.8 여신의 서, 5.2 밸런스형)

**범위:**
- 이 계획에서 하는 것: 밸런스형까지만 완성한다.
- 다음 계획(3b)으로 미루는 것: 3D 무기·아이콘·여신 모델(GeckoLib)
- 계획 4로 미루는 것:
  - 탱킹형·인파이트·궁수·마법사 등 나머지 유형
  - 마법서와 륜
  - 보스 스킬
  - 다른 모드 무기 재연결("2번 작업")

## Global Constraints

- Minecraft 1.21.1, NeoForge 21.1.266, Java 21, 모드 ID `goddessrpg`, 패키지 `dev.goddessrpg`. 플레이어에게 보이는 글은 번역 키(ko_kr 기본, en_us 보조)로 쓴다.
- **스킬 얻는 법** (스펙 5.4)
  - 숙련도 달성 → 기본 스킬(자동 습득)
  - 스킬북 → 중급
  - 보스 → 상위
  - 고서 → 지식(이미 구현됨)
  - **자기 직업군 스킬만 배운다.** 하위 유형 전용 가지는 그 하위 유형만 배운다.
- **슬롯과 키** (스펙 5.4)
  - 액티브 스킬은 **슬롯 4칸**에 끼운다. 패시브는 배우면 자동 적용된다. 회피·가드는 Epic Fight 키 그대로다.
  - 슬롯 키 기본값은 `Z`, `X`, `C`, `V`다. 바닐라의 툴바 저장/불러오기(C/X)와 겹치지만 거의 쓰지 않는 키라 이렇게 정했다(빌더 결정). 키 설정에서 바꿀 수 있다.
  - 여신의 서를 여는 단축키 기본값은 `J`다.
  - Epic Fight 기본 키(Alt 회피, Space 이동, R 모드, G 고정, K 스킬창, Y 이모트)와 겹치지 않아야 한다.
- **Epic Fight 무기 고유 스킬은 플레이어가 쓸 수 없다** (스펙 4.2 C안: 회피·가드만 남긴다).
- **전투 중 신경 쓸 수치는 체력, 마력, Epic Fight 스태미나다.** 전위 스킬은 스태미나와 쿨타임을 쓰고 마력은 0이다.
- **마력** (스펙 5.7: 직업군마다 총량이 크게 다르다). 값은 `data/goddessrpg/tuning/mana.json`에 둔다(빌더 재량).
  - 전위: 최대 30, 초당 회복 0.5
  - 후위 딜러와 후위 서포터: 최대 100, 초당 회복 2
  - 자기 직업군 두 번째 분야(전위: 방어술, 후위 딜러: 원소 마법, 후위 서포터: 유물술) 레벨 1당 최대 +2
- **여신의 서** (스펙 5.8): 책 아이템을 우클릭하거나 단축키로 연다. 페이지를 넘기는 구조이고 페이지는 1 능력치, 2 스킬 마인드맵, 3 지식, 4 여신의 말씀이다.
  - 마인드맵은 배운 스킬을 밝게, 못 배운 스킬을 흐리게, 미발견 스킬을 `???`로 보인다.
  - 마우스를 올리면 얻는 법 힌트가 뜨고, 클릭하면 슬롯에 장착한다. 선행 제한은 없다.
- **밸런스형 평타는 EF-Extra `sword_auto1~3`, 대시 `sword_dash`, 공중 `sword_airslash`다** (`docs/design/vanguard-moveset.md`, 사용자 결정 A: 근접은 전문가 동작 우선). 우리 `balanced_auto1~3`은 구성 파일만 바꾸면 쓸 수 있는 대안으로 남긴다.
- 스킬 수치는 초안이다. 사용자 플레이 테스트로 조정하고, 모든 수치는 데이터 파일에 둔다.

## Review Focus

1. **쿨타임 중이거나 스태미나가 부족한데 키를 연타하는 경우:** 발동되지 않는다. 서버가 판정하고, 짧은 거절 표시만 뜬다. (Task 4 `castRejectedOnCooldownOrStamina`)
2. **조작된 클라이언트가 배우지 않은 스킬이나 다른 직업 스킬을 시전 요청하는 경우:** 무시한다. (Task 4 `castRejectsUnlearnedOrForeign`)
3. **슬롯에 끼운 스킬이 재질의(직업 변경)로 무효가 되는 경우:** 재질의할 때 배운 스킬과 슬롯을 비운다. 지식은 유지한다. (Task 2 `rerollClearsSkills`)
4. **죽거나 재접속한 뒤:** 배운 스킬, 슬롯, 쿨타임이 유지된다. 마력은 가득 찬 상태로 돌아온다. (Task 2 `skillsSurviveDeath`, Task 3 `manaRefillsOnRespawn`)
5. **여신의 서를 처음 연 플레이어(아무것도 배우지 않음):** 마인드맵에 직업군 중심과 분야만 보이고 스킬은 흐리거나 `???`로 보인다. 크래시가 나면 안 된다. (Task 7 `MindMapLayoutTest.emptyPlayer`)

---

## File Structure

```
mod/src/main/java/dev/goddessrpg/
  rules/SkillDef.java, SkillRules.java, ManaRules.java        순수 규칙 (JUnit)
  skill/SkillRegistry.java          skills/*.json 리로드
  skill/SkillService.java           배우기, 슬롯, 시전 판정, 쿨타임 (서버)
  skill/SkillEffects.java           효과 종류 구현 (animation_strike, counter_window, buff, knockback_stun, passive_attribute)
  skill/SkillPayloads.java          시전 요청, 슬롯 변경, 쿨타임/마력 동기화
  skill/client/SkillKeys.java       키 매핑 Z X C V, J
  skill/client/SkillHud.java        슬롯·쿨타임·마력 HUD
  mana/ManaAttachment.java          마력 상태 (copyOnDeath 아님, 리스폰 시 가득)
  item/SkillBookItem.java           스킬북 (데이터 컴포넌트 skill_id)
  compat/epicfight/EfInnatePolicy.java  EF 무기 고유 스킬 차단
  compat/epicfight/MovesetLoader.java   movesets/*.json → 무기 종류
  tome/client/TomeScreen.java       책 UI (페이지 넘김)
  tome/client/pages/*.java          StatsPage, MindMapPage, KnowledgePage, GoddessPage
  tome/client/MindMapLayout.java    노드 배치 계산 (순수, JUnit)
mod/src/main/resources/data/goddessrpg/
  skills/*.json, movesets/balanced.json, tuning/mana.json
docs/design/skills-balanced.md      밸런스형 스킬 표 (사용자 검토용)
```

---

### Task 1: 스킬 정의와 순수 규칙

**Files:** `rules/SkillDef.java`, `rules/SkillRules.java`, `rules/ManaRules.java`, `skill/SkillRegistry.java`, Test: `mod/src/test/java/dev/goddessrpg/rules/SkillRulesTest.java`, `ManaRulesTest.java`

**Interfaces:**
- Produces:
  - `record SkillDef(String id, RpgClass rpgClass, Optional<String> subtype, Field field, Kind kind, Unlock unlock, double cooldownSec, double staminaCost, double manaCost, Optional<String> animation, Effect effect, MapPos map)`
    - `enum Kind { ACTIVE, PASSIVE }`
    - `Unlock`는 sealed다:
      - `Proficiency(Field field, int level)`: JSON `{"type":"proficiency","field":"sword","level":10}`
      - `Book()`: `{"type":"book"}`
      - `Boss(String boss)`: `{"type":"boss","boss":"..."}`
    - `Effect`: `{"type": "<효과 종류>", ...수치}`. 종류별 키는 Task 4가 정의하고, 여기서는 `Map<String, Double> params`와 `String type`으로 보관한다.
    - `MapPos(int x, int y)`: 마인드맵 좌표. 직업군 중심이 (0,0)이다.
    - `Codec<SkillDef> CODEC`가 있고, `id`는 파일 이름에서 얻는다.
  - `SkillRules`:
    - `static boolean canLearn(SkillDef s, RpgPlayerData d)`: 직업군이 같아야 한다. `subtype`이 있으면 하위 유형도 같아야 한다. `inquiryDone`이어야 한다.
    - `static List<SkillDef> newlyUnlockedByProficiency(Collection<SkillDef> all, RpgPlayerData before, RpgPlayerData after)`: 숙련도 조건을 이번 변화로 처음 충족한 스킬 중 `canLearn`이고 아직 배우지 않은 것
    - `static double cooldownRemaining(SkillDef s, long lastCastTick, long nowTick)`: 초 단위, 0 이상
  - `ManaRules`:
    - `record ManaTuning(Map<RpgClass,Double> baseMax, Map<RpgClass,Double> regenPerSec, double perSecondFieldLevel)`와 `CODEC`, `DEFAULT`(Global Constraints 수치)
    - `static double max(ManaTuning t, RpgPlayerData d)`
  - `SkillRegistry`: 리로드 리스너다. `static Map<String,SkillDef> all()`, `static Optional<SkillDef> get(String id)`. 잘못된 파일은 로그를 남기고 건너뛴다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `canLearn`
    - 밸런스형 전위는 `rpgClass=VANGUARD, subtype 없음` 스킬을 배울 수 있다.
    - `subtype="tank"` 스킬은 배울 수 없다.
    - 후위 딜러는 전위 스킬을 배울 수 없다.
    - 질의 전 데이터(EMPTY)는 아무것도 배울 수 없다.
  - `newlyUnlockedByProficiency`
    - 검술 9→10이 되면 `Proficiency(SWORD,10)` 스킬이 나온다.
    - 10→11에서는 나오지 않는다.
    - 이미 배운 스킬은 제외된다.
  - `cooldownRemaining(s(cd 6), 100, 160)`은 `3.0`이다. 20틱이 1초다.
  - `max(DEFAULT, 전위, 방어술 레벨 5)`는 `30 + 5×2 = 40`이다.
  - 코덱으로 세 가지 Unlock 타입을 모두 읽고 쓸 수 있다.
- [ ] **Step 2:** `./gradlew test`를 실행해 실패하는지 확인한다.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** 테스트를 다시 실행해 통과를 확인한다.
- [ ] **Step 5:** 커밋한다. `feat(mod): skill definitions and pure skill/mana rules`

### Task 2: 배운 스킬·슬롯 저장, 자동 습득, 스킬북, 명령어

**Files:**
- Modify: `player/RpgPlayerData.java`. 필드 `Set<String> learnedSkills`, `List<Optional<String>> slots`(크기 4)를 추가한다. 코덱은 기본값이 있어 이전 저장과 호환된다.
- Modify: `growth/GrowthEvents.java`. 레벨업 직후 자동 습득을 연결한다.
- Modify: `inquiry/InquiryService.reroll`. 스킬과 슬롯을 비운다.
- Create: `skill/SkillService.java`(배우기·슬롯 부분), `item/SkillBookItem.java`, lang
- Modify: `command/RpgCommand.java`
- Test: `gametest/.../SkillLearnGameTest.java`

**Interfaces:**
- Consumes: Task 1 전부, `RpgAttachments.get/set`
- Produces:
  - `RpgPlayerData.withLearned(String id)`
  - `RpgPlayerData.withSlot(int index, Optional<String> id)`: index는 0..3. 배우지 않았거나 패시브인 스킬이면 `IllegalArgumentException`. 같은 스킬이 다른 슬롯에 있으면 그 슬롯을 비운다.
  - `RpgPlayerData.resetKeepKnowledge()`: 스킬과 슬롯도 비운다.
  - `SkillService.learn(ServerPlayer, String id) -> LearnResult` (`OK`, `ALREADY`, `NOT_ALLOWED`, `UNKNOWN`). 배우면 한국어 알림을 띄운다. 첫 액티브는 빈 슬롯에 자동으로 끼운다.
  - `SkillService.setSlot(ServerPlayer, int index, Optional<String> id) -> boolean`
  - `SkillBookItem`
    - 데이터 컴포넌트는 `goddessrpg:skill_id`다.
    - 쓰면 `learn`을 호출하고 `OK`일 때만 소모한다.
    - 툴팁에 스킬 이름을 보여 주고, 직업군이 다르면 "당신의 길이 아닙니다"를 보여 준다.
  - 명령어(OP 2)
    - `/goddessrpg skill list [player]`
    - `/goddessrpg skill learn <id> [player]`
    - `/goddessrpg skill slot <1-4> <id|none>`
    - `/goddessrpg level <field> <level> [player]`: 숙련도 레벨을 바로 설정한다(0~최대 레벨, 경험치 0). 레벨이 오르는 경우 자동 습득도 일어나야 한다(테스트 `levelCommandTriggersAutoLearn`). 사용자 요청(2026-11-18).

- [ ] **Step 1: 실패하는 GameTest 작성**
  - `autoLearnOnProficiency`: 밸런스형 플레이어의 검술을 `Proficiency(SWORD,10)` 직전으로 두고 경험치를 줘서 10이 되게 한다. 그러면 그 스킬이 `learnedSkills`에 들어가고, 액티브라면 빈 슬롯 0에 들어간다.
  - `skillBookLearnsAndConsumes`: 같은 직업군 스킬북은 사용 후 배워지고 소모된다. 다른 직업군 스킬북은 배워지지 않고 남는다.
  - `slotRules`: 배우지 않은 스킬을 슬롯에 넣으면 `false`다. 같은 스킬을 슬롯 2에 넣으면 기존 슬롯 0이 비워진다.
  - `rerollClearsSkills`: 재질의하면 `learnedSkills`가 비고 슬롯 4칸이 모두 비며 지식은 유지된다.
  - `skillsSurviveDeath`: `restoreFrom` 뒤에도 배운 스킬과 슬롯이 같다.
  - 코덱 왕복 테스트를 새 필드까지 포함해 확장한다.
- [ ] **Step 2:** `./gradlew runGameTestServer`를 실행해 실패하는지 확인한다.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew runGameTestServer`를 다시 실행해 통과를 확인한다.
- [ ] **Step 5:** 커밋한다. `feat(mod): learned skills, slots, auto-learn, skill books`

### Task 3: 마력

**Files:** `mana/ManaAttachment.java`, `data/goddessrpg/tuning/mana.json`, 동기화 페이로드(`skill/SkillPayloads.java`의 일부), Test: `gametest/.../ManaGameTest.java`

**Interfaces:**
- Produces:
  - `ManaAttachment.MANA`: `AttachmentType<Double>`, copyOnDeath가 아니다.
  - `static double get(Player)`
  - `static boolean tryConsume(ServerPlayer, double amount)`
  - `static void tick(ServerPlayer)`: 서버 틱마다 회복한다. 동기화는 값이 0.5 이상 변했거나 1초가 지났을 때만 보낸다.
  - 최대치는 `ManaRules.max`이고, 질의 전에는 0이다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `manaRegens`: 전위는 마력 0에서 40틱 뒤 `1.0`이다(회복 0.5/초).
  - `manaCappedAtMax`: 회복이 최대치를 넘지 않는다.
  - `tryConsume`: 마력이 부족하면 `false`이고 값이 그대로다.
  - `manaRefillsOnRespawn`: 리스폰하면 최대치로 돌아온다.
  - `mana.json`을 읽은 값이 `ManaTuning.DEFAULT`와 같다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현하고, 통과를 확인한다.
- [ ] **Step 5:** 커밋한다. `feat(mod): mana pool with class-based max and regen`

### Task 4: 시전 — 키, 서버 판정, 효과 종류, 쿨타임, HUD

**Files:** `skill/client/SkillKeys.java`, `skill/SkillPayloads.java`, `skill/SkillService.java`(시전 부분), `skill/SkillEffects.java`, `skill/client/SkillHud.java`, `skill/client/HudLayout.java`(순수), lang. Test: `gametest/.../SkillCastGameTest.java`, `mod/src/test/java/dev/goddessrpg/skill/HudLayoutTest.java`

**Interfaces:**
- Consumes: Task 1~3. EF 스태미나 API(플레이어 패치에서 스태미나를 읽고 쓰는 메서드, javap로 확인한다). `AnimIndex`와 `RpgAnimations` 등록 결과, 또는 `epicfight:`/`epicfightx:` 동작 ID를 EF `AnimationManager`로 조회한 결과.
- Produces:
  - `CastRequestPayload(int slot)`: 클라이언트 → 서버
  - `CooldownSyncPayload(Map<String, Long> readyAtTick)`: 서버 → 본인
  - `SkillService.cast(ServerPlayer, int slot) -> CastResult`
    - 결과값: `OK`, `EMPTY_SLOT`, `NOT_LEARNED`, `WRONG_CLASS`, `COOLDOWN`, `NO_STAMINA`, `NO_MANA`, `BUSY`
    - `BUSY`는 다른 공격 동작이 재생 중인 경우다.
  - 쿨타임은 서버에 `Map<String, Long> readyAtTick`으로 둔다. 플레이어 데이터에 포함하지 않는 별도 attachment이고 copyOnDeath다.
  - `SkillEffects.apply(ServerPlayer, SkillDef)`: 효과 종류는 다음과 같다. `params`의 키 이름이 고정이다.
    - `animation_strike`: `damage_mult`. 스킬의 `animation`을 EF로 재생하고, 그 동작의 타격 구간 동안 공격력에 `damage_mult` 배수를 임시로 적용한다. 동작이 끝나면 제거한다.
    - `knockback_stun`: `knockback`, `stun_sec`, `damage_mult`. `animation_strike`와 같고, 맞은 대상에게 넉백과 구속(Slowness 4)을 준다.
    - `counter_window`: `window_sec`, `damage_mult`. 창 시간 안에 피해를 받으면 그 피해를 무효로 하고 반격 동작(`animation`)과 배수를 적용한다. 창이 끝날 때까지 안 맞으면 아무 일도 없다.
    - `buff`: `duration_sec`, `damage_taken_mult`, `move_speed_mult`. 속성 수정자를 시간 동안 적용한다.
    - `passive_attribute`: `attack_damage_pct`, `armor_flat`, `requires`. `requires`는 `"sword"` 또는 `"shield_offhand"`다. 배운 상태이고 조건을 만족하면 상시 적용하고, 조건이 깨지면 제거한다.
  - 키
    - `SkillKeys`: 카테고리 `key.categories.goddessrpg`, 슬롯 1~4는 `Z X C V`, 여신의 서는 `J`다.
    - EF의 전투 모드가 아닐 때(바닐라 모드)도 동작한다.
    - 화면이 열려 있으면 무시한다.
  - `SkillHud`
    - 화면 하단 핫바 오른쪽에 슬롯 4칸을 그린다. 키 글자, 스킬 아이콘(임시로 이름 첫 글자), 쿨타임 오버레이를 표시한다.
    - 그 위에 마력 막대를 그린다.
    - 배치는 순수 함수 `HudLayout.slots(int screenW, int screenH)`로 계산한다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `castRejectsUnlearnedOrForeign`: 슬롯에 직접 넣은 다른 직업 스킬 ID(데이터를 강제로 조작)는 `WRONG_CLASS`이고, 배우지 않은 스킬은 `NOT_LEARNED`다. 효과가 없어야 한다.
  - `castRejectedOnCooldownOrStamina`: 한 번 시전하면 `OK`이고, 바로 다시 시전하면 `COOLDOWN`이다. 스태미나를 0으로 두면 `NO_STAMINA`다.
  - `buffAppliesAndExpires`: `buff`의 `damage_taken_mult 0.7`, `duration 4`일 때 피해 10을 받으면 7이 들어오고, 81틱 뒤에는 10이 들어온다.
  - `counterWindowNegatesHit`: 창 안에서 받은 피해는 0이다.
  - `passiveAppliesOnlyWithCondition`: 검을 들면 공격력이 오르고, 맨손이면 원래대로다.
  - `HudLayoutTest`: 320×240과 1920×1080에서 슬롯 4칸이 화면 안에 있고 핫바(가운데 182px)와 겹치지 않는다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현하고, 통과를 확인한다. 효과 수치 구현은 EF 피해 이벤트와 속성 API를 쓴다. 쓴 이름은 클래스 주석에 기록한다.
- [ ] **Step 5:** 커밋한다. `feat(mod): skill casting with keys, server validation, effects, cooldown HUD`

### Task 5: Epic Fight 무기 고유 스킬 차단

**Files:** `compat/epicfight/EfInnatePolicy.java`, Test: `gametest/.../EfInnateGameTest.java`

**Interfaces:**
- Produces: `EfInnatePolicy.register()`. 플레이어의 EF 무기 고유 스킬(WEAPON_INNATE 슬롯) 발동을 막는다.
  - EF의 스킬 실행 이벤트나 훅에서 해당 슬롯 카테고리를 취소하는 방식을 javap로 찾는다.
  - 방법이 없으면 장착 시 WEAPON_INNATE 슬롯을 비운다.
  - 시도한 방법은 주석으로 남긴다.

- [ ] **Step 1: 실패하는 GameTest 작성.** `innateSkillBlocked`: 철검을 든 플레이어가 EF 고유 스킬 실행을 시도해도 실행되지 않는다. 슬롯이 비었거나 실행 결과가 거절이어야 한다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현하고, 통과를 확인한다. 회피·가드에는 영향이 없어야 하므로 기존 `EfSkillGameTest`도 통과해야 한다.
- [ ] **Step 5:** 커밋한다. `feat(mod): block Epic Fight weapon innate skills for players`

### Task 6: 밸런스형 평타 구성과 스킬 세트

**Files:**
- Create: `compat/epicfight/MovesetLoader.java`, `data/goddessrpg/movesets/balanced.json`, `data/goddessrpg/skills/balanced_*.json`(아래 6개), lang, `docs/design/skills-balanced.md`
- Modify: `compat/epicfight/RpgWeaponTypes.java`. 하드코딩한 콤보를 구성 파일로 바꾼다.
- Test: `gametest/.../BalancedKitGameTest.java`

**Interfaces:**
- Consumes: Task 1~5, `RpgAnimations`, `AnimIndex`
- Produces:
  - 구성 파일 형식: `{"weapon_type":"goddessrpg:balanced_sword","base":"epicfight:sword","combo":[ID...],"dash":ID,"air":ID}`
    - ID는 `epicfight:`, `epicfightx:`, `wom:`, `goddessrpg:` 경로다.
    - 없는 ID는 로그 오류를 남기고 기본 EF 검 콤보로 대체한다.
  - 구성 파일은 클래스 로딩 시점에 읽는다. 무기 종류 등록 이벤트가 리소스 매니저보다 먼저 오기 때문이며, `AnimIndex`와 같은 방식이다.
  - `balanced.json`
    - combo: `epicfightx:biped/combat/sword_auto1`, `epicfightx:biped/combat/sword_auto2`, `epicfightx:biped/combat/sword_auto3`
    - dash: `epicfightx:biped/combat/sword_dash`
    - air: `epicfightx:biped/combat/sword_airslash`
  - 스킬 6개(수치는 초안이고 `docs/design/skills-balanced.md`에 표로 정리한다):

    | id | 종류 | 얻는 법 | 동작 | 효과 |
    |---|---|---|---|---|
    | `balanced_shield_bash` | 액티브 | 방어술 5 | `goddessrpg:biped/combat/balanced_shield_bash` | `knockback_stun` (넉백 1.2, 기절 1초, 0.6배). 쿨 6초, 스태미나 3 |
    | `balanced_dash_slash` | 액티브 | 검술 10 | `epicfightx:biped/combat/sword_dash` | `animation_strike` (1.3배). 쿨 8초, 스태미나 4 |
    | `balanced_counter` | 액티브, 밸런스형 전용 | 검술 15 | `goddessrpg:biped/combat/balanced_counter` | `counter_window` (0.6초, 1.6배). 쿨 10초, 스태미나 3 |
    | `balanced_steadfast` | 액티브, 밸런스형 전용 | 스킬북 | 없음 | `buff` (4초, 받는 피해 0.7배, 이동 0.85배). 쿨 20초, 스태미나 2 |
    | `vanguard_sword_mastery` | 패시브 | 검술 20 | — | `passive_attribute` (공격력 +10%, 조건 sword) |
    | `vanguard_shield_mastery` | 패시브 | 방어술 20 | — | `passive_attribute` (방어 +2, 조건 shield_offhand) |

    - 마인드맵 좌표: 검술 가지 쪽은 오른쪽, 방어술 가지 쪽은 왼쪽, 하위 유형 전용 가지는 아래다.

- [ ] **Step 1: 실패하는 GameTest 작성**
  - `balancedMovesetUsesEfxCombo`: 수련용 검의 EF 능력 콤보 첫 동작이 `epicfightx:biped/combat/sword_auto1` accessor다. 이 테스트로 EF-Extra ID가 게임 안에서 실제로 풀리는지도 확인한다.
  - `badMovesetIdFallsBack`: 테스트 전용 구성에 없는 ID가 있으면 등록은 성공하고 EF 기본 검 콤보가 들어간다.
  - `balancedSkillsLoad`: 6개 스킬이 모두 `SkillRegistry`에 있고, 각 `animation` ID가 등록된 동작으로 풀린다.
  - `shieldBashKnocksBack`: 시전 후 앞의 좀비가 밀려나고 구속 효과가 있다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현하고, 통과를 확인한다.
- [ ] **Step 5:** `docs/design/skills-balanced.md`를 작성한다. 한국어 표와 "사용자 검토 필요"를 넣는다. 그다음 커밋한다. `feat(mod): data-driven movesets and the balanced vanguard skill kit`

### Task 7: 여신의 서 책 UI

**Files:** `tome/client/TomeScreen.java`, `tome/client/pages/{StatsPage,MindMapPage,KnowledgePage,GoddessPage}.java`, `tome/client/MindMapLayout.java`, `item/GoddessTomeItem.java`(열기 경로 변경), `skill/client/SkillKeys.java`(J 키 연결), 책 배경 텍스처 `assets/goddessrpg/textures/gui/tome.png`(Pillow로 생성: 펼친 책 두 쪽, 금색 테두리), lang. 페이지 4에 쓸 여신 평가 문구 번역 키(하위 유형별 1~2문장)도 추가한다. Test: `mod/src/test/java/dev/goddessrpg/tome/MindMapLayoutTest.java`, `TomePagingTest.java`

**Interfaces:**
- Consumes: 클라이언트 동기화된 `RpgPlayerData`, 마력, 쿨타임, `SkillRegistry`. 클라이언트도 스킬 정의를 알아야 하므로 로그인과 리로드 때 서버가 스킬 정의 목록을 보낸다. `SkillPayloads`에 `SkillDefsPayload`를 추가한다.
- Produces:
  - `TomeScreen`
    - 펼친 책 한 장에 두 쪽이 보인다.
    - ← → 버튼, 키보드 방향키, 마우스 휠로 넘긴다.
    - 페이지는 4개이고 각 페이지가 두 쪽을 쓴다.
    - 마지막으로 본 페이지를 기억한다(클라이언트 메모리).
    - `StatsScreen`은 삭제하고 그 내용을 1페이지로 옮긴다.
  - `SlotSetPayload(int slot, Optional<String> skillId)`: 클라이언트 → 서버. 서버는 `SkillService.setSlot`으로 검증한다(`SkillPayloads`에 추가).
  - `MindMapLayout.layout(List<SkillDef> skills, RpgPlayerData d, int w, int h) -> List<Node>`
    - `record Node(String id, int x, int y, State state, Kind kind)`
    - `enum State { LEARNED, KNOWN_LOCKED, UNDISCOVERED }`
    - 중심 노드는 직업군이고, 그다음 분야 2개, 그다음 스킬이다.
    - `UNDISCOVERED`인 스킬: 스킬북 스킬 중 책을 본 적 없는 것, 보스 스킬. 이름은 `???`로 보인다.
    - 숙련도 스킬은 항상 `KNOWN_LOCKED` 이상이다.
  - 마인드맵 상호작용
    - 마우스를 올리면 얻는 법 힌트가 뜬다. 예: "검술 10 달성", "스킬북: 던전 상자", "보스 처치".
    - 배운 액티브를 클릭하면 슬롯 선택 팝업(1~4)이 뜨고, 서버에 `SlotSetPayload`를 보낸다.
    - 아래에 현재 슬롯 4칸을 보여 준다.
  - 3페이지(지식): 읽은 고서 목록과 오른 배율을 보여 준다.
  - 4페이지(여신의 말씀): 질의 때 고른 답 문장과 하위 유형별 여신 평가 문구를 보여 준다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `MindMapLayoutTest.emptyPlayer`: 아무것도 배우지 않은 밸런스형이면 노드가 3개(직업군, 분야 2개)와 스킬이 보인다. 숙련도 스킬은 `KNOWN_LOCKED`, 스킬북 스킬은 `UNDISCOVERED`다.
  - `MindMapLayoutTest.noOverlap`: 6개 스킬을 320×240과 1920×1080에 배치하면 노드끼리 겹치지 않고 모두 책 쪽 영역 안에 있다.
  - `learnedShowsLearned`: 배운 스킬은 `LEARNED`다.
  - `TomePagingTest`: 페이지 0에서 ←를 누르면 0, 3에서 →를 누르면 3이다. 끝에서 넘어가지 않는다.
- [ ] **Step 2~4:** 실패를 확인하고, 구현하고, 통과를 확인한다. 화면 렌더링 자체는 수동 확인 대상이라 Task 8 체크리스트에 넣는다.
- [ ] **Step 5:** 커밋한다. `feat(mod): Goddess Tome book UI with stats, skill mind map, knowledge, goddess pages`

### Task 8: 배포 0.2.0과 사용자 테스트 안내

**Files:** `mod/version.txt`, `pack/mods/goddessrpg.pw.toml`, `docs/admin/modpack.md`, `docs/admin/mod-sources.md`

- [ ] **Step 1:** 다음을 모두 통과시킨다. `./gradlew test runGameTestServer build`, `pytest tools/anim`, `pytest tools prism`, `build_anims --check`.
- [ ] **Step 2:** `mod/version.txt`를 `0.2.0`으로 올리고 푸시한다. 릴리스 워크플로가 성공했는지 확인한다.
- [ ] **Step 3:** 팩 업데이트, `validate_pack`, `smoke_server`를 실행한다. 로그에 goddessrpg 오류가 0이어야 한다.
- [ ] **Step 4:** 사용자 체크리스트(한국어)를 작성한다.
  - 여신의 서를 J나 우클릭으로 연다. 4페이지를 넘겨 본다.
  - 마인드맵에서 스킬 상태를 확인한다.
  - `/goddessrpg xp sword 2000`, `/goddessrpg xp defense 2000`으로 자동 습득과 알림을 확인한다.
  - 슬롯 장착 → Z X C V 시전 → 쿨타임과 HUD를 확인한다.
  - 방패 밀치기로 좀비를 밀어낸다.
  - 반격 자세 중에 맞으면 반격이 나간다.
  - `/give`로 스킬북(`balanced_steadfast`)을 얻어 배운다.
  - 수련용 검 평타가 EF-Extra 3연격으로 나오는지 본다.
  - Epic Fight 무기 고유 스킬이 막혔는지 본다.
  - 다른 직업으로 재질의하면 스킬이 초기화되는지 본다.
- [ ] **Step 5:** 커밋하고 푸시한다. `feat(pack): goddessrpg 0.2.0 (skills, tome, balanced kit)`
