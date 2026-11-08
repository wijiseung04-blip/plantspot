# CLAUDE.md — plantspot (식물 놓을 자리 추천 서비스)

이 파일은 Claude Code가 프로젝트 맥락을 이해하기 위한 안내서다. 작업 전에 끝까지 읽을 것.
사용자는 한국어로 소통하고, 코드 주석·화면 문구·커밋 메시지도 한국어를 쓴다.

---

## 1. 프로젝트 개요

- **과목**: 2026-2학기 웹 프로그래밍 기말 프로젝트 (동아대학교)
- **마감**: 2026-12-18 (구두 발표 포함)
- **서비스 한 문장**: 공공데이터로 우리 방 햇빛을 계절별로 계산하고, 창가 아두이노 센서로 실제 햇빛을 측정해 보정해서, 식물이 가장 잘 자랄 자리를 알려주는 웹 서비스
- **시연 범위**: 부산광역시 사하구 (건물 데이터 41,271동 확보)
- **발표 핵심 문장**: "계산만 하면 앞 건물과 나무를 모르고, 측정만 하면 다른 계절을 모릅니다. 공공데이터로 1년을 예측하고, 센서로 우리 집 현실에 맞춥니다."

## 2. 평가 기준 (설계 판단의 기준)

**필수 기능**
- 로그인·회원가입, **회원 / 비회원 / 관리자** 권한 구분
- **파일 업로드·다운로드**
- 3계층 구조: Controller → Service → Repository(JPA) → DB
- REST 설계(자원 중심 URL), Bean Validation, 공통 예외 처리, 로그
- 다국어 처리는 옵션
- DB 제출: 원래 기준은 `*.db` 파일 → **MySQL로 바꿨으므로 `mysqldump`로 만든 `.sql` 파일로 제출 가능한지 교수님께 확인할 것**

**추가 점수 (가장 중요)**
- **① DB 데이터 생성 프로그램 (20~30점)**: 센서 데이터 저장, 인터넷 공개 데이터를 원하는 형태의 DB로 전환, AI 등으로 분석 후 저장. 랜덤 데이터는 목적이 있을 때만 인정
- **② DB와 무관한 추가 프로그램 (20점)**: 브라우저에서 실행되는 게임·에디터·컴파일러 연결 등. **웹사이트 기본 로직(조회·계산 화면)은 대상 아님** → 독립된 브라우저 프로그램이어야 함
- 가치(상업적·학술적) 5점, 완성도 5점

**설계 원칙**: 모든 기능은 위 항목 중 어디에 해당하는지 설명할 수 있어야 한다.

## 3. 기술 환경과 구조 (backend / frontend 분리)

- **Spring Boot 4.1.x** (Spring Framework 7, Spring Security 7, Hibernate 7), **Java 26** (Gradle toolchain, JDK 26이 없으면 `-PjavaVersion=25`)
- **Gradle 멀티 프로젝트**: 루트 `settings.gradle` 이 `backend`, `frontend` 를 포함. 반드시 루트의 `gradlew` 사용
- IDE: **이클립스**(Buildship) — 루트 폴더를 Gradle 프로젝트로 가져오면 plantspot / backend / frontend 세 프로젝트가 보인다
- DB: **MySQL 8** (`plantspot` 데이터베이스, utf8mb4). 테스트는 H2(MySQL 호환 모드) 메모리 DB

```
plantspot/
├── settings.gradle, build.gradle(플러그인 버전만), gradlew
├── backend/   (포트 8081) REST API 전용 — Controller → Service → Repository(JPA) → MySQL
│   ├── sql/create-database.sql      처음 한 번: DB·계정 만들기
│   └── src/main/resources/application.properties (+ application-local.properties: DB 비밀번호·API 키, 커밋 금지)
└── frontend/  (포트 8080) 화면 전용 — Thymeleaf 템플릿 + 정적 파일(js, css, editor), DB·보안 없음
    ├── PageController     화면 주소 → 템플릿
    ├── ApiProxyController 브라우저의 /api/** 를 backend로 그대로 전달 (쿠키·파일 포함)
    └── LoginInterceptor   backend /api/members/me 로 로그인 확인 → /my/** 회원, /admin/** 관리자
```

