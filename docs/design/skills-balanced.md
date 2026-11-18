# 밸런스형 전위 스킬 (초안)

전위 밸런스형(검 + 방패)의 스킬 6개와 평타 구성을 정리한 표다. 수치는 모두 초안이고, 플레이 테스트 뒤 데이터 파일만 고쳐서 조정한다.

- 스킬 파일: `mod/src/main/resources/data/goddessrpg/skills/<id>.json`
- 평타 구성 파일: `mod/src/main/resources/data/goddessrpg/movesets/balanced.json`
- 전위 스킬은 마력을 쓰지 않는다(마력 0). 쿨타임과 Epic Fight 스태미나만 쓴다.

## 평타 구성

| 구분 | 동작 ID | 출처 |
|---|---|---|
| 평타 1~3 | `epicfightx:biped/combat/sword_auto1` ~ `sword_auto3` | Epic Fight Extra |
| 대시 공격 | `epicfightx:biped/combat/sword_dash` | Epic Fight Extra |
| 공중 공격 | `epicfightx:biped/combat/sword_airslash` | Epic Fight Extra |

- 무기 종류 `goddessrpg:balanced_sword`(수련용 검)에 붙는다. 가드, 탈것 공격 등 나머지는 Epic Fight 검과 같다.
- 우리 동작 `goddessrpg:biped/combat/balanced_auto1~3`으로 바꾸려면 구성 파일의 ID만 바꾸면 된다.
- 구성 파일에 없는 ID가 하나라도 있으면 로그에 오류를 남기고 Epic Fight 기본 검 콤보를 쓴다. EF-Extra가 빠진 모드팩도 이 경우다.

## 스킬

| 스킬 | id | 종류 | 얻는 법 | 핵심 효과 | 수치 | 동작 | 쿨타임 / 스태미나 |
|---|---|---|---|---|---|---|---|
| 방패 밀치기 | `balanced_shield_bash` | 액티브 | 방어술 5 달성 | 동작 중 맞힌 적을 밀쳐 내고 묶는다(`knockback_stun`) | 넉백 1.2, 기절(구속 IV) 1초, 피해 0.6배 | `goddessrpg:biped/combat/balanced_shield_bash` (우리 동작) | 6초 / 3 |
| 돌진 베기 | `balanced_dash_slash` | 액티브 | 검술 10 달성 | 돌진 베기 동작 동안 공격력 증가(`animation_strike`) | 피해 1.3배 | `epicfightx:biped/combat/sword_dash` (EF-Extra) | 8초 / 4 |
| 받아치기 | `balanced_counter` | 액티브, 밸런스형 전용 | 검술 15 달성 | 시전 후 짧은 시간 안에 들어온 첫 공격을 막고 반격(`counter_window`) | 반격 창 0.6초, 반격 피해 1.6배 | `goddessrpg:biped/combat/balanced_counter` (우리 동작, 반격할 때 재생) | 10초 / 3 |
| 굳건한 자세 | `balanced_steadfast` | 액티브, 밸런스형 전용 | 스킬북 | 잠시 받는 피해 감소, 대신 느려짐(`buff`) | 4초, 받는 피해 0.7배, 이동 속도 0.85배 | 없음 | 20초 / 2 |
| 검술 숙련 | `vanguard_sword_mastery` | 패시브 | 검술 20 달성 | 검을 들고 있으면 공격력 증가(`passive_attribute`, 조건 `sword`) | 공격력 +10% | — | — |
| 방패 숙련 | `vanguard_shield_mastery` | 패시브 | 방어술 20 달성 | 왼손에 방패를 들고 있으면 방어 증가(`passive_attribute`, 조건 `shield_offhand`) | 방어 +2 | — | — |

- "달성"은 숙련도 레벨에 닿으면 자동으로 배운다는 뜻이다. 스킬북은 중급 스킬을 얻는 방법이다.
- 검술 숙련, 방패 숙련은 전위 공통(하위 유형 제한 없음)이다. 받아치기, 굳건한 자세는 밸런스형만 배운다.

## 마인드맵 위치

직업군 중심이 (0, 0)이다. 검술 가지는 오른쪽, 방어술 가지는 왼쪽, 밸런스형 전용 가지는 아래(+y)다. 노드 사이는 40 이상 띄운다.

| 스킬 | x | y |
|---|---|---|
| 돌진 베기 | 80 | -20 |
| 검술 숙련 | 140 | -50 |
| 방패 밀치기 | -80 | -20 |
| 방패 숙련 | -140 | -50 |
| 받아치기 | 40 | 90 |
| 굳건한 자세 | -40 | 90 |

## 확인한 것

- `BalancedKitGameTest`: 수련용 검의 첫 평타가 EF-Extra `sword_auto1`인지(게임 안에서 ID가 풀림), 없는 ID가 든 테스트 전용 구성이 EF 기본 검 콤보로 대체되는지, 6개 스킬이 모두 읽히고 동작 ID가 풀리는지, 방패 밀치기 뒤 좀비가 밀려나고 구속이 걸리는지.
- `BalancedSkillFilesTest`: 위 표의 수치와 마인드맵 규칙.

## 사용자 검토 필요

- 수치 전부(피해 배수, 넉백, 기절 시간, 쿨타임, 스태미나)는 초안이다. 플레이 테스트로 조정한다.
- 방패 밀치기의 기절은 구속 IV 1초로 구현되어 있다(완전히 멈추지는 않음).
- 평타를 EF-Extra 동작으로 바꾸면서 모드팩에 Epic Fight Extra가 필요해졌다.
- 굳건한 자세는 전용 동작이 없다. 필요하면 탱킹형 `tank_fortress` 같은 자세 동작을 붙일 수 있다.
