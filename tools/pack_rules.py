"""모드팩 규칙. 설계 문서(docs/superpowers/specs/2026-11-16-rpg-mod-and-modpack-design.md)에서 가져온 값."""

MC_VERSION = "1.21.1"
MAX_MODS = 200

# 정규화된 이름(소문자, 영숫자만, &→and)의 앞부분 -> 넣지 않는 사유.
# 모드 이름이 이 키로 시작하면 걸린다 (예: ftbchunksneoforge).
DENIED: dict[str, str] = {
    "ironsspellsnspellbooks": "마법은 우리 모드가 담당 (사용자 결정)",
    "bettercombat": "Epic Fight와 충돌",
    "projectmmo": "레벨업은 우리 모드가 담당",
    "pmmo": "레벨업은 우리 모드가 담당",
    "puffish": "레벨업은 우리 모드가 담당 (Pufferfish's Skills와 애드온)",
    "pufferfishs": "레벨업은 우리 모드가 담당 (Pufferfish's Skills와 애드온)",
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

# 첫 실행 기본 설정 (config/yosbr/ 아래, 처음 한 번만 적용)
DEFAULT_OPTIONS: dict[str, str] = {
    "renderDistance": "10",
    "simulationDistance": "6",
    "entityDistanceScaling": "0.75",
    "particles": "1",  # 0=전부, 1=줄임, 2=최소
    "lang": "ko_kr",
}
KOREAN_RESOURCE_PACK = "file/rpg-korean"