- 브라우저는 **frontend(8080)만** 본다. 데이터는 화면 스크립트가 `/api/**` 로 요청 → frontend가 backend로 전달
- 로그인: `POST /api/auth/login` (JSON, 세션 쿠키), 가입 `POST /api/auth/signup`, 로그아웃 `POST /api/auth/logout`
- CSRF: backend가 `XSRF-TOKEN` 쿠키 사용, 화면 `static/js/api.js` 가 `GET /api/auth/csrf` 로 토큰을 받아 헤더에 붙임
- 실제 데이터 보호(권한)는 backend가 한다. frontend의 LoginInterceptor는 화면 안내용

**실행 순서**: MySQL 켜기 → backend 실행(8081) → frontend 실행(8080) → 브라우저 http://localhost:8080

**Spring Boot 4 주의사항**
- 웹 스타터는 `spring-boot-starter-webmvc` (`starter-web` 아님), 테스트 스타터는 기능별(`webmvc-test` 등), `@MockBean` → `@MockitoBean`
- Jackson 3 (`tools.jackson.*`)

## 4. 현재 코드 상태 (아두이노 전까지 구현, backend/frontend 분리 완료)

빌드·테스트: `./gradlew build` (backend 단위·API 테스트 + frontend 화면 테스트). MySQL 실제 연결·두 서버 함께 실행해 브라우저로도 확인함.

```
backend/src/main/java/com/plantspot
├── config/        SecurityConfig(웹 전용: 비회원/회원/관리자, 401·403 JSON, CSRF 쿠키), PasswordConfig
├── common/        NotFound/Forbidden/Conflict/Unavailable 예외, GlobalExceptionHandler, ErrorResponse
├── member/        회원가입·로그인, Role(USER/ADMIN), AdminInitializer(app.admin.password 로 첫 관리자)
├── sun/           SolarCalculator(NOAA), SunService
├── geo/           TmConverter(EPSG:5186↔위경도), Wkt
├── building/      Building(칸 building_usage — USAGE는 MySQL 예약어), HeightRule, BuildingService
├── house/         House, Room, RoomWindow(테이블 room_window — WINDOW는 예약어), HouseEvents
├── obstruction/   ObstructionCalculator, obstruction_angle(BUILDING/SENSOR)
├── sunlight/      SunlightCalculator, sunlight_cell / sunlight_summary
├── plant/         농사로 가져오기, PlantConverter(변환표), plant_species
├── weather/       sun_event, weather_hour, 맑은 시간·자연광 판단
├── recommend/     PlantScorer, recommendation, my_plant(알림), PreviewService(비회원 간단 예측)
├── file/          upload_file + FileStorage(backend/data/uploads)
├── report/        리포트 PDF(PDFBox + 한글 글꼴), 배치도 PNG, CSV
├── tool/          BuildingImporter(GeoPackage → MySQL), GpkgGeometryReader
└── web/           REST 컨트롤러 (AuthApiController, AdminApiController 포함)
frontend/src/main/resources
├── templates/     화면 (fragments/layout.html 머리글은 model 'me' 로 로그인 표시)
└── static/        css, js/api.js, js/roomplan.js, editor/ (② 평면도 햇빛 에디터)
frontend/src/test/js/editor-parity.js   브라우저 JS와 서버 계산 일치 확인 (node 로 실행)
```

**데이터 생성 프로그램 (① — 웹 서버 없이 실행 후 종료, DB 설정은 backend의 properties)**
- `./gradlew :backend:importBuildings -Pgpkg=경로` / `./gradlew :backend:importPlants` / `./gradlew :backend:collectWeather -Pfrom= -Pto=`

