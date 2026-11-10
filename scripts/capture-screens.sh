#!/usr/bin/env bash
# 에뮬레이터 없이 차량 앱 화면(V00~V07)을 PNG로 뽑는다.
# 결과: screenshots/{landscape,landscape-dark,portrait,design-light,design-dark}/*.png
set -euo pipefail
cd "$(dirname "$0")/.."
# 이 스크립트가 만드는 폴더만 지운다 (screenshots/pleos 는 run-pleos.sh 결과라 남김)
rm -rf screenshots/{landscape,landscape-dark,portrait,design-light,design-dark}
# --rerun: 코드가 그대로여도 매번 다시 그린다 (안 주면 Gradle이 up-to-date로 건너뜀)
./gradlew testDebugUnitTest --rerun -Pscreens --tests 'com.ilink.vehicle.screens.*' "$@"
echo
echo "저장한 화면:"
find screenshots -name '*.png' | sort
