# 전위 근접 구성표 (다른 모드 동작 재사용)

전위 하위 유형 4가지(설계 문서 5.2)의 평타, 대시 공격, 공중 공격, 스킬 후보를 정리한 표다. 모든 후보는 모드팩에 들어 있는 Epic Fight(EF), Epic Fight Extra(EF-Extra), Weapons of Miracles(WOM)의 동작이다.

## 쓰는 방법

- 동작 파일은 복사하지 않는다. 게임 안에서 **동작 ID로 불러 쓴다.**
  - ID 형식은 `네임스페이스:경로`이고, 각 모드가 `AnimationRegistryEvent`의 `newBuilder(네임스페이스, …)`와 `nextAccessor("경로", …)`로 등록한 이름이다.
  - EF는 `epicfight:`, EF-Extra는 `epicfightx:`, WOM은 `wom:`이다.
- 검사: `python3 tools/anim/catalog.py verify docs/design/vanguard-moveset.md`
  - 표의 모든 ID에 대해 두 가지를 확인한다. jar 안에 `assets/<네임스페이스>/animmodels/animations/<경로>.json`이 있는지, 그 경로 문자열이 해당 jar의 클래스 상수에 있는지(실제로 등록되는지)다.
  - 테스트 `tools/anim/tests/test_catalog.py::test_moveset_ids_exist_in_jars`가 같은 검사를 한다.
- 미리보기: `python3 tools/anim/catalog.py previews docs/design/vanguard-moveset.md`
  - 결과는 `build/ref-preview/<네임스페이스>__<경로>.gif`와 `index.json`이다(저장소에 넣지 않음).
  - 미리보기에서 밸런스형과 탱킹형은 방패, 쌍검은 왼손 검을 함께 그린다.
- 열 설명
  - **길이**: 파일의 전체 길이다. EF-Extra와 WOM 동작은 회복 구간이 길어 1~2.5초지만, 연계 입력이 들어오면 회복 중에 다음 동작으로 넘어간다.
  - **타격**: 무기 끝 속도로 추정한 타격 창(초)이다. 공격이 아닌 자세·피격 반응은 `—`로 둔다. `catalog.py analyse`로 잰다. 우리 무기 종류에 붙일 때 단계 시간(antic/contact/recovery)을 정하는 출발점으로 쓴다. EF-Extra·WOM의 공격 판정 시간은 각 모드의 동작 객체에 이미 들어 있다.
- 숫자 비교와 다듬기 기준은 `docs/design/motion-reference.md`에 있다.

## 밸런스형 (검 + 방패)

검과 방패, 공수 균형. 무기 종류는 `goddessrpg:balanced_sword`(EF 검 복사본)이고 왼손에 방패를 든다.

