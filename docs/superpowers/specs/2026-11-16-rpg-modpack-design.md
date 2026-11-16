# RPG 모드팩 설계 문서

작성일: 2026-11-16 · 상태: 사용자 승인 대기

## 1. 목적과 배경

친구들끼리 함께 플레이할 **전통적인 RPG 경험**의 마인크래프트 모드팩을 만든다.
레벨업으로 성장하고, 던전·미궁을 공략하고, 다른 모드가 추가하는 보스를 잡는 흐름이 핵심이다.
전투 애니메이션은 Epic Fight, 그래픽은 쉐이더(선택)를 사용한다.

- 대상: 사용자와 친구들(내수용). 모드 사이트에 공개 배포하지 않는다.
- 부가 용도: 유튜브 영상 촬영 가능성 있음.
- 플레이 기간: **사실상 무기한**. 정해진 엔딩 시점이 없다.
- 이 저장소에서 직접 만드는 것:
  1. 모드 목록과 설정 (packwiz)
  2. 한글 패치 리소스팩
  3. 성장 시스템 (Project MMO 설정 + Pufferfish's Skills 스킬트리 데이터팩)
  4. 보스 재조정 (KubeJS 스크립트, 필요할 때만 소규모 Java 애드온)
  5. 진행 안내 퀘스트북 (FTB Quests)
  6. 상세 설치 가이드 / 플레이 가이드 (한국어)

## 2. 목표 / 비목표 / 반목표

**목표**
- 레벨업 → 장비 강화 → 던전/보스 공략으로 이어지는 진행 흐름. 단계는 **시간이 아니라 보스 처치로** 열린다.
- 최종 보스 이후에도 계속할 거리 (높은 숙련도, Apotheosis 장비 파밍, 보스 재도전).
- 모든 텍스트가 한국어로 보이는 것.
- 친구들이 기술 지식 없이 가이드만 따라 설치·접속·업데이트할 수 있는 것.
- 모드팩 업데이트가 친구들에게 자동으로 반영되는 것.
- 사용자 PC(RAM 16GB)에서 월드를 열고 친구들이 접속해 쾌적하게 플레이 가능.

**비목표**
- 새 보스/몬스터를 처음부터 코드로 만드는 것. (전용 보스는 Brutal Bosses 데이터로 만든다.)
- 전용(24시간) 서버, 서버팩.
- CurseForge/Modrinth 공개 배포.
- 지도 모드.

**반목표 (돌아가도 실패로 보는 것)**
- 친구마다 모드 버전이 달라 접속이 안 되는 상태.
- 영어가 섞여 나와서 무슨 아이템/스킬인지 알 수 없는 상태.
- 성장이 무의미함: 레벨을 안 올려도 상위 보스를 잡을 수 있거나, 단순 반복 노가다 없이는 진행 불가.
- 사용자 PC에서 끊기거나 메모리 부족으로 튕기는 상태.

## 3. 고정된 제약 (사용자 결정)

| 항목 | 결정 |
|---|---|
| 마인크래프트 버전 | **1.21.1** |
| 모드 로더 | **NeoForge** |
| 전투 애니메이션 | **Epic Fight** (Better Combat 제외 — 충돌) |
| 쉐이더 | **Iris + Sodium**, 기본 꺼짐(선택 사항) |
| 멀티플레이 | **Essential**로 사용자가 월드를 열고 친구 초대 |
| 성장 방식 | **C안: Project MMO + Pufferfish's Skills 연동** |
| 배포/관리 | **A안: 공개 GitHub 저장소 + packwiz + Prism Launcher 자동 동기화** |
| 보스 수정 방식 | **C안: 기존 강화 모드 + KubeJS 우선, 부족할 때만 소규모 Java 애드온** |
| 지도 모드 | 사용 안 함 |
| 제외 확정 | Aether, Twilight Forest, World Bosses, Ultimate Bosses, Dimensions & Dungeons, The Bumblezone, Advent of Ascension |
| 번역 | **AI 번역 후 검수.** 아이템·툴팁은 정확성 위주, **스토리·퀘스트·설정글은 자연스럽게 다듬기** |
| 문서 | 설치·사용 방법을 상세히 적은 한국어 가이드 필수 |
| 호스트 PC | 사용자 RAM **16GB** |
| 모드 수 | 200개 이하 필수, 목표 **130~150개** (메모리 사정으로) |

## 4. 검토한 접근 방식

### 배포/관리
| 안 | 내용 | 결과 |
|---|---|---|
| **A** | packwiz + Prism Launcher 자동 동기화 | **선택** — 계속 다듬는 작업이라 버전 맞추기 비용 제거가 최우선 |
| B | CurseForge 앱 zip 공유 | 업데이트마다 수동 재설치, 버전 어긋남 |
| C | Modrinth .mrpack 공유 | B와 동일 |

### 성장
| 안 | 내용 | 결과 |
|---|---|---|
| A | Project MMO 단독 | 단일 레벨 없음 |
| B | Pufferfish's Skills 단독 | 전통적이나 단순 |
| **C** | 두 모드 연동: 숙련도 상승 → 스킬 포인트 | **선택** — 가장 풍부, 밸런스 작업 최대 |

### 보스 수정
| 안 | 내용 | 결과 |
|---|---|---|
| A | 설정 + KubeJS만 | 수치·추가 패턴만 가능, 원래 공격 동작 변경 불가 |
| B | 직접 Java 애드온 | 무엇이든 가능하나 작업량·유지보수 큼 |
| **C** | 기존 강화 모드 + KubeJS 우선 + 필요할 때만 Java | **선택** |

## 5. 모드 구성

정확한 버전 고정과 라이브러리 선정은 빌더가 한다. 아래는 콘텐츠 모드.

### 전투
- Epic Fight (21.13+), Epic Fight - Mod Compat, Epic Fight Weapons Compat
- Simply Swords, Epic Knights (필요 시 Simply Swords Epicfied: Reforged)

### 성장
- Project MMO, Pufferfish's Skills, Project MMO: Pufferfish Skills Compat
- Apotheosis (장비 등급/접두사/보석/엘리트 몹)
- Iron's Spells 'n Spellbooks (마법)

### 보스
- L_Ender's Cataclysm (초반~최종까지 단계별 배치, 수치·패턴 재조정 대상)
- Bosses'Rise (소울라이크 보스 5종)
- Bosses of Mass Destruction
- Mowzie's Mobs, Born in Chaos, Mutant Monsters
- Abyssal Corrupter (의식 소환 보스)
- **엔더 드래곤 강화: True Ending** (설계자 선택, 대안 Savage Ender Dragon)
- **위더 강화: Boss AI Tweaks** (설계자 선택, 대안 Wither: Reincarnated)
- 보스 도구: **Multiplayer Bosses** (인원수에 따른 체력·보상 조정), **Brutal Bosses** (모드팩 전용 보스 제작)
- 보류: Mo'Bosses (다운로드 적음, 안정성 테스트 후 결정)

### 차원
- Eternal Starlight (보스 4종)
- The Undergarden (Forgotten Guardian)
- Deeper and Darker (Otherside)
- Unusual End (엔드 확장)

### 던전/구조물
- When Dungeons Arise
- YUNG's 시리즈 (Better Dungeons/Strongholds/Mineshafts/Desert Temples/Ocean Monuments/Nether Fortresses, Better End Island는 True Ending과 충돌 여부 확인 후)
- Dungeons and Taverns, Towns and Towers
- Lootr

### 탐험/편의
- Waystones, Sophisticated Backpacks, Artifacts, Curios, Corpse
- JEI(또는 EMI), Jade
- FTB Quests
- Simple Voice Chat
- KubeJS (보스 재조정·연동 대체용)

### 멀티/그래픽/성능
- Essential (대체안: e4mc)
- Sodium, Iris, ModernFix, FerriteCore, Entity Culling, ImmediatelyFast
- Distant Horizons (기본 꺼짐, 영상 촬영용)

### 최적화 (자세한 내용은 8장)
- Lithium, FerriteCore, ModernFix, Entity Culling, ImmediatelyFast, Clumps, Chunky, Spark 등

### 보류
- Alex's Caves: 비공식 포팅만 존재 → 안정성 테스트 후 결정

## 6. 진행 단계 (초안 — 플레이 테스트 후 조정)

단계는 보스 처치로 열린다. 각 단계의 대표 보스를 잡으면 다음 단계 장비의 Project MMO 요구 레벨을 맞출 수 있을 만큼의 숙련도와 재료가 모이도록 조정한다.

| 단계 | 내용 |
|---|---|
| 1 | YUNG's 던전, Born in Chaos, Mutant Monsters, Undergarden(Forgotten Guardian), 대격변 초반 보스 |
| 2 | Mowzie's Mobs, Bosses of Mass Destruction, When Dungeons Arise, Eternal Starlight 게이트키퍼 |
| 3 | Bosses'Rise, Eternal Starlight 내부 보스, 대격변 중반 보스, Abyssal Corrupter |
| 4 | 강화 위더, Deeper and Darker, 대격변 후반 보스 |
| 5 | 강화 엔더 드래곤, Unusual End, 대격변 최종 보스 |
| 이후 | 높은 숙련도, Apotheosis 장비 파밍, 보스 재도전, Brutal Bosses 전용 보스 |

대격변 보스의 단계 배치는 빌더가 대격변의 원래 진행 구조를 확인해 정한다.

## 7. 직접 만드는 부분

### 7.1 한글 패치 리소스팩
- packwiz로 함께 배포하고 기본 활성화.
- 각 모드의 `assets/<modid>/lang/ko_kr.json`. 공식 ko_kr가 충분한 모드는 그대로 둔다.
- **AI 번역 후 검수.** 아이템·블록·툴팁은 용어 통일과 정확성 위주. 스토리·퀘스트·설정글·가이드북은 문장을 다듬는다.
- 용어집(주요 고유명사·스탯·보스 이름)을 저장소에 두고 통일한다.
- **저장소가 공개이므로 다른 사람이 만든 한글 패치는 넣지 않는다.** 직접 번역한 것만 넣는다.
- 모드 업데이트 시 누락 키를 찾는 방법을 마련한다 (방식은 빌더 재량).

### 7.2 성장 시스템
- Project MMO: 분야별(검술/궁술/마법/채광/방어 등) 경험치, 장비 착용 요구 레벨을 6장 단계에 맞춰 설정.
- Pufferfish's Skills 데이터팩: 근접/원거리/마법/생존 계열 스킬트리, 한국어 이름·설명.
- 연동: 숙련도 레벨업 → 스킬 포인트. 연동 애드온이 1.21.1에서 동작하지 않으면 **KubeJS로 포인트 지급**으로 대체 (C안 유지).

### 7.3 보스 재조정
- 대격변 보스: 단계에 맞춰 체력·공격력·방어력 조정 (KubeJS 속성 수정 / 대격변 설정).
- 패턴 추가: KubeJS 이벤트로 페이즈 전환 시 소환, 장판, 추가 투사체 등.
- 엔더 드래곤 / 위더: 강화 모드를 기본으로 쓰고 수치만 단계에 맞춘다.
- KubeJS로 불가능한 수정만 소규모 NeoForge Java 애드온으로 구현 (저장소 안에서 빌드).
- Multiplayer Bosses로 인원수에 따른 체력·보상 조정.

### 7.4 진행 안내 (FTB Quests)
- 지도 모드가 없으므로 퀘스트북이 다음 목표를 안내.
- 6장의 단계를 챕터로. 모드 사용법 안내 퀘스트 포함. 퀘스트 글은 처음부터 한국어로 다듬어 쓴다.

## 8. 최적화 모드와 기본 설정

전제: 사용자 PC(RAM 16GB)가 **게임 화면 + 내장 서버(월드)**를 동시에 돌린다. 그래서 화면(FPS)뿐 아니라 서버 쪽(몹 AI, 월드 생성, 메모리)도 최적화한다. Epic Fight와 알려진 충돌이 없는 모드만 쓰고, 넣은 뒤 전투·보스전을 실제로 테스트한다.

### 8.1 최적화 모드

| 분류 | 모드 | 역할 | 상태 |
|---|---|---|---|
| 화면 | Sodium | 렌더링 엔진 교체, FPS 대폭 향상 | 확정 |
| 화면 | ImmediatelyFast | UI·글자·아이템 렌더링 최적화 | 확정 |
| 화면 | Entity Culling | 안 보이는 몹·블록엔티티 렌더링 생략 | 확정 |
| 화면 | Iris | 쉐이더 (기본 꺼짐) | 확정 |
| 화면 | Distant Horizons | 원거리 렌더링 (기본 꺼짐, 영상용) | 확정 |
| 메모리 | FerriteCore | 메모리 사용량 감소 | 확정 |
| 메모리/로딩 | ModernFix | 로딩 시간·메모리 감소 | 확정 |
| 서버 | Lithium (NeoForge 공식) | 몹 AI·충돌·블록 업데이트 최적화 | 확정 |
| 서버 | Clumps | 경험치 구슬 합치기 (보스 처치 시 렉 방지) | 확정 |
| 월드 생성 | Chunky | 플레이 전 월드 미리 생성 | 확정 |
| 진단 | Spark | 렉 원인 측정 | 확정 |
| 월드 생성 | Noisiumed | 지형 생성 가속 | 테스트 후 결정 |
| 구조물 탐색 | 비동기 구조물 탐색 모드 (예: Async Locator) | 탐험 지도·locate 시 멈춤 방지 | 1.21.1 빌드 확인 후 결정 |
| 제외 | C2ME (NeoForge) | NeoForge판은 아직 알파 → 제외 | 제외 |
| 제외 | Krypton Reno | 1.21.1 지원 미확인 → 제외 | 제외 |

### 8.2 기본 설정값 (처음 설치 시 적용)

- **기본 설정은 처음 한 번만 적용**하고, 이후 친구가 바꾼 설정은 업데이트 때 덮어쓰지 않는다. (구현 방식은 빌더 재량. 예: 첫 실행 기본값 모드)
- 시야 거리 10청크, 시뮬레이션 거리 6청크 (월드를 여는 사람 기준)
- 엔티티 표시 거리 75%, 입자 "줄임"
- 쉐이더 꺼짐, Distant Horizons 꺼짐
- 몹 스폰: Born in Chaos·Apotheosis 엘리트 등 스폰이 겹치므로 스폰량을 조정해 몹 수가 과도하지 않게.

### 8.3 메모리(Java) 설정

- Java 21, G1GC 계열 권장 플래그를 Prism 인스턴스에 미리 넣어 둔다.
- 월드를 여는 사람: **8GB로 시작**, 테스트 후 최대 10GB까지. (16GB PC에서 10GB 이상은 윈도우·Essential·디스코드 등과 겹쳐 오히려 느려질 수 있다.)
- 참가자: 6GB.

### 8.4 월드 미리 생성

- When Dungeons Arise, YUNG's 등 구조물 모드가 많아 새 지역 생성 시 렉이 크다.
- 첫 플레이 전에 월드를 여는 사람이 **Chunky로 스폰 주변 반경 약 2,000~3,000블록을 미리 생성**한다 (가이드에 단계별로 적는다). 반경은 테스트로 조정.

### 8.5 설정 프리셋 (가이드에 수록)

| 프리셋 | 대상 | 내용 |
|---|---|---|
| 저사양 | 사양 낮은 친구 | 시야 6~8, 입자 최소, 엔티티 거리 50% |
| 기본 | 대부분 | 8.2 기본값 |
| 영상 촬영 | 촬영할 때 | 쉐이더 켜기, Distant Horizons 켜기, 시야 확대 (월드 여는 사람이 아닌 PC에서 촬영 권장) |

## 9. 문서 (한국어, 상세)

1. **설치 가이드** (윈도우 기준)
   - Prism Launcher 설치, 마이크로소프트 계정 로그인
   - 모드팩 인스턴스 만들기, packwiz-installer 자동 업데이트 설정 (한 번만)
   - 메모리 할당: 월드를 여는 사람(16GB PC) 8~10GB, 참가자 6~8GB (실측 후 조정)
   - 쉐이더 / Distant Horizons 켜는 법 (사양 여유 있을 때, 영상 촬영 시)
   - 자주 생기는 문제와 해결법
2. **접속 가이드**: Essential로 월드 열기·초대·참가, 대체안 e4mc 사용법
3. **플레이 가이드**
   - 성장 구조(숙련도 → 스킬 포인트 → 스킬트리), 화면 여는 키
   - Epic Fight 조작법
   - Apotheosis, Iron's Spells 기초
   - 주요 모드별 사용법(가방, 웨이스톤, 장신구, 시체 회수, 퀘스트북 등)
   - 단계별 진행 안내(스포일러 최소화)
4. **관리자용 문서**: 모드 추가/업데이트, 번역 추가·검수, KubeJS 스크립트 수정, 변경 올리는 법

## 10. 확인/테스트 항목

- 사용자 PC(16GB)에서 월드 열기 + 친구 접속 시 메모리·프레임.
- Epic Fight + Iris 쉐이더 조합.
- Essential 코스튬과 Epic Fight 플레이어 모델.
- Essential 초대로 2인 이상 접속 시 모드 불일치 없는지.
- True Ending / Boss AI Tweaks가 다른 모드와 충돌 없는지 (YUNG's Better End Island 포함).
- Epic Fight로 각 보스 모드 보스를 실제로 잡을 수 있는지 (경직, 히트박스).
- 보류 모드(Alex's Caves, Mo'Bosses) 안정성.
- 최적화 모드 적용 전후 FPS·서버 틱(Spark로 측정), 특히 보스전·대형 던전 근처.
- Lithium·Entity Culling과 Epic Fight 전투·보스 모드 충돌 여부.

## 11. 설계자가 정한 사항

- 저장소 `starday1474/mods`는 **공개** → packwiz-installer는 `raw.githubusercontent.com`의 `pack.toml`로 로그인 없이 받는다.
- 연동 애드온 실패 시 KubeJS로 대체.
- CurseForge에서 외부 다운로드를 막은 모드는 Modrinth판으로 대체하거나 제외. 수동 다운로드 단계 없음.
- Essential 불안정 시 e4mc.
- 드래곤 강화는 True Ending, 위더 강화는 Boss AI Tweaks.
- 쉐이더·Distant Horizons 기본 꺼짐.
- 플랫폼은 윈도우 기준으로 가이드 작성 (맥/리눅스 사용자가 생기면 추가).
- 다른 사람의 한글 패치는 저장소에 넣지 않음.

## 12. 빌더 재량

- 모드 정확한 버전, 라이브러리 선정, JEI vs EMI, 기본 쉐이더팩.
- 저장소 폴더 구조, 번역 누락 점검 방식.
- 스킬트리 노드 구성, 경험치·보스 수치 초안 (플레이 테스트 전제).
- 대격변 보스의 단계 배치 세부.