**주의할 점 (작업하며 확인한 것)**
- 벌크 delete 쿼리 대신 엔티티 단위 삭제(`deleteAll(find...)`) 사용 중 (SQLite 시절 이유였지만 영속성 컨텍스트와 어긋나지 않아 그대로 둠)
- 계산 결과(가림 각도·칸별 햇빛·추천)는 HouseEvents/ObstructionChanged/PlantEvents 로 낡으면 지우고 다음 조회 때 다시 계산
- Thymeleaf `<script>` 안의 `[(`, `[[` 는 인라인 문법 → 스크립트에 `th:inline="none"`
- frontend는 multipart를 해석하지 않는다(`spring.servlet.multipart.enabled=false`) — 업로드 본문을 그대로 backend에 넘기기 위해
- 건물을 다시 가져올 때 house.building_id 를 끊었다가 동·지번으로 다시 연결한다(외래 키 때문)
- PDF 한글 글꼴: `app.report.font`(없으면 윈도 맑은 고딕 → 맥 → 리눅스 나눔·Noto 순)

**검증된 기준값** (테스트 기대값으로 사용 중)
- 부산(35.10N, 128.97E) 정오 최고 고도: 춘분 54.7°, 하지 78.3°, 동지 31.5°, 방위각 약 180°
- 2026-11-08 부산 일출 계산값 약 06:52
- EPSG:5186 (200000, 600000) → (38.0, 127.0)
- 하단동 1160-6 중심점 (378731.47, 281659.43) → (35.1154, 128.9605), 면적 약 300㎡, 높이 18.75m(MEASURED)
- 사하구 가져오기 결과: MEASURED 15,360 / FLOOR_ESTIMATE 9,444 / DEFAULT 16,467 (합계 41,271) — 실제 파일로 재확인 필요

## 5. 데이터 소스

API 키·DB 비밀번호는 `backend/src/main/resources/application-local.properties`에 둔다 (`.gitignore` 대상, 절대 커밋 금지, 예시는 같은 폴더의 `.example`).
`application.properties`에서 `spring.config.import=optional:classpath:application-local.properties`로 읽는다.

| 데이터 | 방식 | 상태 | 키 이름 |
|---|---|---|---|
| 국토부 GIS 건물통합정보 | 파일(브이월드), SHP → QGIS에서 사하구만 GeoPackage로 저장 | 확보 | - |
| 한국천문연구원 출몰시각 | 공공데이터포털 API, XML | 키 발급 완료 | `api.kasi.service-key` |
| 기상청 ASOS 시간자료 | 공공데이터포털 API, JSON/XML | 키 발급 완료 | `api.kma.service-key`, `api.kma.station-id` |
| 농촌진흥청 실내정원용 식물 | 농사로 Open API, XML | **키 승인 대기** | `api.nongsaro.api-key` |
| 브이월드 주소 검색 | API | 키 발급(선택 기능) | `api.vworld.key` |

### 5-1. GIS 건물통합정보
- 좌표계 **EPSG:5186** (Korea 2000 / Central Belt 2010, 미터). 거리 계산은 이 좌표 그대로 사용
- 원본 인코딩 EUC-KR (QGIS 저장본은 UTF-8)
- 칸 이름이 A0~A28 (추정 의미, 공식 정의서로 확인 필요): A2 PNU, A3 법정동코드(앞 2자리 26=부산), **A4 법정동명**("부산광역시 사하구 하단동"), **A5 지번**, **A9 용도**, A13 사용승인일, **A16 높이(m)**, **A24 건물명**(비어 있는 경우 많음), A25 동명, **A26 지상층수**, A27 지하층수
- 사하구 품질: 높이 있음 38%, 층수 있음 60%, 둘 다 없음 40%(대부분 건축물대장 미연계 소형 건물, 면적 가운데값 33㎡). **10층 이상 721동 중 93%는 높이 있음**
- 입력 오류 존재(예: 높이 15,400m)
- 파일명 규칙: `AL_D010_26_YYYYMMDD` = 부산 전체, `CH_...` = 일별 변경분(U/I/D)