| 구분 | 동작 ID | 출처 | 길이(초) | 타격(초) | 선택 이유 |
|---|---|---|---|---|---|
| 평타 1 | `epicfightx:biped/combat/sword_auto1` | Epic Fight Extra | 1.10 | 0.18–0.23 | 골반을 150° 돌리며 내딛는 사선 베기. 20개 관절이 모두 움직이고 30Hz로 구워져 있다. `balanced_auto1` 자리 |
| 평타 2 | `epicfightx:biped/combat/sword_auto2` | Epic Fight Extra | 1.00 | 0.18–0.22 | 되돌려 베기. 골반 200° 회전, 1.4블록 전진. `balanced_auto2` 자리 |
| 평타 3 | `epicfightx:biped/combat/sword_auto3` | Epic Fight Extra | 1.30 | 0.12–0.23 | 크게 몸을 돌리는 막타(골반 220°). `balanced_auto3` 자리 |
| 평타 대안 1 | `epicfight:biped/combat/sword_auto1` | Epic Fight | 0.60 | 0.02–0.10 | EF 원본, 0.6초로 짧고 가볍다. 왼팔(방패) 움직임이 43°로 가장 작다 |
| 평타 대안 2 | `epicfight:biped/combat/sword_auto2` | Epic Fight | 0.60 | 0.10–0.18 | EF 원본 2타. 1블록 전진 |
| 평타 대안 3 | `epicfight:biped/combat/sword_auto3` | Epic Fight | 0.60 | 0.07–0.15 | EF 원본 3타 |
| 대시 | `epicfightx:biped/combat/sword_dash` | Epic Fight Extra | 1.40 | 0.42–0.47 | 몸을 낮춰 4.1블록 돌진하며 베기. 예비 동작 0.42초(30%)가 분명하다 |
| 대시 대안 | `epicfight:biped/combat/sword_dash` | Epic Fight | 0.60 | 0.12–0.18 | EF 원본, 0.6초에 1.5블록. 짧은 연계용 |
| 공중 | `epicfightx:biped/combat/sword_airslash` | Epic Fight Extra | 1.40 | 0.38–0.40 | 공중에서 내려 베며 착지(0.46블록 낮춤) |
| 공중 대안 | `epicfight:biped/combat/sword_airslash` | Epic Fight | 0.62 | 0.13–0.20 | EF 원본 공중 베기, 0.62초 |
| 스킬: 휩쓸기 | `epicfight:biped/skill/sweeping_edge` | Epic Fight | 1.00 | 0.18–0.22 | EF 검 고유 스킬. 몸을 한 바퀴 돌리는(골반 318°) 범위 베기 |
| 스킬: 막고 받아치기 | `epicfightx:biped/skill/guard_sword_hit_active1` | Epic Fight Extra | 0.30 | 0.05–0.07 | 검으로 쳐내기 성공 반응(0.3초). 반격 스킬의 첫 동작으로 이어 쓸 수 있다 |
| 스킬: 가드 반격 참고 | `wom:biped/skill/solar_guard_counter` | Weapons of Miracles | 2.55 | 0.32–0.42 | 막는 자세에서 앞으로 찔러 들어가는 반격(2.55초). 방패가 아닌 무기로 막는다 |

## 탱킹형 (방패 앞세우기)

방패와 방어술 중심, 어그로 담당. 방패를 앞에 둔 채 짧게 치는 동작을 고른다. 공격 동작은 모두 왼팔(방패)도 움직이므로, 방패를 고정하려면 EF 레이어 마스크로 상체 오른쪽만 덮어쓰는 방법을 검토한다.

| 구분 | 동작 ID | 출처 | 길이(초) | 타격(초) | 선택 이유 |
|---|---|---|---|---|---|
| 평타 1 | `epicfight:biped/combat/sword_auto1` | Epic Fight | 0.60 | 0.02–0.10 | 왼팔 움직임이 43°로 가장 작아 방패가 앞에 남는다. 0.6초 짧은 베기. `tank_auto1` 자리 |
| 평타 2 | `epicfight:biped/combat/sword_auto4` | Epic Fight | 0.62 | 0.02–0.10 | 제자리(전진 0) 베기. 버티는 탱커에 맞다. `tank_auto2` 자리 |
| 평타 무거운 대안 1 | `epicfightx:biped/combat/hammer_onehand_auto1` | Epic Fight Extra | 1.20 | 0.35–0.40 | 한손 둔기 내려치기. 예비 동작 0.35초로 묵직하고 왼팔 64° |
| 평타 무거운 대안 2 | `epicfightx:biped/combat/hammer_onehand_auto2` | Epic Fight Extra | 1.20 | 0.32–0.37 | 한손 둔기 2타, 왼팔 87° |
| 대시 | `epicfightx:biped/combat/hammer_onehand_dash` | Epic Fight Extra | 1.40 | 0.35–0.40 | 2.6블록 돌진하며 내려치기. 방패 돌진(전용 동작)은 없어서 이것을 쓴다 |
| 공중 | `epicfightx:biped/combat/hammer_onehand_airslash` | Epic Fight Extra | 1.50 | 0.38–0.47 | 공중에서 무겁게 내려찍는 착지 공격 |
| 스킬: 방패 가드 자세 참고 | `epicfightx:biped/living/shield` | Epic Fight Extra | 2.67 | — | 방패를 앞에 세우고 검을 든 가드 유지 자세(2.67초 반복). 철벽 자세의 상체 참고용이며 다리를 벌려 낮추는 동작은 없다 |
| 스킬: 방패로 막기 반응 | `epicfight:biped/combat/hit_shield_offhand` | Epic Fight | 0.05 | — | 왼손 방패로 공격을 받을 때의 짧은 반응(0.05초). 철벽 자세 중 피격 반응으로 쓸 수 있다 |

