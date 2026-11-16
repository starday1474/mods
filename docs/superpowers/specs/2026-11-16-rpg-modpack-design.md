# RPG 모드팩 설계 문서

작성일: 2026-11-16

## 1. 목적과 배경

친구들끼리 함께 플레이할 **전통적인 RPG 경험**의 마인크래프트 모드팩을 만든다.
레벨업으로 성장하고, 던전·미궁을 공략하고, 다른 모드가 추가하는 보스를 잡는 흐름이 핵심이다.
전투 애니메이션은 Epic Fight, 그래픽은 쉐이더를 사용한다.

- 대상: 사용자와 친구들(내수용). 모드 사이트에 공개 배포하지 않는다.
- 부가 용도: 유튜브 영상 촬영 가능성 있음 → 쉐이더/원거리 렌더링 품질이 의미 있음.
- 이 저장소에서 "직접 만드는 것"은 크게 네 가지다:
  1. 모드 목록과 설정 (packwiz로 관리)
  2. 한글 패치 리소스팩
  3. 성장 시스템(스킬트리 데이터팩 + Project MMO 설정)
  4. 상세 설치 가이드 / 플레이 가이드 문서 (한국어)

## 2. 목표 / 비목표 / 반목표

**목표**
- 레벨업 → 장비 강화 → 던전/보스 공략으로 이어지는 진행 흐름.
- 모든 모드의 텍스트가 한국어로 보이는 것(공식 번역 없는 모드는 직접 번역).
- 친구들이 기술 지식 없이 가이드만 따라 설치·접속·업데이트할 수 있는 것.
- 모드팩 업데이트가 친구들에게 자동으로 반영되는 것.

**비목표**
- 새 보스/몬스터/아이템을 코드로 직접 만드는 것 (Java 모드 개발은 하지 않음).
- 전용(24시간) 서버 운영 및 서버팩.
- CurseForge/Modrinth 공개 배포.
- 지도 모드 (사용자 결정으로 제외).

**반목표 (기술적으로 돌아가도 실패로 보는 것)**
- 친구마다 모드 버전이 달라 접속이 안 되는 상태.
- 영어가 섞여 나와서 무슨 아이템/스킬인지 알 수 없는 상태.
- 성장이 의미 없음: 레벨을 안 올려도 끝 보스까지 갈 수 있거나, 반대로 노가다 없이는 진행 불가.
- 사양이 너무 무거워 월드를 여는 사람 PC에서 플레이가 안 되는 상태.

## 3. 고정된 제약 (사용자 결정)

| 항목 | 결정 |
|---|---|
| 마인크래프트 버전 | **1.21.1** |
| 모드 로더 | **NeoForge** (1.21.1용 Epic Fight가 NeoForge 기반) |
| 전투 애니메이션 | **Epic Fight** |
| 쉐이더 | 사용 → **Iris + Sodium** (NeoForge 1.21.1 빌드 존재) |
| 멀티플레이 방식 | **Essential 모드**로 한 명이 월드를 열고 친구 초대 |
| 성장 방식 | **C안: Project MMO(행동별 숙련도) + Pufferfish's Skills(스킬트리) 연동** |
| 지도 모드 | **사용 안 함** |
| 배포/관리 방식 | **A안: GitHub 저장소 + packwiz + Prism Launcher 자동 동기화** |
| 모드 수 | 라이브러리 포함 **200개 이하** (예상 130~150개) |
| 문서 | **설치 방법과 사용 방법을 상세히** 적은 한국어 가이드 필수 |

기타 제약
- Better Combat은 Epic Fight와 충돌하므로 제외.
- 모드 jar 파일은 저장소에 넣지 않는다(packwiz가 다운로드 링크로 관리). 저장소가 공개여도 문제 없도록.

## 4. 검토한 접근 방식

