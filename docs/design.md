# 차량 앱 1차 설계

## 범위

차량 IVI 화면 V00~V07, 음성 안내 U01~U09, 차량 신호 전송(SIG-01·02). 합류 호출 시점(도착 3분 전·700m)과 가드 G1~G5 판정은 서버가 한다. 차량 앱은 신호를 보내고 서버 상태를 보여준다.

## 플랫폼

- Pleos Connect (Android Automotive OS), Activity + Jetpack Compose
- 빌드 설정은 Pleos 공식 샘플(`Pleos-Playground/pleos-sample-vehicle-app`)과 맞췄다: AGP 8.8.2, Kotlin 2.0.0, Gradle 8.10.2, JDK 17 타깃, compile/targetSdk 35
- minSdk 28 (공모전 기준)
- Pleos SDK (`https://nexus-playground.pleos.ai/repository/maven-releases/`)

| SDK | 버전 | 용도 |
|---|---|---|
| Vehicle | 2.3.2 | 도어 개폐(`registerDoorPosition`), 기어(`registerGearSelection`) |
| NaviHelper | 3.0.5 | 길안내 요청(`requestRoute`), 남은 시간·거리(`onDrivingInfo`) |
| FusedLocation | 1.1.3 | 위치·진행 방향 |
| TextToSpeech | 3.3.1 | U01~U09 발화 |
| SpeechToText | 3.3.1 | U08 음성 명령 (미구현, Playground 앱 등록 필요) |

주행 규제는 AAOS `CarUxRestrictionsManager`(`useLibrary("android.car")`)로 받는다.

## 패키지

```
com.ilink.vehicle
├─ AppContainer          수동 DI
├─ core/network          OkHttp 클라이언트, 401 시 refresh 1회, Idempotency-Key, 오류 본문 파싱
├─ core/auth             기기 토큰 / 차량 토큰 저장 (DataStore)
├─ data/model            Swagger DTO (차량이 쓰는 것만)
├─ data/remote           VehicleApi(REST), SessionStream(SSE)
├─ data/repository       LinkRepository(V00 연결), SessionRepository(세션 단일 출처)
├─ domain                SessionReducer, DoorCycleDetector, VehicleScreenResolver
├─ platform              Pleos SDK 래퍼 (vehicle, navi, location, voice, ux)
├─ service               PickupSessionService (포그라운드, 텔레메트리·차량 이벤트)
└─ ui                    MainViewModel, AppRoot, screen/V00~V07
```

## 흐름

**연결 (V00)**
AUTH-02 기기 등록 → 기기 토큰 → LINK-01 코드 발급(QR `pickup://link?code=`) → LINK-02 상태 폴링(`pollAfterSeconds`, 기본 2초) → LINKED면 차량 access/refresh 토큰 저장 → V01

**픽업 (V01 → V05)**
1. V01: SET-09 주간 일정, `readiness.startable`이 false면 시작 버튼 비활성
2. 픽업 시작: SES-02 (같은 일정의 진행 중 세션이면 서버가 그 세션을 돌려줌) → SSE 구독 + 포그라운드 서비스 시작
3. V02: 장소 선택 → NaviHelper 길안내 → `onRouteStarted`를 받은 뒤에만 SES-04 경로 확정. 실패하면 장소를 확정하지 않는다 (ERR-03)
4. V03/V04: 서비스가 15초마다 SIG-01(ETA·남은 거리·위치·기어) 전송. 서버가 호출을 만들면 SSE로 받아 V04
5. 정차: 기어 P 전환 시 SIG-02 STOPPED, P에서 벗어나면 DEPARTED
6. V05: 정차 중 운전석 외 도어 열림→닫힘 → DOOR_CYCLE. 판정·취소 창(10초)·COMPLETED는 서버. 취소는 SES-12, 도어 신호가 없으면 SES-11 MANUAL

화면은 사용자가 넘기지 않고 세션 상태로 정한다 (`VehicleScreenResolver`).

## 상태 동기화

- 모든 세션 응답은 `SessionReducer.merge`를 거친다. 낮은 `stateVersion`은 버리고, COMPLETED/CANCELLED 이후 응답은 무시한다 (F4-04, F4-07, QA-18)
- SSE 이벤트를 받으면 SES-03으로 전체 세션을 다시 받는다. 이벤트 payload 형식이 확정되면 바꿀 수 있다
- SSE가 끊기면 SES-03 재조회 후 `Last-Event-ID`로 재구독, 지수 백오프(최대 30초)
- 409는 최신 세션을 다시 받은 뒤 오류를 보여준다
- 회전·창 크기 변경에도 상태는 `SessionRepository`에 있어 유지된다 (QA-26)

## 주행 규제 (Pleos 가이드)

- `screenOrientation` 지정 안 함: Portrait 1창 / Landscape 2창 모두 대응. `AdaptiveScreen`이 창 비율로 보조 정보 영역을 붙인다
- Activity에 `distractionOptimized` 메타데이터 선언. 실제 선언 방식은 Pleos 심사 기준 확인 필요
- 규제 중: V00·V07 QR 숨김, V05 수동 완료 비활성, 텍스트 입력 없음. 주행 중 조작은 '합류 안내 보류' 1탭만
- 음성: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`를 얻었을 때만 1회 발화, 못 얻으면 배너만. 같은 사건은 한 번만

## 도어 판정 (DoorCycleDetector)

- 운전석(1열 좌측) 제외, 정차(기어 P) 중만
- 열림 → 닫힘 후 3초 동안 다시 열리지 않으면 1회 전송. 여러 번 여닫으면 마지막 닫힘 기준
- 출발하면 판정 초기화
- 도어 position 값 0 = 닫힘으로 가정. 에뮬레이터에서 확인 필요

## 남은 일

- [ ] Figma 기준 화면 구현 (지금은 정보 배치만 있는 골격)
- [ ] U01~U09 발화 문구·트리거 연결 (SSE 이벤트 타입 → VoiceCue 매핑)
- [ ] U08 음성 명령 (STT, Playground My Project 등록)
- [ ] 차량 푸시 토큰(AUTH-03): 차량에서 쓸 푸시 수단 확인
- [ ] V06 직접 연락(SES-10 연락처) 화면
- [ ] 보호자 확인형(GUARDIAN_CONFIRM) 가족의 CALL-02 수동 발송 버튼
- [ ] Pleos Connect 에뮬레이터에서 도어·기어·내비 값 확인, 폼팩터 테스트(12.9" Portrait / 14.6" Landscape)
- [ ] 앱 아이콘 교체 (지금은 기본 아이콘)