## 인파이트 — 대검

대검, 고위험 고피해. 동작이 길고 크다. 전문가 대검 동작은 2초 안팎이지만 회복 구간은 다음 입력으로 끊긴다.

| 구분 | 동작 ID | 출처 | 길이(초) | 타격(초) | 선택 이유 |
|---|---|---|---|---|---|
| 평타 1 | `epicfightx:biped/combat/greatsword_auto1` | Epic Fight Extra | 2.00 | 0.35–0.40 | 골반 240° 회전, 0.35초 예비 동작 뒤 크게 내려 베고 27° 넘침. `infight_gs_auto1` 자리 |
| 평타 2 | `epicfightx:biped/combat/greatsword_auto2` | Epic Fight Extra | 2.20 | 0.48–0.60 | 반대로 휘두르는 2타(골반 217°). `infight_gs_auto2` 자리 |
| 평타 3 | `epicfightx:biped/combat/greatsword_auto3` | Epic Fight Extra | 2.00 | 0.33–0.40 | 다리 160°, 1.6블록 전진하는 막타. `infight_gs_auto3` 자리 |
| 평타 대안 1 | `epicfight:biped/combat/greatsword_auto1` | Epic Fight | 1.25 | 0.22–0.27 | EF 원본 1.25초, 키 13개로 가볍다 |
| 평타 대안 2 | `epicfight:biped/combat/greatsword_auto2` | Epic Fight | 1.72 | 0.47–0.65 | EF 원본 2타 1.72초 |
| 대시 | `epicfightx:biped/combat/greatsword_dash` | Epic Fight Extra | 2.00 | 0.58–0.63 | 3.6블록 돌진, 예비 동작 0.58초, 넘침 41° |
| 대시 대안 | `epicfight:biped/combat/greatsword_dash` | Epic Fight | 1.65 | 0.38–0.48 | EF 원본 대검 대시 |
| 공중 | `epicfightx:biped/combat/greatsword_airslash` | Epic Fight Extra | 2.00 | 0.53–0.57 | 공중에서 대검을 내려찍는다 |
| 공중 대안 | `epicfight:biped/combat/greatsword_airslash` | Epic Fight | 1.00 | 0.45–0.58 | EF 원본, 1초 |
| 스킬: 도약 내려찍기 | `wom:biped/combat/torment_airslam` | Weapons of Miracles | 2.25 | 0.42–0.50 | 앞으로 몸을 굴려 뛰어올랐다가 내려찍는다. `infight_leap_slam` 자리 |
| 스킬: 도약 대안 | `epicfight:biped/skill/demolition_leap` | Epic Fight | 1.00 | 0.02–0.05 | EF 도약 공격의 착지 내려치기(1초). 모으기 동작 `epicfight:biped/skill/demolition_leap_charge`와 짝 |
| 스킬: 회전 베기 | `epicfight:biped/skill/steel_whirlwind` | Epic Fight | 2.53 | 0.02–0.13 | EF 대검 회전 공격(2.53초). 몸을 옆으로 눕혀 돌며 벤다. 모으기 `epicfight:biped/skill/steel_whirlwind_charging` 뒤에 쓴다 |

## 인파이트 — 쌍검

쌍검, 고위험 고피해. 빠른 연타가 중심이다.

