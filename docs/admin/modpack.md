# 모드팩 관리자 문서

모드팩은 [packwiz](https://packwiz.infra.link/)로 관리합니다. 모드 jar 파일은 저장소에 넣지 않고, `pack/mods/*.pw.toml`에 다운로드 정보만 둡니다.

## 준비

```bash
go install github.com/packwiz/packwiz@latest
pip install -r tools/requirements-dev.txt
```

packwiz는 `~/go/bin/packwiz`에 설치됩니다.

## 모드 추가

모드는 Modrinth를 우선 쓰고, Modrinth에 1.21.1 NeoForge 버전이 없을 때만 CurseForge를 씁니다.

```bash
cd pack
~/go/bin/packwiz mr add <modrinth-slug> -y
~/go/bin/packwiz cf add <curseforge-slug> -y
~/go/bin/packwiz refresh
```

수동 다운로드가 필요한 모드는 넣지 않습니다. 넣은 모드와 넣지 못한 모드는 `docs/admin/mod-sources.md`에 기록합니다.

## 검증

```bash
python3 tools/validate_pack.py pack
python3 -m pytest tools prism -v
bash tools/smoke_server.sh
```

- `validate_pack.py`: 버전, 모드 수(200개 이하), 넣지 않기로 한 모드, 필수 모드, 클라이언트 전용 표시, 기본 설정을 검사합니다.
- `smoke_server.sh`: NeoForge 서버를 실제로 띄워 모드팩이 크래시 없이 로딩되는지 확인합니다.

## 서버 로딩 테스트 결과

`bash tools/smoke_server.sh` (2026-11-16, NeoForge 21.1.266)

- 결과: **성공**, 서버 로딩 약 50초, 서버 쪽 모드 82개 (2026-11-16 동작 모드·대격변 연계 모드 추가 후 재확인) (클라이언트 전용 모드는 서버에 설치되지 않음)
- 메모리: `-Xmx6G`로 정상 로딩
- 고친 것: Fzzy Config(다른 모드의 의존)가 Kotlin for Forge를 요구해서 추가
- 남은 로그 오류(무해): 없는 모드와의 호환 레시피(Simply Swords의 Mythic Metals 등), Unusual End 일부 전리품 표, Blueprint 태그 경고, 오프라인 서버라서 생기는 Yggdrasil 키 오류
- 참고: 이 클라우드 환경에서는 `api.github.com`이 막혀 있어서, 스크립트가 packwiz-installer 본체를 미리 받고 `--bootstrap-no-update`로 실행합니다. 친구들 PC에서는 자동 업데이트가 정상 동작합니다.

## 테스트용 인스턴스 만들기

작업 브랜치의 모드팩으로 테스트하려면, 업데이트 주소를 브랜치로 바꾼 zip을 만듭니다.

```bash
python3 prism/build_instance.py build/RPG-modpack-test.zip --pack-url https://raw.githubusercontent.com/starday1474/mods/claude/wizardly-gates-b1hzqk/pack/pack.toml
```

이 zip을 `docs/guide/install.md`의 3번 방법으로 가져오면 됩니다. 친구들에게 줄 최종 zip은 main 브랜치에 합친 뒤 `--pack-url` 없이 만듭니다.

## 사용자 테스트 체크리스트

클라우드 환경에서는 게임 화면을 띄울 수 없어서, 아래 항목은 직접 게임에서 확인해야 합니다.

- [x] (2026-11-16 확인) Prism으로 가져오기 → 첫 실행 시 모드 자동 다운로드 (CurseForge 모드 Savage Ender Dragon, FTB Quests 포함)
- [x] (2026-11-16 확인) 첫 실행 기본 설정: 한국어, 한글 리소스팩 켜짐, 렌더 거리 10, 쉐이더 꺼짐
- [ ] 설정을 바꾼 뒤 다시 켜도 바뀐 설정이 유지되는지 (덮어쓰지 않는지)
- [ ] Epic Fight 전투 + Iris 쉐이더를 켰을 때 화면이 깨지지 않는지
- [ ] Essential로 월드 열기 → 친구 1명 이상 접속 (모드 불일치 오류 없는지)
- [ ] Essential 코스튬이 Epic Fight 전투 중에 어떻게 보이는지
- [ ] 16GB PC에서 월드를 열고 친구가 접속했을 때 프레임·메모리 (F3 화면의 메모리 수치)
- [ ] 보스 몇 개를 Epic Fight로 실제로 잡아 보기 (경직, 피격 판정) — 계획 3 보스 재조정 때 함께 확인

### 우리 모드 (goddessrpg) 시험

- [ ] 새 월드 첫 접속 → 여신의 질의 3단계 → 여신의 서를 받는다
- [ ] `/goddessrpg info` 출력 확인 (명령어는 채팅창(T)에 입력)
- [ ] 여신의 서 우클릭 → 능력치(★) 화면 확인
- [ ] 좀비를 몇 번 때린 뒤 `/goddessrpg info`에서 검술 경험치가 오른다 (레벨업은 한 번에 약 175타가 필요하니 알림은 `/goddessrpg xp sword 600`으로 확인)
- [ ] (두 명 필요) 친구가 처음 접속하면 친구에게 질의 화면이 따로 뜬다 → 끝낸 뒤 나갔다 들어와도 `/goddessrpg info <친구>` 결과가 유지되고 호스트 결과는 그대로다
- [ ] (관리자 테스트용) 질의 중에는 몬스터에게 맞지 않는다
- [ ] (관리자 테스트용) 질의 중 죽거나 차원을 이동해도 질의 화면이 다시 뜬다
- [ ] 방패나 Epic Fight 가드로 막으면 방어술 경험치가 오른다
- [ ] Z(Epic Fight 회피) 구르기와 가드가 Epic Fight 동작으로 나온다
- [ ] Epic Fight 스킬북 사용이 막힌다
- [ ] `/give @s goddessrpg:training_sword` → 3연격 동작이 나온다
- [ ] 수련용 검 3연격의 궤적·손목 각도·검기(트레일)가 자연스럽고, 3타 후 뒤로 미끄러지지 않는다
- [ ] `/give @s goddessrpg:goddess_reroll` → 재질의가 시작된다

## Epic Fight 무기 연결 추가

Epic Fight 호환 모드가 담당하지 않는 무기는 Epic Fight 공격 동작이 없습니다. 그런 무기는 `pack/kubejs/data/<모드ID>/capabilities/weapons/<아이템>.json`에 `{"type": "epicfight:<종류>"}`를 넣어 연결합니다.

- 쓸 수 있는 종류: `tools/pack_rules.py`의 `EF_WEAPON_TYPES` (검사 도구가 잘못된 종류를 막음)
- 현재 연결: Born in Chaos 12종, Eternal Starlight 12종, Cataclysm 2종(astrape, brontes), Undergarden 2종, Mowzie's Mobs 1종(sculptor_staff)
- Simply Swords: Cataclysm 75종 (재료 5종 × 무기 15종): Epic Fight Weapons Compat이 Simply Swords의 같은 종류(`netherite_<종류>`)에 쓰는 설정을 그대로 복사. 예) claymore·greataxe·greathammer=greatsword, katana=uchigatana, glaive·halberd·scythe·spear·twinblade=spear
- 해머·메이스는 `axe` 또는 `greatsword`, 낫은 `greatsword`, 지팡이는 `spear`로 연결
- 총·대포 종류(Cataclysm laser_gatling, wither_assault_shoulder_weapon 등)는 근접 무기가 아니라 연결하지 않음
- 새 무기를 찾을 때: 게임에서 F3+H로 아이템 ID 확인 후 위 폴더에 추가

