# 캐릭터 만들기 — 무기 카드와 재각성의 결정

0.3.0에 들어간 문구를 그대로 옮긴 것입니다. 카드 이름은 하위 유형(subtype)마다 하나씩, 설명은 두 줄입니다.
(언어 키: `goddessrpg.create.weapon.<유형>`, `goddessrpg.create.desc.<유형>.1/.2`)

## 무기 카드 8장

| 유형 | 한국어 이름 | 한국어 설명 | English name | English description |
|---|---|---|---|---|
| balanced | 한손검과 방패 | 방패로 막고, 빈틈에 검을 꽂는다. / 어떤 싸움에도 흔들리지 않는 만능 전사. | Sword & Shield | Block with the shield, then drive the blade into the gap. / A do-it-all warrior nothing can shake. |
| tank | 방패와 둔기 | 거대한 방패로 적의 시선을 끌어모은다. / 동료 앞을 막아서는 움직이는 성벽. | Shield & Mace | Draw every enemy's eye with a massive shield. / A walking fortress in front of your friends. |
| infight | 대검과 쌍검 | 크게 휘두르고 끝까지 몰아친다. / 위험할수록 불타오르는 돌격 전사. | Greatsword / Dual Blades | Swing big and never stop pressing. / A berserker who burns brighter the closer danger gets. |
| archer | 장궁 | 멀리서 한 발로 급소를 꿰뚫는다. / 바람을 읽고 시위를 당기는 사냥꾼. | Longbow | Pierce a weak spot from afar with a single shot. / A hunter who reads the wind before the draw. |
| elementalist | 지팡이 | 불과 얼음과 번개를 한꺼번에 쏟아붓는다. / 적이 몰려올수록 신나는 광역 마법사. | Staff | Pour down fire, ice and lightning all at once. / A mage who grins when the horde rushes in. |
| summoner | 소환석 | 돌에 잠든 짐승을 깨워 함께 싸운다. / 혼자서도 무리를 이끄는 지휘자. | Summon Stone | Wake the beasts sleeping in the stone and fight side by side. / A commander who leads a pack even alone. |
| healer | 성표 | 치유의 정령을 불러 동료를 일으킨다. / 파티가 쓰러지지 않게 지키는 빛. | Holy Symbol | Call healing spirits to pull your friends back up. / The light that keeps the party standing. |
| relicist | 유물(반지·바늘) | 여러 유물을 갈아 끼우며 신기한 힘을 부린다. / 상황마다 해답을 꺼내는 재주꾼. | Relics (Rings & Needles) | Swap between relics and wield their strange powers. / A trickster with an answer for every situation. |

지금 고를 수 있는 카드는 한손검과 방패(balanced)뿐이고, 나머지는 "준비 중"으로 어둡게 보입니다 (서버 구성에 따라 다름).

## 재각성의 결정 (`goddessrpg:reawakening_crystal`)

- 제작법 (모양 있는 제작): 위·아래 줄이 `다이아몬드 블록, 에코 조각, 다이아몬드 블록`, 가운데 줄이 `에코 조각, 네더의 별, 에코 조각`.
- 사용: 우클릭 한 번은 확인 안내만 띄우고, 5초(100틱) 안에 한 번 더 우클릭하면 결정을 1개 쓰고 캐릭터를 초기화한 뒤 캐릭터 만들기 화면이 다시 열립니다. 숙련도·배운 스킬·슬롯·쿨타임은 지워지고, 서와 지식은 남습니다. 캐릭터가 없으면 쓸 수 없습니다.
- 관리자용 재선택의 깃은 결정과 별개로 관리자만 쓸 수 있습니다.

사용자 검토 필요
