#!/usr/bin/env bash
# 모드팩 서버 로딩 테스트: NeoForge 전용 서버에 모드팩을 설치하고 "Done (" 까지 뜨는지 확인한다.
# 사용법: bash tools/smoke_server.sh
# 종료 코드: 0 성공, 1 로딩 실패, 2 다운로드 실패(네트워크 허용 필요)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER="$ROOT/build/server"
LOG="$SERVER/smoke.log"
PACKWIZ="${PACKWIZ:-$HOME/go/bin/packwiz}"
TIMEOUT="${SMOKE_TIMEOUT:-600}"
PORT=8085
NEO_VERSION="$(python3 -c "import tomllib;print(tomllib.load(open('$ROOT/pack/pack.toml','rb'))['versions']['neoforge'])")"

mkdir -p "$SERVER"
cd "$SERVER"

download() { # url dest
  if ! curl -fsSL -o "$2" "$1"; then
    echo "다운로드 실패: $1" >&2
    echo "필요한 호스트: maven.neoforged.net, github.com, release-assets.githubusercontent.com, api.modrinth.com, cdn.modrinth.com, api.curseforge.com, edge.forgecdn.net, mediafilez.forgecdn.net, piston-meta.mojang.com, piston-data.mojang.com, libraries.minecraft.net" >&2
    exit 2
  fi
}

# 1. NeoForge 서버 설치 (버전이 바뀌면 다시 설치)
if [ ! -f "neoforge-$NEO_VERSION.installed" ]; then
  download "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO_VERSION/neoforge-$NEO_VERSION-installer.jar" installer.jar
  java -jar installer.jar --install-server . > install.log 2>&1 || { echo "NeoForge 설치 실패: $SERVER/install.log" >&2; exit 2; }
  rm -f installer.jar neoforge-*.installed
  touch "neoforge-$NEO_VERSION.installed"
fi

# 2. 모드팩 설치 (packwiz serve + packwiz-installer)
[ -f packwiz-installer-bootstrap.jar ] || download \
  "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar" \
  packwiz-installer-bootstrap.jar
(cd "$ROOT/pack" && "$PACKWIZ" refresh >/dev/null && exec "$PACKWIZ" serve --port "$PORT") > serve.log 2>&1 &
SERVE_PID=$!
trap 'kill $SERVE_PID 2>/dev/null || true' EXIT
for _ in $(seq 1 30); do curl -fs "http://localhost:$PORT/pack.toml" >/dev/null && break; sleep 1; done
# 이 환경처럼 api.github.com이 막힌 곳을 위해 본체를 미리 받고 자동 업데이트를 끈다
[ -f packwiz-installer.jar ] || download \
  "https://github.com/packwiz/packwiz-installer/releases/latest/download/packwiz-installer.jar" \
  packwiz-installer.jar
java -jar packwiz-installer-bootstrap.jar --bootstrap-no-update -g -s server "http://localhost:$PORT/pack.toml" > packwiz-install.log 2>&1 \
  || { echo "모드팩 설치 실패: $SERVER/packwiz-install.log" >&2; exit 1; }

# 3. 서버 설정
echo "eula=true" > eula.txt
echo "-Xmx6G" > user_jvm_args.txt
if [ ! -f server.properties ]; then
  printf 'online-mode=false\nlevel-seed=rpgsmoke\n' > server.properties
fi
rm -rf crash-reports

# 4. 실행
: > "$LOG"
mkfifo -m 600 stdin.fifo 2>/dev/null || true
exec 3<>stdin.fifo
./run.sh nogui <&3 > "$LOG" 2>&1 &
SRV_PID=$!
START=$(date +%s)
RESULT=1
while kill -0 $SRV_PID 2>/dev/null; do
  if grep -q 'Done (' "$LOG"; then RESULT=0; break; fi
  if [ $(( $(date +%s) - START )) -ge "$TIMEOUT" ]; then echo "시간 초과 (${TIMEOUT}초)" >&2; break; fi
  sleep 5
done
ELAPSED=$(( $(date +%s) - START ))
echo "stop" >&3
for _ in $(seq 1 60); do kill -0 $SRV_PID 2>/dev/null || break; sleep 1; done
kill $SRV_PID 2>/dev/null || true
exec 3>&-

if grep -q 'Missing or unsupported mandatory dependencies' "$LOG"; then RESULT=1; echo "필수 의존 모드 누락" >&2; fi
if [ -d crash-reports ] && [ -n "$(ls -A crash-reports)" ]; then RESULT=1; echo "크래시 리포트 생성됨: $SERVER/crash-reports" >&2; fi

if [ $RESULT -eq 0 ]; then
  echo "성공: 서버 로딩 ${ELAPSED}초, 모드 $(ls mods | wc -l)개"
else
  echo "실패: 로그 $LOG" >&2
fi
exit $RESULT