| 구분 | 동작 ID | 출처 | 길이(초) | 타격(초) | 선택 이유 |
|---|---|---|---|---|---|
| 평타 1 | `epicfightx:biped/combat/sword_dual_auto1` | Epic Fight Extra | 1.20 | 0.32–0.37 | 두 검이 번갈아 나가는 1타. 골반 215°, 1.8블록 전진. `infight_dual_auto1` 자리 |
| 평타 2 | `epicfightx:biped/combat/sword_dual_auto2` | Epic Fight Extra | 1.40 | 0.13–0.17 | 도약하듯 0.42블록 뜨며 베기. `infight_dual_auto2` 자리 |
| 평타 3 | `epicfightx:biped/combat/sword_dual_auto3` | Epic Fight Extra | 1.40 | 0.28–0.33 | 다리 121°, 2블록 전진. `infight_dual_auto3` 자리 |
| 평타 4 | `epicfightx:biped/combat/sword_dual_auto4` | Epic Fight Extra | 1.40 | 0.18–0.23 | 막타(골반 279°). `infight_dual_auto4` 자리 |
| 평타 대안 1 | `epicfight:biped/combat/sword_dual_auto1` | Epic Fight | 0.60 | 0.12–0.18 | EF 원본 0.6초, 빠른 연타용 |
| 평타 대안 2 | `epicfight:biped/combat/sword_dual_auto2` | Epic Fight | 0.60 | 0.08–0.17 | EF 원본 2타 |
| 평타 대안 3 | `epicfight:biped/combat/sword_dual_auto3` | Epic Fight | 0.75 | 0.32–0.37 | EF 원본 3타 0.75초 |
| 대시 | `epicfightx:biped/combat/sword_dual_dash` | Epic Fight Extra | 1.40 | 0.32–0.37 | 3.7블록 돌진 베기 |
| 대시 대안 | `epicfight:biped/combat/sword_dual_dash` | Epic Fight | 0.75 | 0.12–0.18 | EF 원본 4.1블록 돌진(WOM 쌍검 버스터와 같은 동작) |
| 공중 | `epicfightx:biped/combat/sword_dual_airslash` | Epic Fight Extra | 1.40 | 0.43–0.47 | 공중에서 두 검을 내려 벤다 |
| 공중 대안 | `epicfight:biped/combat/sword_dual_airslash` | Epic Fight | 0.62 | 0.13–0.20 | EF 원본, 0.62초 |
| 스킬: 회전 연무 | `epicfight:biped/skill/dancing_edge` | Epic Fight | 1.25 | 0.65–0.73 | EF 쌍검 고유 스킬. 몸을 여러 번 돌리며(골반 486°) 5번 벤다. `infight_whirlwind` 자리 |

## 우리 전위 동작 대체 표시

우리가 직접 만든 근접 원본(`mod/anim/{balanced,tank,infight}_*.json`) 21개에 대한 판정이다.

- **대체 후보 있음**: 위 표의 동작으로 바꿔 쓸 수 있다는 뜻이다.
- **대체 없음**: Task 3에서 `motion-reference.md` 기준으로 직접 다듬는다.
- `balanced_auto1~3`은 후보가 있어도 Task 3에서 함께 다듬는다(`RpgAnimations.java`가 지금 이 동작을 쓴다).

