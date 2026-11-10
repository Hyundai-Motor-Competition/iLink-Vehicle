#!/usr/bin/env bash
# Pleos 에뮬레이터 없이 데모 빌드를 가벼운 Automotive 에뮬레이터에서 실행한다.
#   scripts/run-demo.sh            창을 띄워 실행
#   scripts/run-demo.sh --headless 창 없이 실행 (adb로 캡처할 때)
# 처음 한 번은 AVD(ilink_demo_auto, Android 13 Automotive, 1280x720, RAM 2GB)를 만든다.
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
AVD=ilink_demo_auto
IMAGE="system-images/android-33/android-automotive/arm64-v8a/"

if [ ! -d "$SDK/$IMAGE" ]; then
  echo "Automotive 이미지가 없어요. Android Studio > SDK Manager에서 'Android Automotive ... arm64-v8a (API 33)'를 설치하세요."
  exit 1
fi

if [ ! -f "$HOME/.android/avd/$AVD.ini" ]; then
  mkdir -p "$HOME/.android/avd/$AVD.avd"
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=android-33\n' "$HOME/.android/avd/$AVD.avd" "$AVD" > "$HOME/.android/avd/$AVD.ini"
  cat > "$HOME/.android/avd/$AVD.avd/config.ini" <<CFG
avd.ini.encoding=UTF-8
AvdId=$AVD
avd.ini.displayname=iLink 데모 (Automotive 1280x720)
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=2
image.sysdir.1=$IMAGE
tag.id=android-automotive
hw.lcd.width=1280
hw.lcd.height=720
hw.lcd.density=160
hw.ramSize=2048
disk.dataPartition.size=4G
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.audioInput=no
hw.audioOutput=no
CFG
  echo "AVD $AVD 를 만들었어요."
fi

if ! "$ADB" devices | grep -q emulator-; then
  FLAGS=(-avd "$AVD" -no-audio -no-boot-anim -timezone Asia/Seoul)
  [ "${1:-}" = "--headless" ] && FLAGS+=(-no-window)
  "$SDK/emulator/emulator" "${FLAGS[@]}" > /tmp/ilink-demo-emulator.log 2>&1 &
  echo "에뮬레이터 부팅 중..."
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

./gradlew installDemo
"$ADB" shell am start -n com.ilink.vehicle.demo/com.ilink.vehicle.MainActivity
echo "실행했어요. 오른쪽 위 DEMO 버튼으로 주행 규제·도어 신호·바로 도착을 바꿀 수 있어요."