**HeightRule (구현됨)**
- 높이 > 0 이고 ≤ 200m 이고 (층수 있으면) 높이/층수 ≤ 10m → 그대로 (MEASURED)
- 아니고 층수 있으면 → 층수 × 층고 (공동주택 3.0, 단독주택 3.5, 그 외 3.6) (FLOOR_ESTIMATE)
- 둘 다 없으면 → 3.5m (DEFAULT)

**사용자 집 위치 찾기**: 브이월드 대신 **동 선택 + 지번 입력 → building 테이블에서 검색 → 중심점 사용** 방식으로 결정 (건물명 검색은 건물명이 비어 있는 경우가 많아 부적합)

### 5-2. 한국천문연구원 출몰시각
- 지역별/위치별 조회, 응답: 일출·일몰, **시민박명(아침·저녁)**, 항해박명, 천문박명
- 용도: 박명 시작 전·종료 후의 밝아짐은 인공조명으로 판단해 센서 분석에서 제외, 해 위치 계산 검증

### 5-3. 기상청 ASOS 시간자료
- 시간별 **일조(ss)**, 일사(icsr), 전운량(dc10Tca), 일조 품질검사(ssQcflag)
- **전일(D-1)까지만 제공** → 측정 다음 날 분석
- 부산 지점 번호(`stnIds`)는 활용가이드에서 확인 필요
- 용도: 일조 ≈ 0인 시간은 흐림으로 제외 / 일조가 있는데 창틀 센서가 어두우면 가림으로 판단

### 5-4. 농사로 실내정원용 식물 (매뉴얼 확인 완료)
- 기본 주소 `http://api.nongsaro.go.kr/service/garden/{operation}` (**http**), 모든 요청에 `apiKey`
- XML, UTF-8, 성공 시 `resultCode` = `00`, 목록은 `pageNo`·`numOfRows`·`totalCount`
- `gardenList`: 목록(cntntsNo, cntntsSj 식물명, 썸네일 등), 필터 `lightChkVal=055001,...`
- `gardenDtl?cntntsNo=`: 상세
- 사진 주소: `http://www.nongsaro.go.kr/` + rtnFileCours + `/` + rtnThumbFileNm (여러 장은 `|`로 구분, 첫 번째 사용). `rtnThumbFileUrl`이 있으면 그 값 우선
- **광요구도 `lighttdemanddoCode`** (쉼표로 여러 개 가능): 055001 낮은 광도 300~800 lux / 055002 중간 800~1,500 / 055003 높은 1,500~10,000
- **배치장소 `postngplaceCode`** (창에서의 거리!): 064005 발코니 창측 0~50cm / 064004 발코니 내측 50~150 / 064003 거실 창측 150~300 / 064002 거실 내측 300~500 / 064001 실내 어두운 곳 500+ / 064006 습한 곳 / 064007 넓은 곳 / 064008 좁은 곳
- 겨울 최저온도 `winterLwetTpCode`: 057001 0℃ 이하 / 057002 5℃ / 057003 7℃ / 057004 10℃ / 057005 13℃ 이상
- 생육 온도 `grwhTpCode`: 082001 10~15℃ / 082002 16~20 / 082003 21~25 / 082004 26~30
- 습도 `hdCode`: 083001 40% 미만 / 083002 40~70% / 083003 70% 이상
- 관리 수준 `managelevelCode`: 089001 초보자 / 089002 경험자 / 089003 전문가
- 계절별 물주기 `watercycleSprngCode` 등 (053001~053004)
- 방식: 실시간 호출이 아니라 **목록 전체 → 상세 전부 수집 → plant_species 테이블에 저장** (① 프로그램). 호출 사이에 짧은 대기
- 배치장소의 거리 기준은 "공공데이터 기준 추천"으로 쓰고, 우리 계산·센서 결과와 비교해 보정 결과를 보여주는 것이 발표 포인트

## 6. 하드웨어 (아두이노)

