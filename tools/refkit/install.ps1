# refkit 자동 설치 (Windows). install.bat을 더블클릭하면 실행된다.
# 하는 일: Python(3.11+) 확인/설치 -> ffmpeg, yt-dlp, mpv 설치 -> refkit[pose] 설치 -> refkit doctor
$ErrorActionPreference = "Continue"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Refresh-Path {
    $m = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $u = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$m;$u"
}

function Step($msg) { Write-Host ""; Write-Host "== $msg ==" -ForegroundColor Cyan }

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host "winget이 없습니다. Microsoft Store에서 '앱 설치 관리자(App Installer)'를 설치한 뒤 다시 실행하세요." -ForegroundColor Red
    exit 1
}

function Winget-Install($id, $name) {
    Write-Host "$name 설치 중..."
    winget install --id $id --exact --silent --accept-package-agreements --accept-source-agreements | Out-Host
    return ($LASTEXITCODE -eq 0 -or $LASTEXITCODE -eq -1978335189 -or $LASTEXITCODE -eq -1978335135)  # 0 = 설치됨, 0x8A15002B/0x8A150061 = 이미 설치됨
}

# 1. Python 3.11 이상 찾기
Step "Python 확인"
function Find-Python {
    foreach ($cand in @("py -3", "python")) {
        $parts = $cand.Split(" ")
        $exe = $parts[0]
        $rest = @($parts | Select-Object -Skip 1)
        if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { continue }
        $ver = & $exe @rest -c "import sys; print('%d.%d' % sys.version_info[:2])" 2>$null
        if ($LASTEXITCODE -eq 0 -and $ver) {
            $v = "$ver".Trim().Split(".")
            if ([int]$v[0] -eq 3 -and [int]$v[1] -ge 11) { return $cand }
        }
    }
    return $null
}
$py = Find-Python
if (-not $py) {
    Winget-Install "Python.Python.3.12" "Python 3.12" | Out-Null
    Refresh-Path
    $py = Find-Python
}
if (-not $py) {
    Write-Host "Python 3.11 이상을 찾지 못했습니다. 이 창을 닫고 새로 연 뒤 install.bat을 다시 실행하세요." -ForegroundColor Red
    exit 1
}
$pyParts = $py.Split(" "); $pyExe = $pyParts[0]; $pyArgs = @($pyParts | Select-Object -Skip 1)
Write-Host ("사용할 Python: " + (& $pyExe @pyArgs -c "import sys; print(sys.executable, sys.version.split()[0])"))

# 2. 외부 프로그램
Step "ffmpeg / yt-dlp / mpv 설치"
if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) { Winget-Install "Gyan.FFmpeg" "ffmpeg" | Out-Null } else { Write-Host "ffmpeg: 이미 있음" }
if (-not (Get-Command yt-dlp -ErrorAction SilentlyContinue)) { Winget-Install "yt-dlp.yt-dlp" "yt-dlp" | Out-Null } else { Write-Host "yt-dlp: 이미 있음" }
if (-not (Get-Command mpv -ErrorAction SilentlyContinue)) {
    $ok = $false
    foreach ($id in @("shinchiro.mpv", "mpv.mpv", "mpv")) {
        if (Winget-Install $id "mpv ($id)") { $ok = $true; break }
    }
    if (-not $ok) { Write-Host "mpv 자동 설치 실패: 'winget search mpv'로 찾아 설치하세요. (없어도 'refkit mark --set'으로 시각 입력 가능)" -ForegroundColor Yellow }
} else { Write-Host "mpv: 이미 있음" }
Refresh-Path

# 3. refkit 설치 (자세 추정 포함)
Step "refkit 설치 (자세 추정 포함)"
& $pyExe @pyArgs -m pip install --upgrade pip | Out-Host
& $pyExe @pyArgs -m pip install -e "${here}[pose]" | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host "자세 추정 포함 설치 실패 -> 기본 기능만 설치합니다." -ForegroundColor Yellow
    & $pyExe @pyArgs -m pip install -e "$here" | Out-Host
}

# 4. 점검
Step "refkit doctor"
& $pyExe @pyArgs -m refkit doctor
Write-Host ""
Write-Host "완료. 새 터미널에서 'refkit doctor'가 안 되면 'py -m refkit doctor'를 쓰세요." -ForegroundColor Green
