# 동작 원리와 레퍼런스 모음

작성일은 2026-11-22이다. 동작을 새로 만들거나 다듬을 때 이 문서를 출발점으로 쓴다.

- `motion-reference.md`에는 모드팩에 실제로 들어 있는 전문가 동작을 분석한 수치 기준이 있다. 이 문서는 그 바깥의 **일반 원리와 외부 자료**를 모은 것이다.
- 방향은 스펙 5.9를 따른다. "현실적인 판타지"보다 **"재미있는 판타지"**를 목표로 하고, 몬헌처럼 살짝 과장된 움직임을 쓴다.

## 0. 작업 원칙

- 레퍼런스는 **여러 개를 보고 원리를 뽑아낸 뒤 섞어서** 우리 동작으로 만든다. 특정 게임의 동작을 프레임 단위로 따라 그리지 않는다.
- 조작 체계, 타이밍 구조, 게임 규칙은 가져와도 된다. 예를 들어 몬헌 활의 차지 단계와 연계 흐름은 가져올 수 있다.
- 외형과 동작 데이터 자체는 우리가 새로 만든다.
- 동작 하나를 만들 때 서로 다른 레퍼런스를 **최소 2개 이상** 본다. 셋 중 하나는 실제 사람의 움직임(궁도, 검술 영상 등)이면 좋다.
- 확인할 때는 "읽히는가(무슨 공격인지 바로 보이나)", "손맛이 있나", "조작이 답답하지 않나" 세 가지를 본다.

## 1. 일반 원리

### 1.1 예비 동작 → 타격 → 회복 (3단 구조)

모든 공격은 세 구간으로 나눈다.
- 격투게임에서는 startup / active / recovery라고 부른다.
- 다크소울 분석에서는 Anticipation / Attack / Recovery라고 부른다.

| 구간 | 역할 | 우리 기준 |
|---|---|---|
| 예비 동작 | 무엇이 올지 알려줌, 힘 모으기 | 반대 방향으로 감기. 무거울수록 길게 |
| 타격 | 실제 판정, 가장 빠른 구간 | 2~4프레임(20fps 기준 0.1~0.2초) 안에 휘두름 끝 |
| 회복 | 무게감, 다음 입력까지의 틈 | 여운은 남기되 캔슬 구간을 일찍 열어 조작감 유지 |

- 프레임은 곧 시간이다. 60fps에서 10프레임은 1/6초다. EF 동작은 초 단위이므로 `0.05초 = 60fps 3프레임`으로 환산해 생각한다.
- **게임에서는 예비 동작이 영화보다 더 중요하다.** 플레이어가 작은 움직임 하나로 다음 행동을 읽어야 하기 때문이다.
- 회복을 너무 길게 잡으면 캐릭터가 굼떠 보인다. 무게감은 회복의 길이보다 **넘침과 감속 곡선**으로 표현한다.

### 1.2 무게감 = 여운과 겹침의 양

- 무거운 부위일수록 늦게 따라오고 늦게 멈춘다. 무기는 팔보다, 팔은 몸통보다 늦게 따라온다.
- 무거운 공격은 예비 동작을 줄이는 대신 여운을 크게 줄 수도 있다. 큰 질량을 빨리 움직인 느낌이 난다.
- 게임에서는 여운을 일부러 과장하는 경우가 많다. 가독성을 높이고 타격감을 조절하는 수단이다.

### 1.3 과장과 가독성

- 실루엣이 먼저다. 예비 자세는 정면과 3인칭 뒤쪽 시점 모두에서 팔과 무기의 윤곽이 몸통과 겹치지 않아야 한다.
- 우리 방향은 "재미있는 판타지"이므로, 예비 동작의 감기와 타격 후 넘침을 현실보다 **1.2~1.5배** 정도 과장해도 된다.
- 체공, 회전, 무기 궤적도 과장해도 된다. 다만 판정 위치와 보이는 궤적은 일치해야 한다.

### 1.4 손맛 (game feel / juice)

