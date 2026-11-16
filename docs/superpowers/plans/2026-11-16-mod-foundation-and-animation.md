# 우리 모드 기반 + 전투 동작 도구 (계획 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 우리 RPG 모드(`goddessrpg`)의 뼈대를 만든다. 범위는 플레이어 데이터, 여신의 질의 3단계, 숙련도·재능·적성·지식, Epic Fight 스킬 정리, 직접 만든 Epic Fight 전투 동작(Python 도구 → 모드 등록 → 시험용 무기)이다. 결과 jar를 GitHub Releases로 배포하고 모드팩에 넣어 사용자가 게임에서 시험한다.

**Architecture:**
- **모드 (`mod/`):** NeoForge ModDevGradle 프로젝트.
  - 게임과 무관한 규칙(점수 계산, 성장 곡선)은 `rules` 패키지의 순수 Java로 두고 JUnit으로 시험한다.
  - 게임에 붙는 부분(데이터 저장, 이벤트, 화면, 패킷)은 GameTest로 시험한다.
- **Epic Fight:** jar를 개발 의존으로 연결한다. jar는 `tools/fetch_jars.py`가 `.cache/jars/`에 받는다.
- **전투 동작:**
  - `tools/anim/`의 Python 도구가 키프레임 원본(`mod/anim/*.json`)을 Epic Fight 동작 JSON(관절별 4×4 행렬)으로 바꾼다.
  - 미리보기 GIF를 그려 사용자 검토를 받는다.
  - Blender는 쓰지 않는다. 이 환경에서 받을 수 없고, 포맷이 단순해 직접 만들 수 있다.

