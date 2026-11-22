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
- [ ] 동작 확인: `/goddessrpg anim list`, `/goddessrpg anim play <이름>`으로 동작 확인, `/goddessrpg anim stop`으로 재생 중인 동작이 멈추는지 확인
  - 명령어는 치트를 켠 월드 또는 OP 2단계에서만 됩니다. 동작은 F5 3인칭으로 봐야 보입니다.
  - 근접 공격 동작(balanced·tank·infight)은 검(공격 속도 1.6)을 들고 재생해야 원래 속도로 보입니다. 맨손이나 다른 무기면 더 빠르거나 느리게 재생됩니다(재생 시 안내 문구가 나옵니다). 공격 동작은 주변 몬스터를 실제로 때릴 수 있습니다.
  - 반복(loop) 동작은 움직이거나 `/goddessrpg anim stop`을 할 때까지 계속됩니다. 시전 동작(궁수·마법사·소환사·힐러·유물사)은 속도가 일정하고 피해가 없습니다.
  - 다듬지 않은 근접 동작 12개(`balanced_dash`, `tank_auto1`, `tank_auto2`, `infight_*` 9개)는 다른 모드의 동작으로 교체할 임시 자리 표시입니다(`docs/design/vanguard-moveset.md` 참고). 이 동작들로 품질을 판단하지 마세요.

### 우리 모드 (goddessrpg) 시험 (기본 동작)

- [ ] 새 월드 첫 접속 → 캐릭터 만들기 화면 → 모험가의 서를 받는다 (자세한 순서는 아래 "0.3.x 시험")
- [ ] `/goddessrpg info` 출력 확인 (명령어는 채팅창(T)에 입력)
- [ ] 모험가의 서 우클릭 → 능력치(★) 화면 확인
- [ ] 좀비를 몇 번 때린 뒤 `/goddessrpg info`에서 검술 경험치가 오른다 (레벨업은 한 번에 약 175타가 필요하니 알림은 `/goddessrpg xp sword 600`으로 확인)
- [ ] (두 명 필요) 친구가 처음 접속하면 친구에게 캐릭터 만들기 화면이 따로 뜬다 → 끝낸 뒤 나갔다 들어와도 `/goddessrpg info <친구>` 결과가 유지되고 호스트 결과는 그대로다
- [ ] (관리자 테스트용) 캐릭터 만들기 중에는 몬스터에게 맞지 않는다
- [ ] (관리자 테스트용) 만들기 중 죽거나 차원을 이동해도 화면이 다시 뜬다
- [ ] 방패나 Epic Fight 가드로 막으면 방어술 경험치가 오른다
- [ ] Z(Epic Fight 회피) 구르기와 가드가 Epic Fight 동작으로 나온다
- [ ] Epic Fight 스킬북 사용이 막힌다
- [ ] `/give @s goddessrpg:training_sword` → 3연격 동작이 나온다
- [ ] 수련용 검 3연격의 궤적·손목 각도·검기(트레일)가 자연스럽고, 3타 후 뒤로 미끄러지지 않는다

### 0.3.x 시험 (캐릭터 만들기, 스킬, 모험가의 서, 균형 장비)

모든 명령어는 채팅창(T)에 입력합니다. 치트를 켠 월드(또는 OP 2단계)에서만 됩니다. 스킬 시험은 한손검과 방패(균형)로 하는 것이 가장 편합니다. 0.2.x까지의 질의(여신의 질의)는 없어지고, 첫 접속 때 무기 카드를 고르는 캐릭터 만들기 화면이 뜹니다. 모드 이름은 "Adventurer RPG"(모드 ID는 그대로 `goddessrpg`)입니다.

**캐릭터 만들기 (카드 선택 → 별 분배)**

1. 새 월드 첫 접속 때 "캐릭터 만들기" 화면이 뜨고 무기 카드 8장이 보입니다. 지금 고를 수 있는 것은 **한손검과 방패** 카드뿐이고 나머지는 "준비 중"입니다. 카드를 고릅니다.
2. 별 12개를 나눕니다 (처음엔 칸마다 1개씩 들어 있어 8개가 남습니다). 한손검+방패의 분배 예시: **검술 재능 ★4, 검술 적성 ★2, 방어술 재능 ★3, 방어술 적성 ★3** (합계 12). 재능은 시작 레벨, 적성은 숙련도 경험치 배율이며, 칸 아래에 "Lv.N에서 시작" / "숙련도 경험치 ×N"으로 미리 보입니다. 별을 모두 써야 "확정"이 켜집니다.
3. 확정하면 모험가의 서를 받고 화면이 닫힙니다.

**방패 밀치기 자동 습득 시험용 분배**