### 추가 동작 모드 (2026-11-16 추가)

- **EpicFight-Extra** (`epicfightx`): 새 동작 다수. 무기 종류 `epicfightx:great_tachi`, `eternal_night_scythe`, `yoto`, `efx_uchigatana`
- **Weapons of Miracles** (`wom`): 자체 무기 + 동작 세트. 무기 종류 `wom:agony`, `antitheus`, `blackstar`, `gesetz`, `herrscher`, `jabberwocky`, `moonless`, `napoleon`, `nova`, `orbit`, `ruine`, `satsujin`, `solar`, `tormented_mind`, `evil_tachi`, `hollow_longsword`, `ender_blaster`, `*_greataxe`, `*_staff` 등
- 두 모드의 종류도 다른 모드 무기에 `{"type": "wom:ruine"}`처럼 연결할 수 있음 (두 모드 모두 자체 종류를 등록함)
- 위 29개 무기를 이 동작들로 다시 연결하는 작업은 우리 모드의 자체 동작 제작(계획 2) 뒤에 함께 정함. 그때 `EF_WEAPON_TYPES`에 새 종류를 추가

## 모드팩이 강제하는 설정

`pack/config/`의 파일은 업데이트 때마다 덮어씁니다 (게임 규칙이라 친구별로 다르면 안 되는 것만).