- 보유 키트: 아두이노 **우노**(와이파이 없음) + 센서 키트 37종 등
- 사용 부품: **CDS 조도 센서 2개**(창틀용 1, 방 안 후보 자리용 1), **온습도 센서(DHT11)**, **DS1302 시계 모듈**
- 키트에 없는 것: 무게·가스·CO2·전류 센서
- 연결 방식
  - 노트북 연결형: USB 시리얼 → 수집 프로그램이 읽어 저장 (개발·시연)
  - 단독 기록형: 휴대폰 충전기 전원 + 내부 EEPROM(1KB)에 10분 단위 요약 → 나중에 기록 파일 업로드 (파일 업로드 기능과 연결)
- **CDS는 상대값**이므로 절대 밝기 대신 "급변 시각"과 "비율"로만 판단. 창틀 센서는 직사광에서 포화되므로 직사광 있음/없음 판단용. 조도(lux) 비교가 필요하면 휴대폰 조도계로 1회 대응표 작성

## 7. 핵심 로직 설계

**초기 예측**
1. 동·지번으로 집 건물 찾기 → 중심점(EPSG:5186)
2. 창문 방향 앞쪽 일정 반경 안의 건물 검색 → 방향별 **가림 각도** = atan(높이차 / 거리)
3. 해 위치(15분 간격) + 가림 각도로 직사광 여부 판정
4. 창문 네 모서리를 바닥(또는 선반 높이)에 투영해 햇빛 영역 계산
5. 바닥 25cm 칸 격자, 칸별 직사광 시간 합산, **계절 대표일 4개(춘분·하지·추분·동지)** 저장
6. 직사광 없는 칸은 창 크기·거리로 밝기 단계(밝음/보통/어두움)

**센서 보정**
- 창틀 센서 값이 급등하는 시각 = 해가 가림물 위로 올라온 순간 → 그 시각 해 고도 = 실측 가림 각도
- ASOS 일조로 맑은 시간만 사용, 박명 시각으로 인공조명 제외
- 며칠 측정해 방향별 실측 가림 각도 목록(창에서 본 스카이라인) 생성 → 건물 데이터 추정값을 덮어씀 (대장 미연계 큰 건물, 나무, 간판도 반영)
- 방 안 센서의 창가 대비 비율로 밝기 단계 보정

**식물 추천**
- 광요구도 lux 범위·배치장소 거리 ↔ 칸별 결과 매칭해 적합도 점수
- 1년 내내 적합 → 고정 자리 / 계절마다 다름 → 계절별 이동 자리
- 겨울 측정 온도 < 식물 겨울 최저온도 → 경고

## 8. 계획된 테이블

member, house(주소·좌표·층수), room(모양 좌표·커튼), window(벽·방향·폭·높이·바닥에서 높이),
building(구현됨), obstruction_angle(방향별 가림각, 출처: 건물데이터/센서),
sunlight_cell(방·대표일별 칸: 좌표·직사광 시간·밝기 단계), plant_species, my_plant,
recommendation, device, sensor_log, calibration, weather_hour, sun_event, upload_file

## 9. 계획된 REST API

```
POST /api/houses                       (동·지번 → 건물 매칭 후 저장)
POST /api/houses/{id}/rooms
PUT  /api/rooms/{id}
POST /api/rooms/{id}/windows
GET  /api/rooms/{id}/sunlight?date=
GET  /api/rooms/{id}/recommendations
POST /api/devices/{id}/readings        (USB 수집 프로그램이 전송)
POST /api/rooms/{id}/sensor-logs       (단독 기록 파일 업로드)
GET  /api/plants?keyword=
GET  /api/rooms/{id}/report            (PDF 다운로드)
```

**권한**
- 비회원: 동·지번·창 방향만으로 간단 예측 체험, 식물 검색
- 회원: 집·방·창문 등록, 평면도 에디터, 기기·측정, 식물 등록·추천·알림, 리포트 다운로드
- 관리자: 식물 변환표, 건물 데이터 갱신, 기기·회원 관리

**파일**: 업로드 = 센서 기록 파일, 평면도 이미지, 식물 사진 / 다운로드 = 계절별 배치도 이미지, 리포트 PDF, 측정 원본 CSV