- **히트스톱:** 맞는 순간 아주 짧게 멈춘다. 격투게임에서는 이제 거의 표준이다.
  - 히트스톱이 부족하거나, 소리가 안 맞거나, 카메라 처리가 없으면 타격감이 떨어진다는 연구가 있다.
  - EF에는 자체 히트 정지와 흔들림이 있다. 우리 쪽에서는 효과음과 파티클 타이밍을 타격 키에 맞춘다.
- 화면 흔들림, 파티클, 소리는 **동작의 타격 키 시각에 정확히 맞춰야** 한다. 우리는 phase 정보가 있으므로 그 시각에 효과를 건다.

### 1.5 캔슬 구간

- 연계(평타 1→2→3, 차지 후 사이드스텝 등)는 회복 구간 중간부터 다음 입력을 받는다.
- 연결 지점의 자세는 다음 동작의 시작 자세와 가까워야 한다. 그래야 섞일 때 튀지 않는다. 기존 계획의 "연계 이음매" 검증이 이것이다.

## 2. 우리 도구로 옮기는 법

| 원리 | `build_anims.py` 기능 |
|---|---|
| 예비 동작의 천천히 → 빠르게 | 타격 직전 키에 `ease: "in"` |
| 타격 후 감속 | 타격 키 다음 구간에 `ease: "out"` |
| 넘침(오버슈트) | 회복 첫 키에 `ease: "back"` |
| 부드러운 곡선 표현 | 원본에 `bake: 30` |
| 효과 타이밍 | `phase`의 타격 시각 + `trail_joint` |
| 무게감 겹침 | 몸통 → 팔 → 무기 순서로 키를 1~2프레임씩 늦게 배치 |
| 상하체 분리(이동 중 사격 등) | `layer` |

## 3. 직업별 레퍼런스

### 3.1 근접 (밸런스·탱킹·인파이트)

- **기본 방침:** 모드팩에 있는 EF, EpicFight-Extra, WOM 동작을 ID로 불러 쓴다. 구성표는 `vanguard-moveset.md`에 있다.
- 직접 만드는 것은 방패 밀치기, 도발처럼 대체할 동작이 없는 경우뿐이다.
- 볼 자료:
  - 다크소울3 적 공격 해부. 3단 구조와 텔레그래프를 다룬다.
  - 실제 검술과 방패술 영상. 체중 이동과 허리 비틀기를 본다.
- 판타지 과장 포인트:
  - 대검은 예비 동작에서 등 뒤까지 크게 감는다.
  - 방패 밀치기는 한 발 내딛고 어깨를 먼저 던진다.

### 3.2 궁수 (장궁, 몬헌월드식 조작)

**가져오는 것 (시스템):**
- 누르고 있으면 활을 당기고, 차지 1~3단계가 있다.
- 연속으로 쏘면 다음 사격의 차지 단계가 오른다.
- 차지한 채로 사이드스텝, 강사격 2연, 곡사, 관통 사격, 퀵샷.

**새로 만드는 것 (동작):** 실제 궁술의 단계를 바탕으로 과장한다.

| 궁도 사법팔절 | 뜻 | 우리 동작에서 |
|---|---|---|
| 아시부미 | 발 디딤 | 사격 시작 시 발을 어깨너비로 벌림 |
| 도즈쿠리 | 몸통 세우기 | 허리를 펴는 짧은 예비 동작 |
| 유가마에 | 준비 | 화살 메기기. 퀵샷에서는 생략 |
| 우치오코시 | 활 들어올리기 | 차지 시작. 판타지 과장으로 머리 위까지 들어올림 |
| 히키와케 | 당기기 | 차지 1→2→3. 단계마다 작은 "멈칫"을 넣어 단계가 읽히게 함 |
| 가이 | 만작 | 차지 유지. 호흡처럼 몸이 미세하게 들썩임 |
| 하나레 | 발사 | 가장 빠른 구간. 활이 앞으로 튕기고 시위 손이 뒤로 펼쳐짐(넘침) |
| 잔신 | 남은 자세 | 짧은 여운. 연속 사격 입력이 들어오면 즉시 캔슬 |

