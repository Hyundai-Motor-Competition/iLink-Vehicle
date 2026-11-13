# 기획서 ↔ Swagger 차이 (서버 확인 필요)

기준: 기획서 v0.5 (2026.11.06), Swagger `Pickup Service API v1` (2026.11.10 조회)

기획서의 API 번호(API-01~25)와 Swagger의 ID(SES-, SIG-, CALL- 등)가 다르다. 클라이언트 코드는 Swagger ID를 주석으로 단다.

## 기획서에는 있는데 Swagger에 없는 것

| 기획서 | 내용 | 지금 클라이언트 처리 | 확인할 것 |
|---|---|---|---|
| API-04 recommend-spot | SG-01~12 통과 후보 중 비용순 추천 | SPOT-01 장소 목록에서 기본 장소(isDefault)를 먼저 보여줌. V02 디자인의 정차 예상·추천 이유는 `Spot.etaSeconds`·`Spot.reason`(nullable)로 받을 자리만 만들어 둠 | 추천 결과(추천 이유, 대안, 정차 예상)를 줄 API가 생기는지 |
| API-07 prediction | 최신 시간 예측 | SES-03 `eta` 사용 | P10/P90 같은 분포값을 줄 계획인지 |
| API-24 call-eligibility | 가드 G1~G5 · 발송 예정 | SES-03 `call.guard`, `call.scheduledAt` 사용 | `guard.unmet` 값 목록 (G1~G5 코드?) |
| API-08 child-events (READY) | 자녀 준비 완료 (EVT-06) | 없음. C02 "준비 완료" 버튼 동작 보류 | 준비 완료 신호를 보낼 API |
| F0-01 보호자 로그인 | 로그인·동의 | LINK-04 승인 응답 토큰을 로그인으로 사용 | 앱 재설치·폰 교체 시 보호자 재로그인 방법 (차량 RELINK만 가능?) |
| P03 후보 승인 | 검증된 후보 목록에서 승인 | SPOT-02로 정차·만남 좌표를 직접 추가 | 서버가 검증 후보를 내려주는지, 아니면 보호자가 직접 찍는 구조로 확정인지 |
| spotRecommendMode | 자동 추천 / 기본 장소 고정 | 필드 없음 | 저장 위치 |

## 값 정의가 다른 것

- `childPhase`: 기획서 WAITING/PREPARING/READY/CALLED/MEETING ↔ Swagger WAITING/CALLED/MEETING/NOT_BOARDED. 모바일은 세션 이벤트 `DEPARTURE_NOTICE_SENT`가 있으면 C02(준비)로 본다.
- `callStatus`: Swagger `SessionStatusResponse.call`이 enum 없는 string. 값 목록 필요 (NONE/ACCEPTED/SENT/DELIVERED/ACKNOWLEDGED/REVOKED로 가정).
- `sessionId`: 기획서 UUID ↔ Swagger int64. Swagger를 따른다.
- 오류 코드: 서버 `error.code`는 문자열(`LINK_CODE_INVALID` 등). 기획서 ERR-01~17 이름과 같은지 전체 목록 필요.

## 정해지지 않은 것

- 성공 응답 본문: 기획서는 `data, version, error` 공통 구조라고 했지만, Swagger 스키마는 본문을 바로 준다. 클라이언트는 Swagger를 따랐다. 오류는 `{ "error": { code, message, retryAfterSeconds } }`를 실제 응답으로 확인했다.
- SSE(SES-08)는 차량 토큰만 쓸 수 있다. 모바일은 폴링(5초) + 푸시 수신 시 재조회로 맞춘다. 이벤트 `data` 형식(전체 세션인지, 변경분인지) 필요.
- 푸시 payload: 만료 푸시를 거르려면(QA-21) `sessionId`, `stateVersion`, `spotVersion`이 들어 있어야 한다.
- 초대 링크(FAM-04 `inviteUrl`) 형식. 딥링크 스킴은 지금 `pickup://invite/{token}`으로 가정.
- `help-requests.type`, `incident.type`, `cancellation.reason` 값 목록.
- LINK-03 응답: 만료(410) · 없음(404) · 이미 연결(409)을 HTTP 코드로 주는지. 404는 실제로 확인했다.
- 텔레메트리 단위: Pleos 내비 `DrivingState.duration` / `distance` 단위를 초 · m로 가정. 에뮬레이터에서 확인 후 변환 필요.

## SPOT-04 장소 추천 (2026.11.13 추가)

- `GET /v1/pickup-spot-recommendations?academyId&origin=위도,경도` (차량 토큰 가능). 학원 근처 길가 정차 · 공영주차장 후보와 운전 · 걷는 시간.
- 결과에 spotId가 없어 차량이 바로 길안내 확정(SES-04)할 수 없다. 그래서 V02는 저장된 장소(SPOT-01)를 보여 주고, 같은 자리(50m 안) 추천 후보가 있으면 그 운전 · 걷는 시간을 쓴다. 후보를 새 장소로 저장하는 건 보호자 앱(SPOT-02)에서 한다.
- 2026.11.13부터 합류 안내는 정차(STOPPED) 뒤에만 나간다(가드 G6). 도착 3분 전에는 자녀에게 실내 준비 알림만 간다. 데모 서버 · 음성 안내(U04) · V03/V04 문구는 아직 이전 규칙(3분 전 발송) 기준이다.
