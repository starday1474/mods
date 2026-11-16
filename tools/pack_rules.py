"""모드팩 규칙. 설계 문서(docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md)에서 가져온 값."""

MC_VERSION = "1.21.1"
MAX_MODS = 200

# 정규화된 이름(소문자, 영숫자만) -> 넣지 않는 사유
DENIED: dict[str, str] = {
    "ironsspellsnspellbooks": "마법은 우리 모드가 담당 (사용자 결정)",
    "bettercombat": "Epic Fight와 충돌",
    "projectmmo": "레벨업은 우리 모드가 담당",
    "pmmo": "레벨업은 우리 모드가 담당",
    "puffishskills": "레벨업은 우리 모드가 담당",
    "pufferfishsskills": "레벨업은 우리 모드가 담당",
    "aether": "사용자 결정으로 제외",
    "thetwilightforest": "사용자 결정으로 제외",
    "twilightforest": "사용자 결정으로 제외",
    "worldbosses": "사용자 결정으로 제외",
    "ultimatebosses": "사용자 결정으로 제외",
    "dimensionsanddungeons": "사용자 결정으로 제외",
    "thebumblezone": "사용자 결정으로 제외",
    "bumblezone": "사용자 결정으로 제외",
    "adventofascension": "사용자 결정으로 제외",
    "c2me": "NeoForge판 알파",
    "kryptonreno": "1.21.1 지원 미확인",
    "xaerosminimap": "지도 모드 사용 안 함",
    "xaerosworldmap": "지도 모드 사용 안 함",
    "journeymap": "지도 모드 사용 안 함",
    "ftbchunks": "지도 모드 사용 안 함",
}

REQUIRED: set[str] = {
    "epicfight",
    "essential",
    "sodium",
    "lithium",
    "ferritecore",
    "modernfix",
    "lenderscataclysm",
    "curios",
    "geckolib",
    "kubejs",
    "yosbrneo",
}

CLIENT_ONLY: set[str] = {
    "sodium",
    "iris",
    "immediatelyfast",
    "entityculling",
    "distanthorizons",
}