- 방어술 재능을 **★1**로 하면 방어술 시작 레벨이 0입니다. 예: 검술 재능 ★4, 검술 적성 ★3, 방어술 재능 ★1, 방어술 적성 ★4 (합계 12). 5레벨 전에서 시작하므로 자동 습득을 볼 수 있습니다.
- 갓 만든 캐릭터에서 `/goddessrpg xp defense 2000` : 0레벨에서 5레벨까지 합계는 약 1290이라 2000이면 5레벨을 넘어 방패 밀치기를 자동으로 배우고 화면 아래에 레벨 상승 알림이 뜹니다. 1레벨업에 필요한 경험치는 40*(레벨+1)^1.6 입니다.
- 이어서 `/goddessrpg level sword 20`, `/goddessrpg level defense 20` : 나머지 스킬 (검 10: 돌진 베기, 검 15: 반격, 레벨 20 패시브 둘) 자동 습득을 확인합니다.
- `/goddessrpg xp sword 2000` 도 같은 방식으로 쓸 수 있습니다.

**다시 만들기 (재각성의 결정)**

- 제작법: 위·아래 줄 `다이아몬드 블록, 에코 조각, 다이아몬드 블록`, 가운데 줄 `에코 조각, 네더의 별, 에코 조각`. 또는 `/give @s goddessrpg:reawakening_crystal`.
- 사용: 들고 우클릭을 **두 번** 합니다. 첫 번째는 확인 안내만 뜨고, 5초 안에 한 번 더 누르면 결정 1개가 쓰이고 캐릭터가 초기화되며 캐릭터 만들기 화면이 다시 열립니다 (스킬·숙련도·슬롯 초기화, 서와 지식은 유지). 캐릭터가 없으면 쓸 수 없습니다.
- 관리자용: `/give @s goddessrpg:goddess_reroll` (관리자용 재선택의 깃)도 있습니다. `/goddessrpg reset` 은 스킬과 레벨을 초기화하고 캐릭터 만들기를 다시 엽니다.

**기타 명령어**

- `/give @s goddessrpg:skill_book[goddessrpg:skill_id="balanced_steadfast"]` : 스킬북(`balanced_steadfast`)을 얻습니다. 들고 우클릭하면 배웁니다.
- `/goddessrpg skill list` : 배운 스킬 목록, `/goddessrpg skill learn <id>` : 바로 배우기, `/goddessrpg skill slot 1 <id>` : 1번 슬롯에 장착.
- 무기 동작(moveset)은 모드 jar 안에서 읽습니다. 바꾸려면 jar를 업데이트해야 하고 `/reload` 로는 바뀌지 않습니다.

**캐릭터 만들기 화면 (게임에서만 확인 가능)**

- [ ] GUI 크기 1~4와 작은 창에서 카드와 칸의 글자가 들어맞고, 줄여 쓴 글자(60%/75%)도 읽을 수 있고, 툴팁이 읽기 쉽다.
- [ ] 카드에 아이템 아이콘이 보이고, "준비 중" 카드의 어두운 표시가 자연스럽다.
- [ ] -/+ 와 확정이 알맞게 켜지고 꺼진다. "뒤로" 뒤에 같은 카드를 다시 고르면 별 분배가 유지된다.
- [ ] 서버가 거절하면 (예: 화면이 열린 채 `/reload` 로 하위 유형이 막힘) 오류가 화면과 채팅에 뜨고 선택은 유지된다.
- [ ] ESC로 화면이 닫히지 않는다. 만들기 중 죽으면 리스폰 뒤 화면이 다시 열린다.
- [ ] 아이템 이름·툴팁·키 설정 분류가 한국어/영어 모두 알맞다. 재각성의 결정 아이콘이 인벤토리와 손에서 보인다.
- [ ] 모험가의 서 4페이지 "나의 선택": 새 캐릭터, 0.2.x에서 만든 이전 캐릭터("기록 없음"), 캐릭터 없는 플레이어 세 경우를 확인한다.
- [ ] 무기 카드 문구 8장 (한국어/영어 각 2줄)은 사용자 검토가 필요하다 (`docs/design/creation-cards.md`).

**모험가의 서**

