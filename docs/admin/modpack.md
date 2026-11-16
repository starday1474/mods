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

- 결과: **성공**, 서버 로딩 약 50초, 서버 쪽 모드 75개 (2026-11-16 동작 모드 추가 후 재확인) (클라이언트 전용 모드는 서버에 설치되지 않음)
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

## Epic Fight 무기 연결 추가

Epic Fight 호환 모드가 담당하지 않는 무기는 Epic Fight 공격 동작이 없습니다. 그런 무기는 `pack/kubejs/data/<모드ID>/capabilities/weapons/<아이템>.json`에 `{"type": "epicfight:<종류>"}`를 넣어 연결합니다.

- 쓸 수 있는 종류: `tools/pack_rules.py`의 `EF_WEAPON_TYPES` (검사 도구가 잘못된 종류를 막음)
- 현재 연결: Born in Chaos 12종, Eternal Starlight 12종, Cataclysm 2종(astrape, brontes), Undergarden 2종, Mowzie's Mobs 1종(sculptor_staff)
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