**② 추가 프로그램 = 평면도 햇빛 에디터** (브라우저 캔버스): 방 그리기, 창문 배치, 날짜·시각 슬라이더로 햇빛 이동 시뮬레이션(해 위치 계산을 브라우저에서 수행, 서버와 같은 공식), 식물 끌어다 놓기로 적합도 색 표시, 보정 전·후 햇빛 영역 겹쳐 보기

## 10. 로드맵

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 기본 구조, 해 위치, 좌표 변환, 건물 가져오기 | 완료 (실제 사하구 파일로 가져오기 확인 필요) |
| 2 | 회원·권한(Security 7), 집·방·창문 등록, 파일 업로드 기본 | 완료 |
| 3 | 가림 각도, 창문 투영, 칸별 햇빛, 농사로 가져오기 프로그램 | 완료 (농사로 키 승인 후 실제 응답 확인 필요) |
| 4 | 센서 수집 프로그램, 출몰시각·ASOS 연동, 보정 로직, 식물 추천 | 출몰시각·ASOS·추천 완료 / **센서 수집·보정은 아두이노 작업 때** (실측 가림 각도 저장 API `PUT /api/houses/{id}/obstructions/measured` 와 판단 함수 isClear·isNaturalLight 준비됨) |
| 5 | 평면도 햇빛 에디터, 리포트 다운로드, 실제 방 검증 사진 | 에디터·리포트 완료 / 검증 사진 예정 |
| 6 | 통합 테스트, 예외 정리, 발표 준비 (마감 12/18) | 예정 |

※ 맑은 날 센서 데이터가 쌓여야 하므로 센서 측정은 가능한 한 빨리 시작

## 11. 작업 규칙

- **비밀 정보 커밋 금지**: `application-local.properties`, API 키
- `.gitignore`에 포함: `.gradle/`, `build/`, `bin/`, `.project`, `.classpath`, `.settings/`, `.idea/`, `**/application-local.properties`, `backend/data/uploads/`
- 화면 변경은 frontend, 데이터·계산·권한 변경은 backend. API를 바꾸면 frontend의 화면 스크립트도 함께 확인
- Git: 원격 `https://github.com/wijiseung04-blip/plantspot.git`, 기본 브랜치는 **main**으로 맞추는 중 (이전에 main/master가 갈라지는 문제가 있었음)
- 같은 이클립스 워크스페이스에 다른 과제 프로젝트(StudyMateProject 등)가 있음 → **plantspot 외 프로젝트는 건드리지 말 것**
- 각 기능을 추가할 때 평가 기준 항목(필수 / ① / ②)과의 연결을 주석이나 커밋 메시지에 남기기
- 계산 로직은 단위 테스트로 검증 (기준값은 4장 참고)

## 12. 확인 필요 사항

- [x] `./gradlew build` 성공 여부 (JDK 25로 확인, JDK 26 환경에서 한 번 더)
- [x] `hibernate-community-dialects` 버전 자동 관리 여부 (Boot BOM이 관리)
- [ ] GIS 칸 정의서로 A0~A28 의미 확정
- [ ] ASOS 부산 지점 번호
- [ ] 농사로 API 키 승인 후 실제 응답 형식 확인
- [ ] CDS 센서 창틀 직사광 포화 테스트
- [ ] 사하구 GeoPackage 경로 확인 후 `BuildingImporter` 실행 (기대: 41,271동)

## 13. 이클립스에서 실행

1. MySQL에서 `backend/sql/create-database.sql` 실행 (비밀번호 바꿔서)
2. `backend/src/main/resources/application-local.properties.example` → 같은 폴더에 `application-local.properties` 로 복사, DB 비밀번호·키 입력
3. File → Import → Gradle → Existing Gradle Project → **plantspot 루트 폴더**
4. backend: `com.plantspot.PlantSpotApplication` 우클릭 → Run As → Java Application (8081)
5. frontend: `com.plantspot.front.PlantspotFrontendApplication` 우클릭 → Run As → Java Application (8080)
6. 브라우저 http://localhost:8080