- [ ] J 키 또는 모험가의 서 우클릭으로 열린다. J를 다시 누르거나 Esc로 닫힌다.
- [ ] 4페이지(능력치, 마인드맵, 지식, 나의 선택)를 방향키 ←/→ 또는 마우스 휠로 넘겨 본다. 닫았다 다시 열면 보던 페이지가 기억된다.
- [ ] 책 그림이 자연스럽고, 글자와 칸이 금색 테두리 안에 들어간다. 게임 설정에서 GUI 크기를 1, 2, 3, 4, 자동으로 바꿔 보고, 창을 아주 작게 줄여도 (320x240 정도) 확인한다.
- [ ] 한글이 잘 맞는다: 슬롯 이름, 긴 고서 제목이 알맞게 잘리고, 능력치 줄(예: "Lv.50 / 50")이 페이지 밖으로 나가지 않는다.
- [ ] 마인드맵: 선 색, 패시브 표시, 슬롯 번호 표시가 읽기 쉽고, 마우스를 올렸을 때 설명 창이 화면 밖으로 잘리지 않는다. 배우지 않은 스킬은 "?"로 보인다. 중앙 줄기(척추)가 자연스럽게 이어진다.
- [ ] 스킬 상태 확인: 배운 스킬, 못 배운 스킬, 장착한 스킬이 서로 구별된다.
- [ ] 슬롯 장착: 배운 액티브 스킬을 클릭 → 1~4 선택 → 아래 슬롯 줄과 화면 HUD가 바뀐다. 우클릭하면 슬롯이 비워진다. 안 되는 요청은 화면 아래에 안내 문구가 뜬다.
- [ ] 스킬북을 주우면 마인드맵의 "?" 칸이 1초쯤 뒤 알려진 칸으로 바뀐다.
- [ ] `/reload` 뒤 열려 있던 서가 다음 화면 갱신에서 바뀐 내용을 보여준다.

**스킬 습득과 사용**

- [ ] `/goddessrpg xp sword 2000`, `/goddessrpg xp defense 2000`으로 스킬 자동 습득과 알림(채팅 또는 화면 알림)이 뜬다.
- [ ] 스킬을 슬롯에 장착한다.
- [ ] Z X C V 키로 1~4번 슬롯 스킬이 시전된다 (키 설정에서 바꿀 수 있다). Epic Fight 회피는 왼쪽 Alt 키라 겹치지 않는다. 겹치는 것은 바닐라 크리에이티브의 핫바 저장/불러오기(C/X)뿐이고 무해하다.
- [ ] 시전 뒤 쿨타임이 HUD에 표시되고, 쿨타임 중에는 다시 시전되지 않는다.
- [ ] HUD 위치: 핫바 오른쪽에 슬롯 4개가 붙는다. GUI 크기 1~4와 아주 좁은 창에서 확인한다 (좁으면 2x2 칸으로 바뀌고, 핫바와 겹치지 않아야 한다).
- [ ] 스킬 동작(애니메이션)이 Epic Fight 일반 모드와 전투 모드(R로 전환) 양쪽에서 모두 제대로 나온다 (끊기거나 어색한 자세가 없는지). F5 3인칭으로 본다.
- [ ] 방패 밀치기: 좀비 앞에서 시전하면 동작 중에 좀비가 실제로 밀려나고 잠깐 둔해진다 (동작이 끝난 뒤가 아니라 동작 중 타이밍에 맞는지). 화살 같은 투사체로는 발동하지 않는다.
- [ ] 반격: 시전하면 0.6초 동안 반격 대기 상태가 되며 눈에 보이는 자세는 없다. 그 시간 안에 좀비에게 맞으면 그때서야 반격 동작이 나간다. 시간이 너무 짧거나 길지 않은지 체감을 적는다.
- [ ] 스킬북: 위 `/give` 명령으로 `balanced_steadfast` 스킬북을 얻어 우클릭하면 배운다. 배운 뒤 책이 한 권 줄어든다 (이미 알면 줄지 않는다).
- [ ] 수련용 검(`/give @s goddessrpg:training_sword`) 평타가 EF-Extra 3연격으로 나온다 (EF 기본 검 연격이 아니다).
- [ ] 무기 고유 스킬(예: Epic Fight 검의 특수 공격)은 스킬북이 아니라 무기에서 나온다. 막혀 있으면 아무 일도 일어나지 않는다 (안내 문구가 안 뜰 수도 있다). Epic Fight 스킬북으로 회피/패시브를 배우는 것은 정상이다.
- [ ] 재각성의 결정으로 다시 만들면 스킬과 슬롯이 초기화된다.
- [ ] (두 명 필요) 다른 접속자의 화면에도 스킬 정보가 정상으로 뜬다.

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

### 연결 규칙

- **끝 `Root` 위치가 0이 아닌 동작은 반드시 공격·액션 동작(Action/Attack 계열)으로 등록**: 마지막 키의 `Root` x 또는 y가 0이 아니면 대기·반복 같은 일반 동작으로 등록했을 때 끝나면서 제자리로 튕겨 돌아옴. 현재 해당하는 원본은 스크립트로 뽑음:
  `python3 -c "import json,glob;[print(p) for p in sorted(glob.glob('mod/anim/*.json')) if any(abs(v)>1e-9 for v in (json.load(open(p))['keys'][-1].get('translate') or {}).get('Root',[0,0,0])[:2])]"`
  (지금은 `archer_backstep_shot`, `archer_charge_step`, `archer_pierce_stance`, `balanced_*`의 `auto3`·`counter`·`dash`·`shield_bash`, `infight_*` 평타·`leap_slam`·`whirlwind`, `mage_charge_release`, `mage_staff_auto1~2`, `relic_needle_volley`, `relic_ring_flame_*`, `summoner_command`, `tank_auto1~2`·`shield_slam` 등 26개)
