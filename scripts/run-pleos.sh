#!/usr/bin/env bash
# 실제 Pleos Connect 에뮬레이터(v2.1.0, 2560x1440) 화면에서 앱을 본다. 가볍게 띄우도록 RAM 4GB로 만든다.
#   scripts/run-pleos.sh               데모 빌드(가짜 서버·차량)를 창으로
#   scripts/run-pleos.sh --headless    창 없이 띄우고 화면을 screenshots/pleos/*.png로 저장
#   scripts/run-pleos.sh --real        실제 빌드(서버 · Pleos SDK)를 설치
# 무거우면: iOS 시뮬레이터·다른 에뮬레이터를 끄고 실행하세요. 두 번째부터는 빠른 부팅으로 켜집니다.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD=ilink_pleos
# Apple Silicon은 arm64-v8a, Windows · Intel은 x86_64 이미지
ABI=$( [ "$(uname -m)" = arm64 ] && echo arm64-v8a || echo x86_64 )
IMAGE="system-images/android-34/pleos_car_emulator/$ABI/"
HEADLESS=no; VARIANT=Demo; PKG=com.ilink.vehicle.demo
for a in "$@"; do
  case "$a" in
    --headless) HEADLESS=yes ;;
    --real) VARIANT=Debug; PKG=com.ilink.vehicle ;;
  esac
done

if [ ! -d "$SDK/$IMAGE" ]; then
  echo "Pleos 이미지가 없어요. README의 '실행 방법'대로 SDK Manager에서 Pleos Connect System Image를 설치하세요."
  exit 1
fi

if [ ! -f "$HOME/.android/avd/$AVD.ini" ]; then
  mkdir -p "$HOME/.android/avd/$AVD.avd"
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=android-34\n' "$HOME/.android/avd/$AVD.avd" "$AVD" > "$HOME/.android/avd/$AVD.ini"
  cat > "$HOME/.android/avd/$AVD.avd/config.ini" <<CFG
avd.ini.encoding=UTF-8
AvdId=$AVD
avd.ini.displayname=Pleos Connect v2.1.0 (iLink)
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=4
image.sysdir.1=$IMAGE
tag.id=android-automotive
hw.device.name=Pleos_Connect_v2_0
hw.device.manufacturer=42dot
hw.lcd.width=2560
hw.lcd.height=1440
hw.lcd.density=160
hw.ramSize=4096
vm.heapSize=512
disk.dataPartition.size=6G
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.audioInput=no
hw.audioOutput=no
CFG
  echo "AVD $AVD 를 만들었어요 (RAM 4GB, 기본 8GB에서 줄임)."
fi

if ! "$ADB" devices | grep -q emulator-; then
  FLAGS=(-avd "$AVD" -no-audio -no-boot-anim -timezone Asia/Seoul)
  [ "$HEADLESS" = yes ] && FLAGS+=(-no-window)
  "$SDK/emulator/emulator" "${FLAGS[@]}" > /tmp/ilink-pleos-emulator.log 2>&1 &
  echo "Pleos 에뮬레이터 부팅 중..."
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

./gradlew "install$VARIANT"
"$ADB" shell am start -n "$PKG/com.ilink.vehicle.MainActivity"

if [ "$HEADLESS" = yes ]; then
  mkdir -p screenshots/pleos
  for mode in no yes; do
    "$ADB" shell cmd uimode night "$mode" > /dev/null
    sleep 3
    name=$([ "$mode" = yes ] && echo dark || echo light)
    "$ADB" exec-out screencap -p > "screenshots/pleos/now_$name.png"
  done
  "$ADB" shell cmd uimode night no > /dev/null
  echo "저장: screenshots/pleos/now_light.png, now_dark.png"
  echo "끄기: $ADB emu kill"
else
  echo "실행했어요. 라이트/다크 전환: $ADB shell cmd uimode night yes|no"
fi