### 배포/관리 방식
| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A (선택)** | packwiz로 저장소 관리, 친구는 Prism Launcher + packwiz-installer로 실행 시 자동 동기화 | 업데이트 자동, 변경 이력, 되돌리기 쉬움, CF/Modrinth 포맷으로 내보내기도 가능 | 친구들이 Prism Launcher 최초 1회 설치 필요 |
| B | CurseForge 앱 프로필 zip 공유 | 익숙함 | 업데이트마다 수동 재설치, 버전 어긋남 잦음 |
| C | Modrinth .mrpack 공유 | B와 유사 | B와 동일 |

선택 이유: 스킬트리·밸런스·번역은 계속 다듬는 작업이라, 친구들 버전을 맞추는 비용을 없애는 것이 가장 중요.

### 성장 방식
| 안 | 내용 | 비고 |
|---|---|---|
| A | Project MMO 단독 (행동별 숙련도) | 단일 캐릭터 레벨 없음 |
| B | Pufferfish's Skills 단독 (경험치→포인트→스킬트리) | 가장 전통적 |
| **C (선택)** | 둘을 "Project MMO: Pufferfish Skills Compat"로 연동 — 숙련도가 오르면 스킬 포인트 획득 | 가장 풍부, 밸런스 작업 최대 |

## 5. 모드 구성 (초안 — 버전 고정은 빌더가 수행)

### 전투
- Epic Fight (21.13+), Epic Fight - Mod Compat, Epic Fight Weapons Compat
- Simply Swords, Epic Knights (무기/갑옷), 필요 시 Simply Swords Epicfied: Reforged

### 성장
- Project MMO, Pufferfish's Skills, Project MMO: Pufferfish Skills Compat
- Apotheosis (장비 등급/접두사/보석/엘리트 몹)
- Iron's Spells 'n Spellbooks (마법 계열)

### 보스
- L_Ender's Cataclysm, Mowzie's Mobs, Born in Chaos, Mutant Monsters
- Twilight Forest, The Aether, Deeper and Darker

### 던전/구조물
- When Dungeons Arise (NeoForge 1.21.1 2.1.68)
- YUNG's 시리즈 (Better Dungeons/Strongholds/Mineshafts/Desert Temples/Ocean Monuments/Nether Fortresses 등)
- Dungeons and Taverns, Towns and Towers
- Lootr (멀티에서 상자 보상 개인별 지급)

### 탐험/편의
- Waystones, Sophisticated Backpacks, Artifacts, Curios, Corpse
- JEI(또는 EMI), Jade
- FTB Quests (진행 안내 퀘스트북 — 지도 없이 던전/보스 순서 안내)
- Simple Voice Chat

### 멀티/그래픽/성능
- Essential
- Sodium, Iris, ModernFix, FerriteCore, Entity Culling, ImmediatelyFast, Distant Horizons

### 보류 (검증 후 결정)
- Alex's Caves: 공식 1.21.1 없음, 비공식 포팅만 존재 → 안정성 테스트 후 결정
- Ice and Fire, Aquamirae: 1.21.1 빌드 미확인 → 기본 제외

## 6. 직접 만드는 부분

### 6.1 한글 패치 리소스팩
- 저장소 내 리소스팩 폴더로 관리하고 packwiz로 함께 배포, 기본 활성화.
- 각 모드의 `assets/<modid>/lang/ko_kr.json`을 담는다.
- 모드에 이미 공식 ko_kr가 있으면 그대로 두고, 없거나 불완전한 것만 채운다.
- 퀘스트북, 스킬트리 등 직접 만든 콘텐츠는 처음부터 한국어.
- 모드 업데이트로 키가 추가되면 누락 키를 찾을 수 있는 방법(스크립트 등)이 있으면 좋음 — 방식은 빌더 재량.

### 6.2 성장 시스템
- Project MMO 설정: 분야별(검술/궁술/마법/채광/방어 등) 경험치 획득량, 장비 착용 레벨 제한.
  - 예: 보스 드롭 무기는 높은 숙련도 요구. 진행 순서(바닐라 → 일반 던전 → 중형 보스 → Cataclysm 등 상위 보스)에 맞춰 단계화.