- 서양 양궁의 "샷 사이클"도 같은 생각이다. 매번 같은 순서를 반복해 일관성을 만든다.
- 판타지 과장 아이디어:
  - 3단 차지 때 몸이 살짝 뒤로 젖혀지고 활이 크게 휜다.
  - 강사격은 발사 반동으로 반 발짝 밀린다.
  - 곡사는 하늘을 향해 크게 뒤로 젖힌다.
  - 관통 사격은 활을 옆으로 눕혀 한 번 휘두르듯 쏜다. 다만 이것은 우리 식으로 새로 디자인한다.
- 사격 판정은 WOM 엔더 블래스터처럼 애니메이션 기반의 즉발 판정이다. 따라서 발사 키 시각 = 판정 시각 = 효과 시각으로 맞춘다.

### 3.3 마법 계열 (마법사·소환사·치유사)

- **사전 시전 = 예비 동작:** 강한 마법일수록 시전 전에 신호를 보여준다. 상대(와 동료)에게 경고가 되고, 터질 때의 쾌감도 커진다.
- **WoW 7.3의 사례:** 반복되는 시전 루프 대신 "땅에서 마력을 끌어올리는" 짧은 준비 자세를 넣었다. 반응성과 무게감 사이의 균형점을 찾았다고 한다.
  - 우리 시전형(즉시/차지/유지)에 그대로 적용한다. 서클이 높을수록 준비 자세를 크게 한다.
- **스냅감:** 지팡이나 손을 튕기는 마지막 동작을 짧고 날카롭게 하면 시전이 의도적으로 보인다. 손 모양을 명확히 하는 것도 가독성에 좋다.
- **범위 마법:** 피해 전에 예고 구간을 두고, 범위 표시를 같이 보여준다.
- **VFX 가독성:**
  - 중요한 효과일수록 밝기와 채도를 높인다.
  - 완전한 흑·백은 피한다. 지형이나 UI로 오인되기 때문이다.
  - 익숙한 시각 언어를 재사용한다. 예를 들어 치유는 초록색과 +.
- **직업별 차이:**
  - 마법사는 손과 지팡이 중심으로 날카롭게 움직인다.
  - 소환사는 바닥에서 끌어올리고, 손바닥을 위로 향한 큰 동작을 쓴다.
  - 치유사는 부드러운 곡선(`inout`)으로 감싸는 동작을 쓴다.

### 3.4 유물사 (아이템 사용형)

- 원칙은 "물건을 쓰는 동작"이다. 반지는 주먹을 쥐며 앞으로 내밀고, 바늘은 손가락 지휘하듯 움직인다.
- 동작은 짧게 두고, 효과는 유물 자체(떠다니는 바늘, 불꽃)가 맡는다.
- 마법 계열의 사전 시전과 스냅 원리를 그대로 쓰되, 손목과 손가락 위주로 작게 움직인다.
- 유물 컨셉 20개가 정해지면 유물마다 레퍼런스를 2개 이상 붙인다.

## 4. 출처