- **이동하면서 상체만 쓰는 동작은 `layer` 항목이 필요**: `archer_draw`, `archer_quick_shot`, 걸으며 쓰는 시전 동작처럼 다리는 걷기·달리기를 그대로 두고 상체만 덮어야 하는 동작은 원본에 `layer`(예: `{"layer": "COMPOSITE_LAYER", "priority": "MIDDLE", "masks": [{"livingmotion": "ALL", "type": "root_upper_joints"}]}`, Epic Fight `bow_aim_mid`와 같은 형식)를 넣어야 함. 빌드가 `data/<이름>.json`에 그대로 복사함. 없으면 동작이 다리까지 덮어 이동 중 미끄러짐
- **빈손·지팡이에 붙는 궤적은 궤적이 있는 아이템 스킨이 필요**: 궤적(`trail`)은 손에 든 아이템 스킨의 궤적 설정을 써서 그려지므로, 맨손이나 지팡이처럼 궤적이 없는 아이템이면 아무것도 보이지 않음. 해당 아이템에 궤적이 있는 스킨을 연결할 것
- **대기·반복 동작은 `biped/combat` 아래에 둠**: 우리 파일에서는 대기(`*_idle`)와 반복 동작도 공격 동작과 같은 `biped/combat/<이름>` 경로를 쓴다는 이름 규칙일 뿐, 별도 폴더로 나누지 않음

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
- 무기 종류: 평타 구성 파일 `mod/src/main/resources/data/goddessrpg/movesets/<이름>.json` 하나가 무기 종류 하나다 (`MovesetLoader`가 읽고 `RpgWeaponTypes`가 등록). 형식은 `{"weapon_type":"goddessrpg:balanced_sword","base":"epicfight:sword","combo":[ID...],"dash":ID,"air":ID}`이고 ID는 `epicfight:`, `epicfightx:`, `wom:`, `goddessrpg:` 동작 ID다. 지금 `goddessrpg:balanced_sword`는 Epic Fight `sword`와 같고 한손 콤보만 EF-Extra `sword_auto1~3` + `sword_dash` + `sword_airslash`로 바꾼 것 (무브셋 `goddessrpg:balanced_1h`). 우리 `balanced_auto1~3`으로 돌리려면 파일의 ID만 `goddessrpg:biped/combat/balanced_auto1` 등으로 바꾼다. 없는 ID가 하나라도 있으면 로그에 오류를 남기고 Epic Fight 기본 검 콤보를 쓴다 (EF-Extra가 빠진 경우 포함)
- 무기 연결: `data/<모드ID>/capabilities/weapons/<아이템>.json`에 `{"type": "goddessrpg:balanced_sword"}` (모드팩 쪽은 위 "Epic Fight 무기 연결 추가"와 같은 방법, `EF_WEAPON_TYPES`에 이미 들어 있음)
- 확인: `cd mod && ./gradlew runGameTestServer` (`AnimationGameTest`, `BalancedKitGameTest`가 동작 등록, 수련용 검 연결, EF-Extra ID 해석을 확인). 게임 테스트 실행에는 EF-Extra jar도 넣는다 (`build.gradle`의 `efxJar`)

## 우리 모드 릴리스

- 버전은 `mod/version.txt`에 한 줄로 적습니다 (예 `0.1.0`). 릴리스 태그는 `mod-v<버전>`, 자산은 `goddessrpg-<버전>.jar`입니다.
- `.github/workflows/mod-release.yml`이 `mod/version.txt`(또는 워크플로 파일) 변경 push, 또는 수동 실행(`workflow_dispatch`, 기본 브랜치에 워크플로가 있어야 함) 때 동작합니다.
- 순서: Java 21 → 도구 의존성 설치 → `fetch_jars.py`로 Epic Fight jar 받기 → 동작 도구 테스트와 `--check` → `./gradlew test runGameTestServer build` → `gh release create`.
- 같은 태그가 이미 있으면 덮어쓰지 않고 실패합니다. 워크플로 파일만 고쳐도 다시 실행되는데, 버전이 그대로면 `already exists`로 끝나는 빨간 실행은 예상된 정상 동작이며 해로울 게 없습니다. 새 릴리스를 내려면 `mod/version.txt`를 올리세요.
- 모드팩에는 릴리스 자산 URL을 `cd pack && ~/go/bin/packwiz url add goddessrpg <자산 URL>`로 등록합니다 (`side = "both"`). 새 버전은 같은 방법으로 다시 등록합니다.