**Tech Stack:**
- 모드: Java 21, Gradle 8.14.3, ModDevGradle `2.0.148`, NeoForge `21.1.266`, Epic Fight `21.17.3.1-mc1.21.1-neoforge`, JUnit 5, NeoForge GameTest.
- 동작 도구: Python 3.13 + numpy + matplotlib + pillow + pytest.
- 배포: GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md`

**이 계획의 범위:** 설계 9장 2단계의 앞부분.
- 원래 "계획 2"를 둘로 나눈다.
  - **계획 2(이 문서):** 뼈대와 동작 도구
  - **계획 3:** 여신의 서 책 UI, 마인드맵, 스킬과 슬롯 4칸, 밸런스형 스킬·3D 무기·여신 모델
- 이후 계획 번호는 하나씩 밀린다. 나머지 8개 유형은 계획 4, 보스 재조정·번역 본작업 등은 계획 5다.
- 이미 넣은 다른 모드 무기를 새 동작으로 다시 연결하는 작업(사용자 요청 "2번")은 계획 3이 끝난 뒤에 한다.

## Global Constraints

- Minecraft `1.21.1`, NeoForge `21.1.266`, Java 21. 모드 ID `goddessrpg`, 패키지 `dev.goddessrpg`.
- 의존: Epic Fight `21.17.3.1-mc1.21.1-neoforge` (필수). GeckoLib·Curios는 이 계획에서 쓰지 않는다 (계획 3 이후).
- 질문·선택지·결과 매핑, 하위 유형 목록, 수치 표는 **데이터 파일(JSON, 데이터팩 형식)**. 코드 수정 없이 고칠 수 있어야 한다.
- 질의 결과는 영구적이다. 바꾸는 방법은 관리자 전용 아이템 하나뿐이다 (크리에이티브 또는 OP 권한 2 이상).
- Epic Fight 스킬은 **회피·가드만** 쓴다. 스킬북 사용은 막는다. 무기 고유 스킬은 계획 3에서 우리 스킬로 바꾼다 (이 계획에서는 그대로 둠).
- 재능·적성은 **별(★) 1~5**로 표시하고 숫자를 보여 주지 않는다.
- 기본 언어 `ko_kr`, 보조 `en_us`. 플레이어에게 보이는 글은 전부 번역 키로 둔다.
- 모드 jar와 의존 jar는 저장소에 커밋하지 않는다 (`.gitignore`의 `*.jar`). Gradle wrapper jar만 예외로 허용한다 (`!mod/gradle/wrapper/gradle-wrapper.jar`).
- 그림·모델·동작 파일은 코드와 분리한다 (`mod/anim/`, `assets/`).
- 수치(빌더 재량, 플레이 테스트 전제). `data/goddessrpg/tuning/growth.json`에 둔다.
  - 별 → 시작 숙련도 `{1:0, 2:2, 3:4, 4:6, 5:8}`
  - 별 → 적성 배율 `{1:0.6, 2:0.8, 3:1.0, 4:1.25, 5:1.5}`
  - 고서 1권 지식 `+0.05`, 지식 합계 상한 `+0.50`. 같은 고서는 한 번만 읽힌다.
  - 실제 배율 = 적성 배율 + 지식
  - 다음 레벨까지 경험치 = `round(40 × (L+1)^1.6)`, 최대 레벨 50
  - 공격 경험치: 적대·중립 생물(플레이어·갑옷 거치대 제외)에게 피해를 주면 1회당 `3`
  - 방어 경험치: 1회당 `min(10, 2 + 막은 피해/2)`
  - 다른 직업군 분야도 경험치가 오르지만 **배율 `0.3` 고정**(적성·지식 미적용, 시작 레벨 0). 자기 직업군 두 분야는 위 적성 배율. (2026-11-16 사용자 결정: 잡캐 가능, 대신 늦게)
  - 스킬은 여전히 자기 직업군 것만 배운다 (설계 5.4·5.5 유지).

## Review Focus

1. **질의 도중 접속을 끊거나 창을 닫는 경우:** 다음 접속 때 질의가 처음부터 다시 열린다. 반쯤 정해진 결과가 저장되면 안 된다. (Task 5 `inquiryReopensAfterRelog`)
2. **클라이언트가 조작한 답 패킷(없는 선택지 번호, 단계 건너뛰기, 질의가 끝난 뒤 재전송):** 서버가 무시하고 결과가 바뀌지 않는다. (Task 5 `forgedAnswersIgnored`)
3. **죽거나 차원을 이동한 뒤:** 직업·숙련도·지식이 그대로 남는다. (Task 3 `dataSurvivesDeath`)
4. **아직 구현하지 않은 하위 유형으로 점수가 몰리는 답:** 구현된 유형(밸런스형)으로 결과가 나온다. 크래시하거나 빈 직업이 되면 안 된다. (Task 2 `unavailableSubtypesExcluded`)
5. **Epic Fight 스킬북을 쓰거나 다른 모드가 회피 스킬을 바꾼 경우:** 회피·가드 외 스킬이 생기지 않는다. 회피·가드는 접속할 때마다 다시 채워진다. (Task 6 `skillbookBlocked`, `dodgeRestoredOnLogin`)

---

## File Structure

```
mod/
  settings.gradle, build.gradle, gradle.properties, gradlew, gradle/wrapper/
  src/main/java/dev/goddessrpg/
    GoddessRpg.java                  모드 진입점, 등록
    rules/                           게임과 무관한 순수 규칙 (JUnit)
      Field.java, RpgClass.java      분야 6개, 직업군 3개
      Stars.java                     별 1~5, 표 조회
      GrowthTuning.java              growth.json 값 (record)
      ProficiencyRules.java          레벨 곡선, 경험치 계산
      InquiryData.java               질문·하위 유형 데이터 (record)
      InquiryScorer.java             답 → 직업군/하위 유형/별
    player/
      RpgPlayerData.java             플레이어 데이터 (record + Codec)
      RpgAttachments.java            NeoForge Data Attachment 등록
      RpgSync.java                   서버→본인 동기화 패킷
    data/                            JSON 데이터 리로드 리스너
    inquiry/                         질의 흐름 (서버 상태, 패킷, 화면)
    growth/                          경험치 이벤트, 고서 아이템
    item/                            여신의 서, 재질의 아이템, 시험용 검
    compat/epicfight/                EF 스킬 정리, 동작·무기 종류 등록
    command/RpgCommand.java          /goddessrpg info|xp|reset (OP)
  src/main/resources/
    META-INF/neoforge.mods.toml
    assets/goddessrpg/lang/ko_kr.json, en_us.json
    assets/goddessrpg/animmodels/animations/biped/combat/*.json  (도구가 생성)
    data/goddessrpg/inquiry/..., subtypes/..., tuning/growth.json, capabilities/weapons/...
  src/test/java/dev/goddessrpg/rules/   JUnit
  src/gametest/java/dev/goddessrpg/     GameTest (별도 소스셋)
  anim/                               동작 키프레임 원본 (사람이 고치는 파일)
tools/anim/
  efanim.py                           EF armature·동작 읽기/쓰기, 자세→행렬
  build_anims.py                      mod/anim/*.json → assets 동작 JSON
  preview.py                          스키닝 렌더 → PNG/GIF
  tests/test_efanim.py, test_preview.py
.github/workflows/mod-release.yml
```

---

### Task 1: Gradle 프로젝트, Epic Fight 연결, 빌드·테스트 실행

**Files:**
- Create: `mod/settings.gradle`, `mod/build.gradle`, `mod/gradle.properties`, gradle wrapper, `mod/src/main/java/dev/goddessrpg/GoddessRpg.java`, `mod/src/main/resources/META-INF/neoforge.mods.toml`, `mod/src/test/java/dev/goddessrpg/SmokeTest.java`, `mod/src/gametest/java/dev/goddessrpg/BootGameTest.java`
- Modify: `.gitignore`, `docs/admin/modpack.md` (빌드 방법 절 추가)

**Interfaces:**
- Produces:
  - `GoddessRpg.MODID = "goddessrpg"`
  - Gradle 작업 `test`(JUnit), `runGameTestServer`(GameTest), `build`(jar)
  - EF jar 경로 규칙 `.cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar`

- [ ] **Step 1:** `python3 tools/fetch_jars.py pack .cache/jars`를 실행한다. `.cache/jars/`에 EF jar가 있으면 성공이다.
- [ ] **Step 2:** `cd mod && gradle wrapper --gradle-version 8.14.3`로 wrapper를 만든다. `.gitignore`에 `!mod/gradle/wrapper/gradle-wrapper.jar`를 추가한다.
- [ ] **Step 3:** `build.gradle`을 작성한다.
  - 플러그인 `net.neoforged.moddev` `2.0.148`, `neoForge.version = "21.1.266"`
  - `compileOnly`와 `localRuntime`에 `files("../.cache/jars/epic-fight-21.17.3.1-mc1.21.1-neoforge.jar")`
  - 소스셋 `gametest`(main 클래스패스 포함)
  - 실행 구성 `gameTestServer`: 시스템 속성 `neoforge.enabledGameTestNamespaces=goddessrpg`
  - JUnit 5 의존
- [ ] **Step 4:** 테스트를 작성한다.
  - `SmokeTest.modIdIsStable()`: `assertEquals("goddessrpg", GoddessRpg.MODID)`
  - `BootGameTest.epicFightLoaded(GameTestHelper h)`: `ModList.get().isLoaded("epicfight")`가 참이어야 한다.
  - `neoforge.mods.toml`: EF를 `type="required"`, `versionRange="[21.17,)"`, `side="BOTH"`로 둔다.
- [ ] **Step 5:** `cd mod && ./gradlew test runGameTestServer build`를 실행한다. 세 작업 모두 성공하고 GameTest 로그에 `All 1 required tests passed`가 나오며 `build/libs/goddessrpg-0.1.0.jar`가 생기면 통과다.
- [ ] **Step 6:** `docs/admin/modpack.md`에 "우리 모드 빌드" 절을 추가한다(위 두 명령). 그다음 커밋한다: `feat(mod): goddessrpg gradle skeleton with Epic Fight dev dependency`

---

### Task 2: 순수 규칙: 분야·별·성장 곡선·질의 점수

**Files:**
- Create: `mod/src/main/java/dev/goddessrpg/rules/{Field,RpgClass,Stars,GrowthTuning,ProficiencyRules,InquiryData,InquiryScorer}.java`
- Test: `mod/src/test/java/dev/goddessrpg/rules/{ProficiencyRulesTest,InquiryScorerTest}.java`

**Interfaces:**
- Produces:
  - `enum Field { SWORD, DEFENSE, ARCHERY, ELEMENTAL, SUMMONING, RELIC }`
  - `enum RpgClass { VANGUARD(SWORD, DEFENSE), REAR_DEALER(ARCHERY, ELEMENTAL), REAR_SUPPORTER(SUMMONING, RELIC); Field first(); Field second(); boolean owns(Field) }`
  - `record GrowthTuning(Map<Integer,Integer> talentStartLevel, Map<Integer,Double> aptitudeMultiplier, double knowledgePerTome, double knowledgeCap, double xpBase, double xpExponent, int maxLevel, double hitXp, double guardXpBase, double guardXpCap, double offClassMultiplier)` with `static GrowthTuning DEFAULT` (Global Constraints 값) and `Codec<GrowthTuning> CODEC`
  - `ProficiencyRules`:
    - `static int xpToNext(GrowthTuning t, int level)`
    - `static double multiplier(GrowthTuning t, int aptitudeStars, double knowledge)`
    - `static double fieldMultiplier(GrowthTuning t, RpgClass c, Field f, int aptitudeStars, double knowledge)`: 자기 직업군 분야면 `multiplier(...)`, 아니면 `t.offClassMultiplier()`
    - `static double guardXp(GrowthTuning t, float blocked)`
    - `static LevelProgress addXp(GrowthTuning t, int level, double xp, double gained)` (`record LevelProgress(int level, double xp)`, 최대 레벨에서 경험치 0 고정)
  - `InquiryData`:
    - `record Answer(String textKey, Map<String,Integer> scores, Map<Field,Integer> talent, Map<Field,Integer> aptitude)`
    - `record Question(String id, String textKey, List<Answer> answers)`
    - `record Subtype(String id, RpgClass rpgClass, boolean available)`
    - 각각 `Codec` 포함
  - `InquiryScorer`:
    - `static RpgClass pickClass(List<Question> stage1, List<Integer> picks, List<Subtype> subtypes)`
    - `static Subtype pickSubtype(List<Question> stage2, List<Integer> picks, List<Subtype> subtypesOfClass)`
    - `static StarResult stars(List<Question> stage3, List<Integer> picks, RpgClass c)` (`record StarResult(Map<Field,Integer> talent, Map<Field,Integer> aptitude)`)
    - 규칙: 점수 동점이면 데이터에 먼저 적힌 것을 고른다. 사용 가능한 하위 유형이 하나도 없는 직업군은 고르지 않는다. 별은 각 분야 `clamp(3 + 합, 1, 5)`이다.
    - 잘못된 입력(`picks` 길이 불일치, 범위 밖 번호)은 `IllegalArgumentException`을 던진다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `ProficiencyRulesTest`:
    - `xpToNext(DEFAULT,0)==40`, `xpToNext(DEFAULT,9)==round(40*10^1.6)==1592`
    - `multiplier(DEFAULT,3,0.0)==1.0`, `multiplier(DEFAULT,5,0.5)==2.0`, `multiplier(DEFAULT,1,0.9)==0.6+0.5` (지식 상한 적용)
    - `fieldMultiplier(DEFAULT, VANGUARD, SWORD, 5, 0.5)==2.0`, `fieldMultiplier(DEFAULT, VANGUARD, ARCHERY, 5, 0.5)==0.3`
    - `guardXp(DEFAULT,4f)==4.0`, `guardXp(DEFAULT,100f)==10.0`
    - `addXp(DEFAULT,0,30,15)` → `(1, 5.0)`. 한 번에 여러 레벨이 오르는 경우도 맞아야 한다.
    - `addXp(DEFAULT,50,0,999)` → `(50, 0.0)`
  - `InquiryScorerTest`:
    - `pickClass`: 점수가 가장 높은 직업군이 나온다.
    - `tiePicksFirstDeclared`
    - `unavailableSubtypesExcluded`: 후위 딜러 점수가 가장 높아도 그 직업군에 사용 가능한 하위 유형이 없으면 VANGUARD가 나온다.
    - `stars`: 합 `+5`는 별 5로, 합 `-7`은 별 1로 잘린다.
    - `stars`는 그 직업군의 두 분야 키만 돌려준다.
    - `badPicksRejected`: 범위 밖 번호를 넣으면 `assertThrows(IllegalArgumentException)`
- [ ] **Step 2:** `./gradlew test`를 실행한다. 클래스가 없어서 컴파일 실패하면 정상이다.
- [ ] **Step 3:** 위 시그니처대로 구현한다. `GrowthTuning.DEFAULT`의 값은 Global Constraints의 수치 그대로다.
- [ ] **Step 4:** `./gradlew test`를 실행해 전부 통과하는지 확인한다.
- [ ] **Step 5:** 커밋한다: `feat(mod): pure growth and inquiry scoring rules`

---

### Task 3: 플레이어 데이터 저장·동기화·명령어

**Files:**
- Create: `player/RpgPlayerData.java`, `player/RpgAttachments.java`, `player/RpgSync.java`, `command/RpgCommand.java`, `data/RpgDataLoader.java` (growth.json, subtypes, inquiry 리로드)
- Create: `mod/src/main/resources/data/goddessrpg/tuning/growth.json`, `data/goddessrpg/subtypes/{balanced,tank,infight,archer,elementalist,spellbow,summoner,relicist,healer}.json`
- Test: `mod/src/gametest/java/dev/goddessrpg/PlayerDataGameTest.java`

**Interfaces:**
- Consumes: Task 2의 `Field`, `RpgClass`, `GrowthTuning`, `InquiryData.Subtype`
- Produces:
  - `record RpgPlayerData(boolean inquiryDone, Optional<RpgClass> rpgClass, Optional<String> subtype, Map<Field,Integer> talent, Map<Field,Integer> aptitude, Map<Field,Integer> level, Map<Field,Double> xp, Set<String> tomesRead, double knowledge, List<Integer> inquiryAnswers)`
    - `static RpgPlayerData EMPTY`
    - `Codec`, `StreamCodec`
    - 불변. 바꾸는 메서드는 새 인스턴스를 돌려준다: `withResult(...)`, `withXp(Field, LevelProgress)`, `withTome(String id, double gain)`, `resetKeepKnowledge()`
  - `RpgAttachments.DATA`: `AttachmentType<RpgPlayerData>`, `copyOnDeath()`
  - `static RpgPlayerData RpgAttachments.get(Player)`
  - `static void RpgAttachments.set(ServerPlayer, RpgPlayerData)`: 저장하고 본인에게 동기화한다.
  - `RpgDataLoader`:
    - `static GrowthTuning tuning()`
    - `static List<Subtype> subtypes()`
    - `static List<Question> stage1()`
    - `static List<Question> stage2(RpgClass)`
    - `static List<Question> stage3(RpgClass)`
  - 명령어(OP 2): `/goddessrpg info [player]`, `/goddessrpg xp <field> <amount>`, `/goddessrpg reset [player]`

- [ ] **Step 1:** 하위 유형 JSON 9개를 작성한다. 형식은 `{"class":"vanguard","available":true}`이다. 이 계획에서는 `balanced`만 `true`로 둔다.
- [ ] **Step 2:** `growth.json`을 작성한다. 내용은 `GrowthTuning.DEFAULT`와 같은 값이다.
- [ ] **Step 3: 실패하는 GameTest 작성**
  - `newPlayerIsEmpty`: 가짜 플레이어(`helper.makeMockServerPlayerInLevel()`)의 데이터가 `EMPTY`와 같다.
  - `dataSurvivesDeath`: 결과를 설정한 뒤 `player.kill()`로 죽이고 리스폰 복사(`PlayerEvent.Clone`)를 거쳐도 `rpgClass`, `level`, `knowledge`가 같다.
  - `codecRoundTrip`: 모든 필드를 채운 데이터를 NBT로 썼다가 읽으면 같은 값이다.
  - `loaderReadsBalancedOnly`: `RpgDataLoader.subtypes()` 중 `available`인 것은 `balanced` 하나다.
- [ ] **Step 4:** `./gradlew runGameTestServer`를 실행한다. 실패하면 정상이다.
- [ ] **Step 5:** 구현한다.
  - 동기화는 `PayloadRegistrar.playToClient`로 하고, 로그인·리스폰·차원 이동·값 변경 때 보낸다.
  - 리로드 리스너는 `AddReloadListenerEvent`에 등록한다.
- [ ] **Step 6:** `./gradlew runGameTestServer`로 통과를 확인한다. 이어서 개발 서버(`./gradlew runServer`)에서 `/goddessrpg info`가 한국어로 출력되는지 로그로 확인한다.
- [ ] **Step 7:** 커밋한다: `feat(mod): player RPG data attachment, sync, data loaders, admin command`

---

### Task 4: 질의 데이터 초안(한국어)과 사용자 검토용 문서

**Files:**
- Create: `data/goddessrpg/inquiry/stage1.json`, `inquiry/stage2/vanguard.json`, `inquiry/stage3/vanguard.json`
- Modify: `assets/goddessrpg/lang/ko_kr.json`, `en_us.json`
- Create: `docs/design/goddess-inquiry.md` (질문·선택지·어디로 점수가 가는지 표, 사용자 검토용)
- Test: `mod/src/test/java/dev/goddessrpg/rules/InquiryDataFilesTest.java`

**Interfaces:**
- Consumes: Task 2 `InquiryData` 코덱
- Produces: 데이터 파일 경로 규칙 `inquiry/stage1.json`, `inquiry/stage2/<class>.json`, `inquiry/stage3/<class>.json`. 각 파일 형식은 `{"questions":[...]}`이다.

- [ ] **Step 1: 실패하는 테스트 작성**
  `InquiryDataFilesTest`는 리소스 JSON을 코덱으로 읽어 다음을 확인한다.
  - stage1 질문 3개, 각 선택지 3개. 모든 선택지의 `scores` 키는 세 직업군 ID 중 하나다.
  - stage2/vanguard 질문 3개. 점수 키는 `balanced|tank|infight`이다.
  - stage3/vanguard 질문 4개. `talent`·`aptitude` 키는 `sword|defense`만 쓴다.
  - 모든 `textKey`가 `ko_kr.json`과 `en_us.json` 양쪽에 있다.
- [ ] **Step 2:** `./gradlew test --tests '*InquiryDataFilesTest'`를 실행한다. 파일이 없어서 실패하면 정상이다.
- [ ] **Step 3:** 데이터 파일과 번역을 작성한다.
  - 여신의 말투는 존댓말이고, 선택지는 성향이 어느 정도 드러나게 쓴다. 숫자나 직업 이름은 직접 쓰지 않는다.
  - stage1 선택지의 점수는 세 직업군에 고르게 퍼지게 한다. 그래야 나중에 다른 유형이 열려도 그대로 쓸 수 있다.
- [ ] **Step 4:** `./gradlew test`로 통과를 확인한다.
- [ ] **Step 5:** `docs/design/goddess-inquiry.md`를 작성한다. 질문과 선택지마다 점수가 가는 곳을 표로 정리하고, 끝에 "사용자 검토 필요"를 표시한다. 그다음 커밋한다: `feat(mod): goddess inquiry question data (draft, Korean)`

---

### Task 5: 질의 흐름: 첫 접속 화면, 서버 검증, 결과, 여신의 서, 재질의 아이템

**Files:**
- Create: `inquiry/InquiryService.java` (서버), `inquiry/InquiryPayloads.java`, `inquiry/client/InquiryScreen.java`, `item/GoddessTomeItem.java`, `item/RerollItem.java`, `item/RpgItems.java`, `inquiry/client/StatsScreen.java` (임시 능력치 화면, 계획 3에서 책 UI로 교체)
- Modify: lang 파일, `assets/goddessrpg/models/item/*.json`, 임시 16×16 아이콘 2개 `assets/goddessrpg/textures/item/{goddess_tome,goddess_reroll}.png`
- Test: `mod/src/gametest/java/dev/goddessrpg/InquiryGameTest.java`

**Interfaces:**
- Consumes:
  - Task 2 `InquiryScorer`
  - Task 3 `RpgAttachments`, `RpgDataLoader`, `RpgPlayerData.withResult(RpgClass, String subtype, StarResult, List<Integer> answers, GrowthTuning)`. 시작 숙련도는 `talentStartLevel`로 정한다.
- Produces:
  - `InquiryService.onLogin(ServerPlayer)`: `inquiryDone`이 거짓이면 진행 상태를 초기화하고 1단계 질문을 보낸다.
  - `InquiryService.submit(ServerPlayer, int stage, List<Integer> picks) -> boolean`: 단계 순서, 선택지 수, 범위를 검증한다. 3단계가 끝나면 결과를 저장하고 여신의 서를 준다.
  - `InquiryService.reroll(ServerPlayer)`: 지식을 남기고 초기화한 뒤 질의를 다시 연다.
  - 진행 중인 답은 서버 메모리(`Map<UUID, Progress>`)에만 둔다. 저장은 3단계가 끝났을 때만 한다.
  - `RpgItems.GODDESS_TOME`, `RpgItems.REROLL`: REROLL은 사용 조건이 `player.hasPermissions(2) || isCreative()`이고, 크리에이티브 탭 `OP_BLOCKS`에만 보인다.

- [ ] **Step 1: 실패하는 GameTest 작성**
  - `fullInquiryGivesBalancedAndTome`: stage1·2·3 답을 차례로 `submit`하면 `rpgClass==VANGUARD`, `subtype=="balanced"`, `inquiryDone`이 참이 되고, 인벤토리에 `GODDESS_TOME` 1개가 생기고, 검술 시작 레벨이 `talentStartLevel[talent별]`과 같다.
  - `forgedAnswersIgnored`:
    - 2단계를 1단계보다 먼저 보내면 `false`를 돌려주고 데이터는 그대로다.
    - 선택지 번호 `99`를 보내면 `false`다.
    - 질의가 끝난 뒤 `submit`하면 `false`이고 결과가 바뀌지 않는다.
  - `inquiryReopensAfterRelog`: 1단계만 제출하고 `onLogin`을 다시 부르면 진행이 1단계로 돌아가고 `inquiryDone`은 거짓이다.
  - `rerollKeepsKnowledge`: 지식 `0.1`, 질의 완료 상태에서 `reroll`하면 `inquiryDone`이 거짓, `rpgClass`가 비고 `knowledge==0.1`이다.
  - `rerollNeedsPermission`: 일반 서바이벌 가짜 플레이어가 REROLL을 쓰면 아무 일도 없다.
- [ ] **Step 2:** `./gradlew runGameTestServer`를 실행한다. 실패하면 정상이다.
- [ ] **Step 3:** 구현한다.
  - 화면은 여신의 말(질문)과 선택지 버튼으로 구성한다. ESC로 닫을 수 없다(`shouldCloseOnEsc=false`).
  - 단계가 끝나면 선택들을 한 번에 보낸다.
  - 여신의 서를 우클릭하면 `StatsScreen`이 열린다. 직업군, 하위 유형, 분야별 레벨, 재능·적성 ★, 지식을 보여 준다.
- [ ] **Step 4:** `./gradlew runGameTestServer`로 통과를 확인한다.
- [ ] **Step 5:** 클라이언트 화면은 로그로 확인한다. `./gradlew runClient`는 화면이 없어서 실행할 수 없으니, 대신 `InquiryScreen`의 버튼 배치 계산을 함수로 분리한다: `static List<Rect> layout(int width, int height, int answerCount)`. JUnit `InquiryLayoutTest`로 다음을 확인한다: 버튼이 서로 겹치지 않고 화면 안에 있으며, 선택지가 2~4개, 화면이 `320×240`과 `1920×1080`인 경우를 모두 포함한다.
- [ ] **Step 6:** 커밋한다: `feat(mod): goddess inquiry flow, tome item, admin reroll`

---

### Task 6: Epic Fight 스킬 정리 (회피·가드만)

**Files:**
- Create: `compat/epicfight/EfSkillPolicy.java`
- Test: `mod/src/gametest/java/dev/goddessrpg/EfSkillGameTest.java`

**Interfaces:**
- Consumes: EF jar의 `yesman.epicfight.skill.SkillSlots`(`DODGE`, `GUARD`), 플레이어 패치·스킬 컨테이너 API
- Produces:
  - `EfSkillPolicy.ensureBaseSkills(ServerPlayer)`: DODGE 슬롯에 `epicfight:roll`, GUARD 슬롯에 `epicfight:guard`를 넣고 EF 동기화 패킷을 보낸다.
  - `EfSkillPolicy.isBlockedSkillbook(ItemStack)`

- [ ] **Step 1:** API 이름을 확인한다.
  - `javap -cp .cache/jars/epic-fight-*.jar`로 다음 셋을 찾는다:
    - `ServerPlayerPatch`/`PlayerPatch`의 스킬 컨테이너 조회
    - 슬롯에 스킬을 넣는 메서드
    - 스킬 등록 조회(`SkillManager` 등)
  - EF 스킬북 아이템 ID(`epicfight:skillbook`)도 `assets` 모델 목록으로 확인한다.
  - 찾은 이름은 `EfSkillPolicy`의 주석 한 줄로 남긴다.
- [ ] **Step 2: 실패하는 GameTest 작성**
  - `dodgeAndGuardGivenOnLogin`: 로그인 처리 뒤 DODGE 슬롯이 `epicfight:roll`, GUARD 슬롯이 `epicfight:guard`다.
  - `dodgeRestoredOnLogin`: DODGE 슬롯을 다른 스킬(`epicfight:step`)로 바꾼 뒤 다시 로그인하면 `epicfight:roll`로 돌아온다.
  - `skillbookBlocked`: EF 스킬북 아이템을 우클릭하는 이벤트(`PlayerInteractEvent.RightClickItem`)가 취소되고, 플레이어가 새 스킬을 얻지 않는다.
- [ ] **Step 3:** `./gradlew runGameTestServer`를 실행한다. 실패하면 정상이다.
- [ ] **Step 4:** `PlayerLoggedInEvent`와 `PlayerRespawnEvent`에서 `ensureBaseSkills`를 부르고, `RightClickItem`에서 스킬북을 막는다.
- [ ] **Step 5:** `./gradlew runGameTestServer`로 통과를 확인한다. 그다음 커밋한다: `feat(mod): restrict Epic Fight skills to dodge and guard`

---

### Task 7: 숙련도 경험치 획득과 고서(지식)

**Files:**
- Create: `growth/GrowthEvents.java`, `growth/WeaponFields.java`, `item/AncientTomeItem.java`, `data/goddessrpg/tomes/{first_steps,old_war_record}.json` (고서 2권 시험용, 형식 `{"knowledge":0.05,"title":"goddessrpg.tome.first_steps"}`)
- Test: `mod/src/gametest/java/dev/goddessrpg/GrowthGameTest.java`, `mod/src/test/java/dev/goddessrpg/rules/WeaponFieldsTest.java`

**Interfaces:**
- Consumes: Task 2 `ProficiencyRules`, Task 3 `RpgAttachments`, `RpgDataLoader.tuning()`
- Produces:
  - `WeaponFields.fieldFor(String efCategory) -> Optional<Field>`: EF 무기 분류 이름(소문자)을 분야로 바꾼다.
    - `sword`, `longsword`, `greatsword`, `tachi`, `uchigatana`, `dagger`, `axe`, `spear`, `trident`, `fist`, `hoe` → `SWORD`
    - `bow`, `crossbow` → `ARCHERY`
    - 그 외 → 비어 있음
  - `GrowthEvents.grant(ServerPlayer, Field, double baseXp)`: `fieldMultiplier`로 배율을 적용하고 (다른 직업군 분야는 `0.3`) 레벨을 올린 뒤 동기화한다. 레벨이 오르면 액션바에 한국어 알림을 띄운다.
  - `AncientTomeItem`: 데이터 컴포넌트 `goddessrpg:tome_id`. 읽으면 `withTome`을 적용하고, 이미 읽은 고서면 "이미 읽은 책입니다"를 띄우고 소모하지 않는다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `WeaponFieldsTest`: 위 표를 확인한다. `shield`→비어 있음, `bow`→ARCHERY.
  - `GrowthGameTest.hitGivesSwordXp`: 밸런스형 플레이어(적성 ★3)가 철검으로 좀비에게 피해를 주면 검술 경험치가 `3.0` 오른다.
  - `aptitudeScalesXp`: 적성 ★5이면 같은 상황에서 `4.5` 오른다.
  - `offClassFieldSlow`: 전위(적성 ★5)가 `grant(ARCHERY, 10)`을 받으면 궁술 경험치가 `3.0` 오른다.
  - `armorStandGivesNothing`: 갑옷 거치대를 때리면 경험치가 오르지 않는다.
  - `tomeReadOnce`: 같은 고서를 두 번 쓰면 지식은 `0.05`이고 두 번째 책은 인벤토리에 남는다.
  - `knowledgeCapped`: 서로 다른 고서 11권 분량을 더해도 지식은 `0.5`다. 고서 데이터는 테스트용 이름으로 만든다.
- [ ] **Step 2:** `./gradlew test runGameTestServer`를 실행한다. 실패하면 정상이다.
- [ ] **Step 3:** 구현한다.
  - **공격:** `LivingDamageEvent.Post`에서 공격자가 `ServerPlayer`이고, 직접 공격이고, 대상이 `Player`나 `ArmorStand`가 아니면 주 손 아이템의 EF 무기 분류로 분야를 찾는다. 분류는 EF 능력 조회에서 얻는다.
  - **방어:** EF 가드 성공 시점에 `guardXp`를 `DEFENSE`에 준다. EF의 가드 성공 훅 이름은 `javap`로 찾고(`GuardSkill`의 이벤트·리스너), 바닐라 방패 막기(`LivingShieldBlockEvent`)도 같이 처리한다.
- [ ] **Step 4:** `./gradlew test runGameTestServer`로 통과를 확인한다.
- [ ] **Step 5:** 커밋한다: `feat(mod): proficiency XP from weapon hits and guards, ancient tomes`

---

### Task 8: 동작 도구: EF 동작 읽기·쓰기·자세 계산

**Files:**
- Create: `tools/anim/efanim.py`, `tools/anim/__init__.py`, `tools/anim/tests/test_efanim.py`
- Modify: `tools/requirements-dev.txt` (`numpy`, `matplotlib`, `pillow` 추가)

**Interfaces:**
- Produces (Task 9·10이 사용):
  - `load_armature(path) -> Armature`
    - `Armature`: `joints: list[str]`, `parent: dict[str,str|None]`, `rest_local: dict[str, np.ndarray(4,4)]`
    - EF `biped.json`의 `armature.hierarchy`에서 읽는다. 행렬은 16개 값의 행 우선 배열이다.
  - `load_animation(path) -> Animation`
    - `Animation`: `tracks: dict[str, Track]`, `Track(times: list[float], local: list[np.ndarray(4,4)])`
  - `save_animation(anim, path)`: EF 형식 `{"animation":[{"name","time","transform"}]}`. 숫자는 소수점 6자리, 관절 순서는 armature 순서를 따른다.
  - `world_matrices(arm, local_by_joint) -> dict[str, np.ndarray]`: 부모부터 차례로 곱한다.
  - `pose_local(arm, base_local, rot_deg: dict[str,(x,y,z)], translate: dict[str,(x,y,z)]) -> dict[str, np.ndarray]`: base 행렬 뒤에 관절 로컬 축 기준 XYZ 오일러 회전을 곱하고, 이동은 로컬 위치에 더한다.

- [ ] **Step 1: 실패하는 테스트 작성** (EF jar에서 `biped.json`과 `biped/combat/sword_auto1.json`을 `zipfile`로 읽음. jar가 없으면 `pytest.skip`)
  - `test_armature_has_20_joints`: 이름 목록이 `Root, Thigh_R, Leg_R, Knee_R, Thigh_L, Leg_L, Knee_L, Torso, Chest, Head, Shoulder_R, Arm_R, Hand_R, Tool_R, Elbow_R, Shoulder_L, Arm_L, Hand_L, Tool_L, Elbow_L`이다.
  - `test_roundtrip_sword_auto1`: 읽고 쓰고 다시 읽은 모든 행렬이 원본과 `1e-6` 안에서 같다.
  - `test_rest_pose_head_above_root`: rest 자세의 world에서 Head의 높이 성분이 Root보다 크다(위쪽 축은 armature에서 확인).
  - `test_zero_rotation_is_identity`: `pose_local(arm, rest, {}, {}) == rest`
  - `test_arm_rotation_moves_tool`: `Arm_R`을 90° 돌리면 `Tool_R` world 위치가 `0.1` 이상 움직이고 `Head`는 그대로다.
- [ ] **Step 2:** `python3 -m pytest tools/anim -v`를 실행한다. 실패하면 정상이다.
- [ ] **Step 3:** 구현한다. numpy만 쓴다.
- [ ] **Step 4:** `python3 -m pytest tools/anim -v`로 통과를 확인한다.
- [ ] **Step 5:** 커밋한다: `feat(tools): Epic Fight animation read/write and pose math`

---

### Task 9: 동작 미리보기 렌더러 (사용자 검토용 GIF)

**Files:**
- Create: `tools/anim/preview.py`, `tools/anim/tests/test_preview.py`

**Interfaces:**
- Consumes: Task 8 `Armature`, `Animation`, `world_matrices`
- Produces:
  - `skin(arm, mesh, world_by_joint) -> np.ndarray(N,3)`: EF `biped.json`의 `vertices`(`positions`, `vcounts`, `vindices`의 (관절, 가중치) 쌍, `weights`, `parts`)로 선형 블렌드 스키닝을 한다.
  - `sample(anim, t) -> local_by_joint`: 키 사이를 보간한다. 회전은 쿼터니언 slerp, 위치는 선형이다.
  - `render_gif(arm, mesh, anim, out_path, fps=20, views=("side","front"))`
    - 프레임마다 두 시점을 나란히 그린다. 부위별로 색을 다르게 칠하고, 오른손 도구 위치(`Tool_R`)에 1블록 길이 막대를 그린다.
  - CLI: `python3 tools/anim/preview.py <anim.json|jar:path> <out.gif>`

- [ ] **Step 1: 실패하는 테스트 작성**
  - `test_rest_skin_matches_bind`: rest 자세 스키닝 결과가 원래 `positions`와 `1e-4` 안에서 같다.
  - `test_sample_endpoints`: `sample(anim, 0)`은 첫 키, `sample(anim, 마지막 시각)`은 마지막 키와 같다.
  - `test_gif_written`: `sword_auto1`로 GIF를 만들면 프레임 수가 `ceil(길이×20)+1`이고 이미지 폭이 높이보다 크다(두 시점).
- [ ] **Step 2:** `python3 -m pytest tools/anim -v`를 실행한다. 실패하면 정상이다.
- [ ] **Step 3:** matplotlib 3D 폴리곤(Agg 백엔드)과 pillow GIF로 구현한다.
- [ ] **Step 4:** `python3 -m pytest tools/anim -v`로 통과를 확인한다. 그다음 EF 원본 `sword_auto1`의 GIF를 만들어 눈으로 확인한다. 검을 휘두르는 모습으로 보여야 하며, 이것이 렌더러가 맞다는 기준이다.
- [ ] **Step 5:** 커밋한다: `feat(tools): animation preview GIF renderer`

---

### Task 10: 첫 자작 동작: 밸런스형 한손검 3연격 + 시험용 무기

**Files:**
- Create: `tools/anim/build_anims.py`, `tools/anim/tests/test_build_anims.py`
- Create: `mod/anim/balanced_auto1.json`, `balanced_auto2.json`, `balanced_auto3.json` (키프레임 원본)
- Create (생성물, 커밋함): `mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat/balanced_auto{1,2,3}.json`
- Create: `compat/epicfight/RpgAnimations.java`, `compat/epicfight/RpgWeaponTypes.java`, `item/TrainingSwordItem.java`, `data/goddessrpg/capabilities/weapons/training_sword.json`
- Modify: `tools/pack_rules.py` (`EF_WEAPON_TYPES`에 `goddessrpg:balanced_sword` 추가, 나중에 다른 무기 연결에 쓸 수 있게), lang 파일, `docs/admin/modpack.md` ("전투 동작 만들기" 절)
- Test: `mod/src/gametest/java/dev/goddessrpg/AnimationGameTest.java`

**Interfaces:**
- Consumes: Task 8 `pose_local`, `save_animation`; Task 9 `render_gif`; EF `AnimationManager.AnimationRegistryEvent.newBuilder(String, Consumer<AnimationBuilder>)`, `AnimationBuilder.nextAccessor(String, Function<AnimationAccessor<T>,T>)`, `ComboAttackAnimation(float convertTime, float antic, float contact, float recovery, Collider, Joint, AnimationAccessor, AssetAccessor<Armature>)`, `Armatures.BIPED`, `HumanoidArmature.toolR`, `WeaponCapabilityPresetRegistryEvent.getTypeEntry()`
- Produces:
  - 키프레임 원본 형식:
    ```json
    {"length": 0.6, "base": "epicfight:biped/living/hold_sword",
     "keys": [{"t": 0.0, "rot": {"Arm_R": [-30, 0, 10]}, "translate": {"Root": [0, 0, 0]}}],
     "phase": {"antic": 0.15, "contact": 0.3, "recovery": 0.45}, "trail": [0.2, 0.32]}
    ```
    `base`는 EF 동작의 첫 프레임을 기준 자세로 쓴다. `rot`은 도(°) 단위다.
  - `build_anims.py mod/anim mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat`:
    - 원본 하나당 동작 JSON 하나와 `data/<이름>.json`(trail)을 쓴다.
    - `--preview build/anim-preview`를 주면 GIF도 만든다.
    - `--check`를 주면 생성물이 커밋된 것과 다를 때 종료 코드 1을 돌려준다.
  - `RpgAnimations.BALANCED_AUTO1..3`: `AnimationAccessor<ComboAttackAnimation>`, 경로 `goddessrpg:biped/combat/balanced_auto{n}`
  - EF 무기 종류 `goddessrpg:balanced_sword`: EF `sword` 프리셋을 바탕으로 콤보를 `BALANCED_AUTO1..3`과 EF의 대시·공중 공격 기본값으로 바꾼다. 무기 분류는 `SWORD`로 둔다.
  - 아이템 `goddessrpg:training_sword` (수련용 검): 철검 수치. 모델은 임시로 바닐라 철검 모델을 쓴다(3D 모델은 계획 3).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `test_build_anims.py::test_builds_three` (임시 폴더): 원본 3개에서 동작 JSON 3개가 생긴다. 각 파일은 `load_animation`으로 다시 읽을 수 있고, 20개 관절 트랙을 모두 갖고, 마지막 키 시각이 `length`와 같다.
  - `test_check_detects_drift`: 생성물 하나를 고치면 `--check`가 1을 돌려준다.
  - `AnimationGameTest.balancedAnimationsRegistered`: 서버 로딩 후 `RpgAnimations.BALANCED_AUTO1.get()`이 null이 아니고, `AnimationManager`에서 `goddessrpg:biped/combat/balanced_auto1`을 찾을 수 있다.
  - `trainingSwordUsesOurType`: `training_sword`의 EF 능력이 `goddessrpg:balanced_sword`이고, 콤보 첫 동작이 `BALANCED_AUTO1`이다.
- [ ] **Step 2:** `python3 -m pytest tools/anim -v && (cd mod && ./gradlew runGameTestServer)`를 실행한다. 실패하면 정상이다.
- [ ] **Step 3:** `build_anims.py`를 구현한다.
- [ ] **Step 4:** 3연격 키프레임을 작성한다. 방향은 다음과 같다(밸런스형, 한손검 + 왼손 방패를 가정해 왼팔은 가드 위치 유지).
  - 1타: 오른쪽 위에서 왼쪽 아래로 사선 베기
  - 2타: 반대 방향으로 되돌려 베기
  - 3타: 반 걸음 내딛으며 찌르기 (`Root` 앞으로 이동)
- [ ] **Step 5:** `python3 tools/anim/build_anims.py mod/anim <assets 경로> --preview build/anim-preview`로 GIF 3개를 만든다. GIF로 스스로 확인할 것:
  - 관절이 꺾여 뒤틀리지 않는다.
  - 칼끝(`Tool_R` 막대)이 의도한 궤적을 그린다.
  - 시작과 끝 자세가 기준 자세와 이어진다.
- [ ] **Step 6:** Java에서 등록한다.
  - `AnimationRegistryEvent`(모드 버스)에서 `newBuilder(MODID, RpgAnimations::build)`
  - 무기 종류는 EF 자체 이벤트 훅으로 등록한다. 훅 이름(`EpicFightEventHooks`의 레지스트리 훅)은 `javap`로 확인한다.
  - 시험용 검 capability JSON은 `{"type":"goddessrpg:balanced_sword"}`이다.
- [ ] **Step 7:** `python3 -m pytest tools/anim -v && (cd mod && ./gradlew test runGameTestServer)`를 실행하고, 이어서 `python3 tools/validate_pack.py pack`도 실행한다. 모두 통과해야 한다.
- [ ] **Step 8:** `docs/admin/modpack.md`에 "전투 동작 만들기" 절을 쓴다(원본 형식, 빌드, 미리보기, 등록). 그다음 커밋한다: `feat(mod): first custom Epic Fight combo (balanced 3-hit) and training sword`

---

### Task 11: 배포: GitHub Actions 릴리스, 모드팩에 추가, 사용자 시험

**Files:**
- Create: `.github/workflows/mod-release.yml`
- Create: `pack/mods/goddessrpg.pw.toml` (packwiz `url add`로 생성)
- Modify: `docs/admin/modpack.md` ("우리 모드 릴리스" 절, 사용자 시험 체크리스트), `docs/admin/mod-sources.md`

**Interfaces:**
- Consumes: Task 1의 Gradle 작업, `tools/fetch_jars.py`
- Produces: 버전 파일 `mod/version.txt`(한 줄, 예 `0.1.0`), 릴리스 태그 규칙 `mod-v<버전>`, 자산 이름 `goddessrpg-<버전>.jar`. `build.gradle`은 버전을 이 파일에서 읽는다.

- [ ] **Step 1:** 워크플로를 작성한다.
  - 트리거는 `push`(경로 `mod/version.txt`)와 `workflow_dispatch`다. `workflow_dispatch`는 워크플로가 기본 브랜치에 있어야 쓸 수 있어서, 작업 브랜치에서는 버전 파일 변경으로 실행한다.
  - 순서: Java 21 → `pip install -r tools/requirements-dev.txt` → `python3 tools/fetch_jars.py pack .cache/jars` → `python3 -m pytest tools/anim` → `python3 tools/anim/build_anims.py mod/anim <assets> --check` → `./gradlew test runGameTestServer build`
  - 마지막으로 `gh release create mod-v<version> mod/build/libs/goddessrpg-<version>.jar --target <브랜치>`를 실행한다. 권한은 `contents: write`다.
- [ ] **Step 2:** `mod/version.txt`를 `0.1.0`으로 두고 커밋·푸시한다. GitHub MCP `actions_list`·`actions_get`으로 실행이 성공했는지 보고, `list_releases`에서 자산 URL을 확인한다. 실패하면 `get_job_logs`로 원인을 고친다.
- [ ] **Step 3:** `cd pack && ~/go/bin/packwiz url add goddessrpg <자산 URL>`을 실행하고 `side = "both"`로 둔다. 이어서 `python3 tools/validate_pack.py pack && bash tools/smoke_server.sh`를 실행해 성공하고 로그에 `goddessrpg`가 로드되었는지 확인한다.
- [ ] **Step 4:** `docs/admin/modpack.md`에 사용자 시험 체크리스트를 추가한다.
  - 새 월드 첫 접속 → 여신의 질의 3단계 → 여신의 서를 받는다
  - 여신의 서 우클릭 → 능력치(★) 화면
  - 좀비를 때려 검술 레벨이 오르는 알림이 뜬다
  - 방패나 EF 가드로 막아 방어술 경험치가 오른다
  - Z(EF 회피) 구르기가 된다
  - EF 스킬북 사용이 막힌다
  - `/give @s goddessrpg:training_sword` → 3연격 동작
  - `/give @s goddessrpg:goddess_reroll` → 재질의
- [ ] **Step 5:** 커밋하고 푸시한다: `feat(pack): add goddessrpg 0.1.0`. 그다음 사용자에게 시험용 zip(`prism/build_instance.py ... --pack-url <브랜치>`)과 동작 GIF 3개를 보낸다.

---

## 계획 3 미리보기 (이 문서 범위 밖)

- 여신의 서 책 UI(페이지 넘김 4쪽), 스킬 마인드맵
- 스킬 시스템과 슬롯 4칸, 단축키, 마력
- EF 무기 고유 스킬을 우리 스킬로 교체
- 밸런스형 스킬 세트와 스킬 동작(이 계획의 도구 사용), 3D 무기와 아이콘, 여신 모델(GeckoLib)
- 그 뒤 "2번" 작업: Born in Chaos 등 다른 모드 무기를 EF-Extra·WOM·우리 동작으로 다시 연결