**일반 원리**
- [The 12 principles of animation in video games — Game Developer](https://www.gamedeveloper.com/production/the-12-principles-of-animation-in-video-games)
- [12 principles for game animation — Game Developer](https://www.gamedeveloper.com/game-platforms/12-principles-for-game-animation)
- [Adding weight to your game design, Part 2: Anticipation](https://gamedeveloper.com/design/adding-weight-to-your-game-design-part-2-anticipation)
- [Adding weight to your game design, Part 5: Follow-through & Overlap](https://gamedeveloper.com/design/adding-weight-to-your-game-design-part-5-follow-through-overlap)
- [Anticipation — Animation Mentor](https://animationmentor.com/blog/anticipation-the-12-basic-principles-of-animation/)
- [Twelve basic principles of animation — Wikipedia](https://en.wikipedia.com/wiki/Timing_(animation))

**전투 손맛 / 프레임**
- [Anatomy of an Enemy Attack in Dark Souls 3 — Game Developer](https://www.gamedeveloper.com/game-platforms/anatomy-of-an-enemy-attack-in-dark-souls-3)
- [Avowed 히트스톱 인터뷰 — PC Gamer](https://www.pcgamer.com/games/rpg/if-youve-been-worried-about-avowed-not-looking-punchy-enough-obsidians-combat-designer-has-a-hitstop-animation-superpower-that-might-reassure-you)
- [Designing Game Feel: A Survey — Pichlmair & Johansen (arXiv)](https://arxiv.org/pdf/2011.09201)
- [Impact feel 연구 논문 (UW)](https://faculty.washington.edu/zkwen/articles/lin22features.pdf)
- [20 Free Combat Feel and Juice Resources — Gamine AI](https://gamineai.com/resources/20-free-combat-feel-and-juice-resources-hitstop-screenshake-vfx-timing-2026-edition)
- [SF Seminar: frame data — Capcom](https://game.capcom.com/cfn/sfv/column/131432?lang=en)
- [Reading Frame Data — Prima Games](https://primagames.com/eguides/street-fighter-v-eguide/primer/reading-frame-data)
- [Fighting Games Essentials: Frame Data — DashFight](https://dashfight.com/news/fighting-games-essentials-frame-data-3156)
- [Tekken 8 frame data beginners guide — esports.gg](https://esports.gg/guides/tekken-8/tekken-8-frame-data-beginners-guide/)

**몬헌월드 활 (조작 체계)**
- [Monster Hunter World's bow is great — Red Bull](https://redbull.com/ca-en/monster-hunter-world-bow-guide)
- [Yet Another Weapon Guide — Bow](https://docs.google.com/document/u/0/d/1vuDNCYYhNvSPHMqPwoC2cyjF-qcuXztYsipi9jUZHa0/mobilebasic)
- [MHW Bow — Game8](https://game8.co/games/Monster-Hunter-World/archives/292950)
- [MHW Bow — Fextralife wiki](https://monsterhunterworld.wiki.fextralife.com/Bow)
- [The Hunter's Arsenal — Massively OP](https://massivelyop.com/2018/03/27/the-hunters-arsenal-massively-ops-guide-to-the-weapons-of-monster-hunter-world/)

**실제 궁술**
- [Kyudo: Japanese Archery — Nippon.com](https://www.nippon.com/en/features/jg00090/kyudo-japanese-archery.html)
- [La técnica del disparo en Kyudo: el sistema Hassetsu — Espadas y Más](https://espadasymas.com/en/blogs/actualidad/la-tecnica-del-disparo-en-kyudo-el-sistema-hassetsu-en-el-tiro-con-arco-japones)
- [Kyudo — Wikipedia](https://Www.Wikipedia.org/wiki/Kyudo)
- [A well-defined shot sequence — Human Kinetics](https://us.humankinetics.com/blogs/excerpt/a-well-defined-shot-sequence-increases-the-probability-of-a-consistent-outcome)

**마법 시전 / VFX**
- [How UX design can help with VFX — Mighty Bear Games](https://medium.com/mighty-bear-games/how-ux-design-can-help-with-vfx-a0925f80e0f0)
- [League of Legends VFX Style Guide (PDF)](https://nexus.leagueoflegends.com/wp-content/uploads/2017/10/VFX_Styleguide_final_public_hidpjqwx7lqyx0pjj3ss.pdf)
- [Patch 7.3 New Combat Animations: It's Magic — Blizzard](https://worldofwarcraft.blizzard.com/en-us/news/20946579/patch-7-3-new-combat-animations-it-s-magic)
- [Runewatch Dev Log #4 — IndieDB](https://www.indiedb.com/games/runewatch/news/runewatch-dev-log-4)
- [Wand spell timing critique — Animation Mentor](https://www.animationmentor.com/?p=90190)
