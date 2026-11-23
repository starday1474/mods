# refkit 사용 안내

`refkit`은 동작 레퍼런스(영상 링크, 구간, 메모, 타이밍)를 모으고 우리 동작(`mod/anim/*.json`)과 비교하는 도구입니다. 코드는 `tools/refkit/`에 있고, 설계는 `docs/superpowers/specs/2026-11-23-reference-kit-design.md`에 있습니다.

## 1. Windows 설치

1. **Python 3.10 이상**을 설치합니다. 설치 화면에서 "Add python.exe to PATH"를 켭니다.
2. 외부 프로그램 세 개를 설치합니다. (PowerShell)

   ```powershell
   winget install Gyan.FFmpeg
   winget install yt-dlp.yt-dlp
   winget install mpv
   ```

   `mpv`를 못 찾으면 `winget search mpv`로 이름을 확인합니다. 설치 후 PowerShell을 새로 엽니다.
3. 저장소 루트에서 refkit을 설치합니다. 자세 추정(`pose`)까지 쓰려면 `[pose]`가 필요합니다.

   ```powershell
   pip install -e tools/refkit[pose]
   ```

   PowerShell에서 대괄호가 문제가 되면 `pip install -e "tools/refkit[pose]"`로 씁니다.
4. 점검합니다.

   ```powershell
   refkit doctor
   ```

   빠진 것은 ❌와 설치 명령으로 알려줍니다. `ffmpeg`만 필수이고 나머지는 해당 명령을 쓸 때만 필요합니다. 한글이나 공백이 든 경로(`C:\Users\홍길동\Videos\활 영상.mp4`)도 따옴표로 감싸면 됩니다.

`refkit` 명령이 안 잡히면 `python -m refkit ...`로도 같습니다.

## 2. 저장 위치

- **메타데이터**(링크, 구간, 태그, `take` 메모, 표시 시각, 자세 통계 숫자): `docs/refs/<동작>.yaml`. 저장소에 커밋합니다.
- **미디어**(영상, 이미지, 프레임, 랜드마크 원본, 보드): 라이브러리 `<라이브러리>/<동작>/<id>/`. 기본 위치는 `C:\Users\<이름>\refkit-library`이고, 환경변수 `REFKIT_LIBRARY`로 바꿉니다. 저장소에는 올리지 않습니다.

## 3. 기본 흐름: 궁수 `archer_charge_shot`

동작 이름은 `mod/anim/`의 파일 이름과 같게 씁니다(다르면 `--anim 이름`).

```powershell
# 1) 레퍼런스 넣기: 링크(YouTube 등) 또는 받아 둔 파일
refkit add "https://www.youtube.com/watch?v=XXXX" --motion archer_charge_shot --start 1:02 --end 1:08 --tags 실사,측면 --take "당김이 길고 놓을 때 짧다"
refkit import "C:\Users\홍길동\Videos\활 영상.mp4" --motion archer_charge_shot --start 0:01 --end 3.5 --tags 실사 --take "측면 촬영"
refkit list --motion archer_charge_shot        # id 확인 (video-01, own-01 ...)

# 2) 구간 자르기 + 슬로모션 + 컨택트 시트 (id는 list에서 확인)
refkit clip --motion archer_charge_shot --id own-01

# 3) 타이밍 표시 (mpv가 열림)
refkit mark --motion archer_charge_shot --id own-01
#    mpv 없이: refkit mark --motion archer_charge_shot --id own-01 --set start=0.2 impact=1.2 settle=2.3

# 4) 자세 추정 (실제 사람 영상만)
refkit pose --motion archer_charge_shot --id own-01

# 5) 타이밍 비교 (예비/회복 비율, 제안)
refkit timing --motion archer_charge_shot

# 6) 나란히 비교 영상
refkit compare --motion archer_charge_shot

# 7) 정리 보드를 브라우저로 열기
refkit board --motion archer_charge_shot --open
```

- 시간은 `62`, `1:02`, `1:02.5`, `0:01:02` 모두 됩니다. 끝이 시작보다 작거나 같으면 오류이고, 영상 길이를 넘으면 영상 끝까지로 자릅니다.
- 같은 url과 start로 `add`를 다시 하면 새로 만들지 않고 기존 항목을 갱신합니다.
- `fetch`(`refkit fetch --motion ...`)는 `tools/refkit/sources.yaml`이 허용한 출처(직접 가져온 파일, CMU 모션캡처, CC 라이선스 등)만 내려받습니다. 허용되지 않으면 링크로 남깁니다. YouTube는 링크 모드입니다.
- `pose`에서 사람이 잡힌 프레임이 50% 미만이면 경고합니다. 못 잡힌 프레임은 통계에서 빠집니다. `--use-suggested-marks`로 추천 표시를 쓸 수 있고(표시가 비어 있을 때만), `--no-overlay`로 오버레이 영상을 건너뜁니다.

### mpv 단축키 (`mark`)

영상은 0.5배속으로 반복 재생됩니다. 동작의 해당 순간에 멈추거나 재생 중에 누릅니다.

| 키 | 동작 |
|---|---|
| `1` | start (예비 동작 시작) |
| `2` | impact (타격/발사 순간) |
| `3` | settle (회복 끝, 정지) |
| `0` | 표시 모두 지우기 |

mpv를 닫으면 저장됩니다. 마지막으로 누른 값이 쓰이고, 순서가 `start < impact < settle`이 아니면 경고합니다. 표시를 하나도 안 했으면 저장하지 않습니다.

## 4. 지킬 것

1. **미디어는 라이브러리에만 둡니다.** 영상, 이미지, 프레임, 랜드마크는 저장소에 커밋하지 않습니다(`test_repo_guard`가 막습니다).
2. **`game` 태그 영상에는 자세 추정을 하지 않습니다.** 게임·애니메이션 클립은 거절됩니다. 실제 사람 영상에만 씁니다.
3. **결과로 동작 키를 만들지 않습니다.** refkit은 `mod/anim`을 읽기만 합니다. 키프레임으로 내보내는 기능은 없고, 자세 추정 결과는 각도와 타이밍 통계로만 남깁니다. 레퍼런스는 "무엇을 배웠는지(`take`)"를 적어 두고 우리 동작은 직접 만듭니다.

## 5. 클라우드 세션에서 할 수 있는 것

영상 재생과 `mpv`, 외부 다운로드가 없는 환경입니다.

- `add`: 링크와 메모 등록
- `list`: 목록 보기
- `timing`: 이미 `marks`가 있는 레퍼런스와 우리 동작 비교
- `board --share 파일.html`: 미디어 없이 링크, 메모, 표시, 통계만 담은 공유용 보드
- CMU 모션캡처 자료 정리(허용된 출처)

`mark`(mpv), `pose`, `clip`, `compare`는 로컬 Windows에서 합니다. 표시는 `mark --set start=... impact=... settle=...`로 숫자만 입력할 수도 있습니다.

## 6. 문제 해결

- 외부 프로그램이 없으면 설치 명령을 보여주고 종료 코드 2로 끝납니다. `refkit doctor`로 확인합니다.
- `compare`/`board`에서 우리 동작 미리보기가 실패하면 빌드된 `mod/src/main/resources/.../combat/<이름>.json`과 `.cache/jars`의 Epic Fight jar가 있는지 확인합니다.
- 콘솔에 글자가 깨져도(cp949) 오류로 멈추지 않고 `?`로 대체됩니다.