- `block_factorys_bosses-server.toml`: Bosses'Rise 구르기 끔 (Epic Fight 회피와 겹치고 Epic Fight 애니메이션이 없음)

## 우리 모드 빌드

모드 소스는 `mod/`에 있습니다. 먼저 저장소 루트에서 `python3 tools/fetch_jars.py pack .cache/jars`로 Epic Fight jar를 받아 둡니다 (Epic Fight는 개발용 의존성으로 `.cache/jars/`의 jar를 씁니다). 모드 버전은 `mod/version.txt`에 있습니다.

```
cd mod
./gradlew test runGameTestServer build
```

- `test`: JUnit 단위 테스트
- `runGameTestServer`: 서버를 띄워 GameTest를 실행하고 끝나면 종료 (실패하면 빌드 실패). 로그에 `All required tests passed`가 나와야 함
- `build`: `mod/build/libs/goddessrpg-<버전>.jar` 생성

## 전투 동작 만들기

우리 모드의 Epic Fight 공격 동작은 짧은 키프레임 원본(`mod/anim/*.json`)을 도구로 Epic Fight 동작 파일로 바꿔 만듭니다. 첫 동작은 밸런스형 한손검 3연격(`balanced_auto1~3`)이고, 시험용 무기 **수련용 검**(`goddessrpg:training_sword`, 철검 수치, 모델은 임시로 바닐라 철검)에 연결되어 있습니다.

### 원본 형식

```json
{"length": 0.6, "base": "epicfight:biped/living/idle",
 "keys": [{"t": 0.0, "rot": {"Arm_R": [-30, 0, 10]}, "translate": {"Root": [0, 0, 0]}}],
 "phase": {"antic": 0.15, "contact": 0.3, "recovery": 0.45}, "trail": [0.15, 0.3]}
```

- `length`: 동작 길이(초). 키는 `t: 0`에서 시작해 `t: length`에서 끝나야 함 (5~8개 정도면 부드럽게 이어짐, 사이는 Epic Fight가 보간)
- `base`: 기준 자세. `epicfight:<경로>`는 Epic Fight jar 안 동작(`assets/epicfight/animmodels/animations/<경로>.json`)의 첫 프레임, `rest`는 뼈대 기본 자세. 지금은 `epicfight:biped/living/idle`을 씀 (`hold_sword`는 이 Epic Fight 버전에 없음)
- `rot`: 관절별 회전(도). 관절 자기 축 기준 X → Y → Z 순서. 적지 않은 관절은 기준 자세 그대로. 자주 쓰는 축: `Arm_R` X=팔을 앞·위로 듦, Z=음수면 바깥으로 벌림 / `Hand_R` X=팔꿈치 굽힘(0~130) / `Tool_R` X=칼끝 위·아래, Y=칼끝 좌우 / `Chest` Y=양수면 왼쪽으로 몸통 비틀기
- `translate`: 관절 위치 이동. `Root` `[0, 0.35, 0]`은 앞으로 반 걸음 (+Y가 앞, +Z가 위). **Epic Fight는 공격 동작의 `Root` 앞뒤 이동을 실제 캐릭터 이동으로 바꾸므로, 한 동작 안에서 앞으로 간 `Root`를 절대 되돌리지 말 것** (되돌리면 회복 중에 캐릭터가 뒤로 밀림). 3타 찌르기는 반 걸음 나간 `Root` 위치(`y 0.35`)를 마지막 키까지 유지함 (높이 `z`는 원래대로 돌아와도 됨). 테스트(`test_root_never_moves_back`)가 확인
- `phase`: 공격 판정 시간 (선딜 끝 `antic`, 판정 끝 `contact`, 다음 동작으로 넘어갈 수 있는 `recovery`). Java의 `RpgAnimations`에 같은 값을 적어야 함 (테스트가 확인)
- `trail`: 칼 궤적 효과 시작·끝 시간 (보통 `antic`~`contact`)
- 왼팔은 방패를 든 가드 자세(`Shoulder_L`, `Arm_L`, `Hand_L`)를 모든 키에 같은 값으로 넣어 유지

