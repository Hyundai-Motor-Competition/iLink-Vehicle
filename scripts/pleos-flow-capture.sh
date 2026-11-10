#!/usr/bin/env bash
# 켜져 있는 Pleos 에뮬레이터에서 데모 앱을 처음부터 돌리며 V00→V01→V02→V03을 라이트·다크로 캡처한다.
#   scripts/run-pleos.sh --headless 로 에뮬레이터를 먼저 띄운 뒤 실행
# 결과: screenshots/pleos/{light,dark}_V0x.png
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
PKG=com.ilink.vehicle.demo
OUT=screenshots/pleos
mkdir -p "$OUT"

# 화면에서 글자로 버튼을 찾아 누른다 (uiautomator 덤프의 bounds 가운데)
tap_text() {
  "$ADB" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  local b
  b=$("$ADB" shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" | head -1 | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -z "$b" ]; then echo "  '$1' 버튼을 못 찾았어요"; return 1; fi
  set -- $b
  "$ADB" shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}

# 화면에 글자가 나올 때까지 기다린다 (최대 30초)
wait_text() {
  for _ in $(seq 1 30); do
    "$ADB" shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
    "$ADB" shell cat /sdcard/ui.xml | grep -qF "$1" && return 0
    sleep 1
  done
  echo "  '$1' 화면이 안 나왔어요"; return 1
}

shot() { "$ADB" exec-out screencap -p > "$OUT/$1.png"; echo "  $OUT/$1.png"; }

for mode in light dark; do
  echo "[$mode]"
  "$ADB" shell cmd uimode night "$([ $mode = dark ] && echo yes || echo no)" > /dev/null
  "$ADB" shell am force-stop --user current $PKG
  "$ADB" shell pm clear --user current $PKG > /dev/null
  # 캡처 중 권한 창이 뜨지 않게 위치 권한을 미리 준다 (앱은 처음 실행 때 직접 요청한다)
  for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION; do "$ADB" shell pm grant --user current $PKG android.permission.$perm; done
  "$ADB" shell am start -n $PKG/com.ilink.vehicle.MainActivity > /dev/null
  wait_text "스마트폰으로 QR 코드를 스캔해 주세요"; sleep 1; shot "${mode}_V00"
  wait_text "오늘의 픽업"; sleep 1; shot "${mode}_V01"     # 데모 서버가 약 12초 뒤 자동 승인
  tap_text "18:10 김현지 픽업 시작"
  wait_text "이 장소로 길안내"; sleep 1; shot "${mode}_V02"
  tap_text "이 장소로 길안내"
  wait_text "픽업 진행 중"; sleep 3; shot "${mode}_V03"
done
"$ADB" shell cmd uimode night no > /dev/null