| 우리 동작 | 판정 | 비고 |
|---|---|---|
| `balanced_auto1` | 대체 후보 있음 → `epicfightx:biped/combat/sword_auto1` |  |
| `balanced_auto2` | 대체 후보 있음 → `epicfightx:biped/combat/sword_auto2` |  |
| `balanced_auto3` | 대체 후보 있음 → `epicfightx:biped/combat/sword_auto3` | 우리 동작은 찌르기, 후보는 크게 도는 베기 |
| `balanced_counter` | **대체 없음 (직접 다듬기)** | 방패로 막은 뒤 올려 베는 반격이다. 후보 `wom:biped/skill/solar_guard_counter`는 무기로 막고 찌르고, `epicfightx:biped/skill/guard_sword_hit_active1`은 쳐내기 반응뿐이라 방패 막기가 없다 |
| `balanced_dash` | 대체 후보 있음 → `epicfightx:biped/combat/sword_dash` |  |
| `balanced_idle` | **대체 없음 (직접 다듬기)** | 방패를 앞에 둔 대기 자세다. `epicfightx:biped/living/hold_onehand_light`는 방패가 옆에 내려가 있고, `epicfightx:biped/living/shield`는 가드 유지 자세라 대기로 쓰기 어렵다 |
| `balanced_shield_bash` | **대체 없음 (직접 다듬기)** | 방패 밀치기. 세 모드의 사람형(biped) 동작에 없다 |
| `tank_auto1` | 대체 후보 있음 → `epicfight:biped/combat/sword_auto1` | 방패 팔이 43° 움직인다 |
| `tank_auto2` | 대체 후보 있음 → `epicfight:biped/combat/sword_auto4` | 우리 동작은 올려 베기, 후보는 제자리 베기 |
| `tank_fortress` | **대체 없음 (직접 다듬기)** | 다리를 벌려 낮게 버티는 반복 자세. `epicfightx:biped/living/shield`는 서서 막는 자세라 다르다 |
| `tank_shield_slam` | **대체 없음 (직접 다듬기)** | 방패로 땅 내려찍기. 없음 |
| `tank_taunt` | **대체 없음 (직접 다듬기)** | 검으로 방패 두드리며 도발. 없음 |
| `infight_dual_auto1` | 대체 후보 있음 → `epicfightx:biped/combat/sword_dual_auto1` |  |
| `infight_dual_auto2` | 대체 후보 있음 → `epicfightx:biped/combat/sword_dual_auto2` |  |
| `infight_dual_auto3` | 대체 후보 있음 → `epicfightx:biped/combat/sword_dual_auto3` |  |
| `infight_dual_auto4` | 대체 후보 있음 → `epicfightx:biped/combat/sword_dual_auto4` |  |
| `infight_gs_auto1` | 대체 후보 있음 → `epicfightx:biped/combat/greatsword_auto1` |  |
| `infight_gs_auto2` | 대체 후보 있음 → `epicfightx:biped/combat/greatsword_auto2` |  |
| `infight_gs_auto3` | 대체 후보 있음 → `epicfightx:biped/combat/greatsword_auto3` |  |
| `infight_leap_slam` | 대체 후보 있음 → `wom:biped/combat/torment_airslam` | WOM 토먼트 무기의 공중 내려찍기. 양손으로 크게 내려찍어 대검에 어울린다 |
| `infight_whirlwind` | 대체 후보 있음 → `epicfight:biped/skill/dancing_edge` | 대검으로 쓰려면 `epicfight:biped/skill/steel_whirlwind` |

### 대체 없음 목록 (Task 3 대상)

- `balanced_counter`
- `balanced_idle`
- `balanced_shield_bash`
- `tank_fortress`
- `tank_shield_slam`
- `tank_taunt`

### 방패·도발 동작을 찾아본 결과

세 jar의 동작 경로 전체(사람형 외 몹 동작 포함)에서 아래 단어를 찾았다.

- 찾은 단어: shield, bash, bump, taunt, provoke, roar, shout, howl, fortress, stance, block, guard, charge, slam, kick, punch

| 찾은 동작 | 판정 |
|---|---|
| EF·EF-Extra `biped/living/shield*`, `biped/combat/hit_shield_*` | 방패를 든 채 서 있거나 막는 자세와 피격 반응뿐이다. 밀치기·내려찍기·도발은 없다. |
| EF-Extra `biped/living/hold_guard_*`, `walk_guard_*` | 무기 가드 유지와 걷기 |
| EF·EF-Extra·WOM `biped/skill/guard_*`, `*_guard*`, `ruine_block_*` | 무기로 막기와 쳐내기. 방패를 쓰지 않는다. |
| WOM `hollow/combat/hollow_shield_bump`(방패 밀치기) | 몹 Hollow 전용 골격(어깨 갑옷 관절, 무릎 관절 없음)으로 등록된 동작이라 플레이어(biped)에 쓸 수 없다. |
| WOM `lycanth/living/lycanth_howl`(울부짖기) | 몹 Lycanth 전용 골격. 도발 참고만 가능하다. |
| WOM `biped/skill/ravanger_charge` | 무기를 앞세운 돌진. 방패 돌진이 아니다. |
| WOM `biped/skill/kick*`, `strong_punch` | 발차기와 주먹. 방패 동작이 아니다. |
| EF `biped/skill/demolition_leap`, `greatsword_slam`, WOM `torment_airslam` | 무기로 내려찍기. 방패 내려찍기가 아니다. |

결론: 방패 밀치기, 방패 내려찍기, 도발, 철벽 자세는 사람형 동작으로 쓸 수 있는 대체가 없다. 직접 다듬는다.