- Pufferfish's Skills 데이터팩: 직업 성격의 스킬트리(근접/원거리/마법/생존 계열 등), 한국어 이름·설명.
- 연동: 숙련도 레벨업 시 스킬 포인트 지급.

### 6.3 진행 안내 (FTB Quests)
- 지도 모드가 없으므로 퀘스트북이 "다음에 뭘 할지"를 안내하는 역할.
- 단계: 시작 → 첫 던전 → 보스별 챕터 → 최종 보스. 모드 사용법 안내 퀘스트 포함.

## 7. 문서 (한국어, 상세)

`docs/` 아래 친구용 가이드. 스크린샷을 넣을 수 있으면 넣는다.

1. **설치 가이드**
   - Java/Prism Launcher 설치, 마이크로소프트 계정 로그인
   - 모드팩 인스턴스 만들기, packwiz-installer 자동 업데이트 설정 (한 번만)
   - 메모리 할당 권장값 (월드 여는 사람 10~12GB, 참가자 6~8GB 기준 — 실측 후 조정)
   - 쉐이더 켜고 끄기, 사양 낮을 때 끌 옵션
   - 자주 생기는 문제와 해결법
2. **접속 가이드**: Essential로 월드 열기/친구 초대/참가
3. **플레이 가이드**
   - 레벨업 구조(숙련도 → 스킬 포인트 → 스킬트리) 설명과 화면 여는 키
   - Epic Fight 조작법(전투 모드 전환, 회피, 스킬)
   - 장비 성장(Apotheosis), 마법(Iron's Spells) 기초
   - 주요 모드별 사용법(가방, 웨이스톤, 장신구, 사망 시 시체 회수, 퀘스트북 등)
   - 권장 진행 순서(던전/보스)
4. **관리자용 문서**: 모드 추가/업데이트, 번역 추가, 변경 올리는 방법

## 8. 확인/테스트 항목

- Epic Fight + Iris 쉐이더 조합이 깨지지 않는지 (쉐이더팩 1~2개 지정).
- Essential 코스튬이 Epic Fight 플레이어 모델에서 어떻게 보이는지.
- Essential 초대로 2인 이상 접속 시 모드 불일치 없이 접속되는지.
- 모드 전체 로딩 시 메모리 사용량과 로딩 시간.
- 보류 모드(Alex's Caves 등) 안정성.

## 9. 빌더 재량

- 각 모드의 정확한 버전 고정, 라이브러리 모드 선정.
- JEI vs EMI, 기본 쉐이더팩 선택.
- 번역 작업 순서와 누락 키 점검 방식.
- 스킬트리 구체 노드 구성, 경험치 수치 초안 (플레이 테스트 후 조정 전제).
- 저장소 폴더 구조.

## 10. 설계자가 정한 사항 (사용자 확인 전, 검토 후 추가)

- **저장소 공개 여부**: `starday1474/mods`는 **공개(public)** 저장소다. packwiz-installer는 `raw.githubusercontent.com`의 `pack.toml` URL로 로그인 없이 받는다. 별도 호스팅 불필요.
- **연동 모드 대체안**: "Project MMO: Pufferfish Skills Compat"가 NeoForge 1.21.1에서 동작하지 않거나 불안정하면, C안의 의도(숙련도 상승 → 스킬 포인트)를 유지한 채 **KubeJS 스크립트(또는 데이터팩)로 포인트를 지급**하는 방식으로 대체한다. A/B안으로 후퇴하지 않는다.
- **수동 다운로드만 가능한 모드**: CurseForge에서 외부 도구 다운로드를 막은 모드는 **Modrinth판으로 대체하거나 빼거나 다른 모드로 바꾼다.** 친구들에게 수동 다운로드 단계를 두지 않는다.
- **Essential 대체안**: Essential이 이 모드팩에서 안정적으로 동작하지 않으면 **e4mc**(NeoForge 지원, 설정 없이 링크 공유로 접속)를 대체안으로 쓰고 접속 가이드에 함께 적는다.