### 빌드와 미리보기

저장소 루트에서:

```
python3 tools/anim/build_anims.py mod/anim mod/src/main/resources/assets/goddessrpg/animmodels/animations/biped/combat --preview build/anim-preview
```

- 원본마다 동작 파일 `<이름>.json`과 궤적 파일 `data/<이름>.json`을 씀 (생성물도 커밋)
- `--preview build/anim-preview`: `<이름>.gif`(옆·앞 화면)와 `<이름>_sheet.png`(장면 모음)를 만듦. 관절이 뒤틀리지 않는지, 칼끝(검은 막대)이 의도한 궤적을 그리는지, 처음과 끝이 기준 자세로 이어지는지 확인
- `--check`: 생성물이 원본과 다르면 종료 코드 1 (테스트에서도 확인). 원본을 고친 뒤 빌드를 잊지 말 것
- Epic Fight 동작을 미리 보려면 `python3 tools/anim/preview.py jar:assets/epicfight/animmodels/animations/biped/combat/sword_auto1.json build/anim-preview/sword_auto1.gif`

### 등록

- 동작: `mod/src/main/java/dev/goddessrpg/compat/epicfight/RpgAnimations.java`의 `build()`에 `builder.nextAccessor("biped/combat/<이름>", ...)`를 추가 (Epic Fight 검 동작처럼 `ComboAttackAnimation`, 판정 관절 `toolR`, 공격 속도 기준 1.6)
- 무기 종류: `RpgWeaponTypes.java`. `goddessrpg:balanced_sword`는 Epic Fight `sword`와 같고 한손 콤보만 `balanced_auto1~3` + Epic Fight 대시·공중 베기로 바꾼 것 (Epic Fight 1.21 버전은 콤보를 "무브셋"으로 관리하므로 무브셋 `goddessrpg:balanced_1h`를 함께 등록)
- 무기 연결: `data/<모드ID>/capabilities/weapons/<아이템>.json`에 `{"type": "goddessrpg:balanced_sword"}` (모드팩 쪽은 위 "Epic Fight 무기 연결 추가"와 같은 방법, `EF_WEAPON_TYPES`에 이미 들어 있음)
- 확인: `cd mod && ./gradlew runGameTestServer` (`AnimationGameTest`가 동작 등록과 수련용 검 연결을 확인)

## 우리 모드 릴리스

- 버전은 `mod/version.txt`에 한 줄로 적습니다 (예 `0.1.0`). 릴리스 태그는 `mod-v<버전>`, 자산은 `goddessrpg-<버전>.jar`입니다.
- `.github/workflows/mod-release.yml`이 `mod/version.txt`(또는 워크플로 파일) 변경 push, 또는 수동 실행(`workflow_dispatch`, 기본 브랜치에 워크플로가 있어야 함) 때 동작합니다.
- 순서: Java 21 → 도구 의존성 설치 → `fetch_jars.py`로 Epic Fight jar 받기 → 동작 도구 테스트와 `--check` → `./gradlew test runGameTestServer build` → `gh release create`.
- 같은 태그가 이미 있으면 덮어쓰지 않고 실패합니다. 워크플로 파일만 고쳐도 다시 실행되는데, 버전이 그대로면 `already exists`로 끝나는 빨간 실행은 예상된 정상 동작이며 해로울 게 없습니다. 새 릴리스를 내려면 `mod/version.txt`를 올리세요.
- 모드팩에는 릴리스 자산 URL을 `cd pack && ~/go/bin/packwiz url add goddessrpg <자산 URL>`로 등록합니다 (`side = "both"`). 새 버전은 같은 방법으로 다시 등록합니다.
