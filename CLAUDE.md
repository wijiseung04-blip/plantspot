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
- **DB는 `*.db` 파일로 제출** → SQLite 사용 (`data/plantspot.db`)

**추가 점수 (가장 중요)**
- **① DB 데이터 생성 프로그램 (20~30점)**: 센서 데이터 저장, 인터넷 공개 데이터를 원하는 형태의 DB로 전환, AI 등으로 분석 후 저장. 랜덤 데이터는 목적이 있을 때만 인정
- **② DB와 무관한 추가 프로그램 (20점)**: 브라우저에서 실행되는 게임·에디터·컴파일러 연결 등. **웹사이트 기본 로직(조회·계산 화면)은 대상 아님** → 독립된 브라우저 프로그램이어야 함
- 가치(상업적·학술적) 5점, 완성도 5점

**설계 원칙**: 모든 기능은 위 항목 중 어디에 해당하는지 설명할 수 있어야 한다.

## 3. 기술 환경과 구조 (교과서 3계층: controller → service → repository → domain)

- **Spring Boot 4.1.x** (Spring Framework 7, Spring Security 7, Hibernate 7), **Java 26** (Gradle toolchain, JDK 26이 없으면 `-PjavaVersion=25`)
- **Gradle 단일 프로젝트, 서버 하나(포트 8080)**. 반드시 `gradlew` 사용 (Gradle 9.8.2 — 이클립스 Gradle 설정은 "Gradle wrapper")
- IDE: **이클립스**(Buildship) — plantspot 폴더를 Gradle 프로젝트로 가져온다
- DB: **SQLite 파일** `data/plantspot.db` (`hibernate-community-dialects`의 `SQLiteDialect`, 커넥션 1개). 테스트는 메모리 SQLite
- 기본 패키지 **`com.springboot`** (수업 프로젝트와 같은 형태). 평가 기준 "구조(10점): 3계층 + 도메인 + DB"가 패키지만 봐도 보이게 계층별로 나눈다

```
src/main/java/com/springboot
├── PlantSpotApplication.java
├── controller/   (MVC) REST 컨트롤러 /api/** + PageController(화면 주소 → 템플릿) + Codes(화면 선택 목록)
├── service/      (비즈니스) 집·방·햇빛·추천·식물·파일·회원·공공데이터·리포트 서비스
├── repository/   (퍼시스턴트) Spring Data JPA 리포지토리
├── domain/       JPA 엔티티·enum (Member, Building, House, Room, RoomWindow, PlantSpecies, SunlightCell …)
├── dto/          요청·응답 객체 (HouseRequests, HouseResponses, SignupForm …)
├── calculator/   계산 로직 (SolarCalculator, ObstructionCalculator, BuildingFacade, FloorRule, TerrainCalculator, SunlightCalculator, PlantScorer, TmConverter …)
├── client/       외부 API 호출 (농사로·천문연·기상청·브이월드)
├── report/       PDF·PNG 만들기 (PdfWriter, RoomPlanRenderer, KoreanFont)
├── tool/         ① 데이터 생성 프로그램 (BuildingImporter, PlantImportRunner, WeatherCollectRunner, TerrainImportRunner, GpkgGeometryReader, AscGridReader)
├── config/       SecurityConfig, PasswordConfig, AdminInitializer, DataDirectoryInitializer, SchemaUpgrade(예전 DB의 enum check 제약 고치기)
├── security/     LoginMember (로그인 회원)
├── event/        HouseEvents, PlantEvents (계산 결과를 낡게 만드는 변경 알림)
└── exception/    예외(NotFound·Forbidden·Conflict·InvalidValue 등)·GlobalExceptionHandler·ErrorResponse
src/main/resources
├── templates/    화면 (Thymeleaf)
├── static/       css, js(api.js 등), editor/ (② 평면도 햇빛 에디터)
└── application.properties (+ application-local.properties: API 키·관리자 비밀번호, 커밋 금지)
data/plantspot.db  DB 파일 (처음 실행할 때 자동 생성, 테이블은 JPA가 만듦, git 제외)
```

- 화면(templates·static)은 데이터를 직접 읽지 않고, 화면 스크립트가 `/api/**`(REST 컨트롤러)를 부른다
- 로그인: `POST /api/auth/login` (JSON, 세션 쿠키), 가입 `POST /api/auth/signup`, 로그아웃 `POST /api/auth/logout`
- 권한(`config/SecurityConfig`): 화면 `/my/**` 회원(아니면 로그인 화면으로), `/admin/**` 관리자(아니면 403), `/api/**` 는 401·403 JSON
- CSRF: `XSRF-TOKEN` 쿠키, 화면 `static/js/api.js` 가 `GET /api/auth/csrf` 로 토큰을 받아 헤더에 붙임
- 화면 머리글의 로그인 표시는 PageController 의 model `me`(LoginMember: me.nickname, me.admin)

**실행**: `com.springboot.PlantSpotApplication` 실행 → 브라우저 http://localhost:8080

**Spring Boot 4 주의사항**
- 웹 스타터는 `spring-boot-starter-webmvc` (`starter-web` 아님), 테스트 스타터는 기능별(`webmvc-test` 등), `@MockBean` → `@MockitoBean`
- Jackson 3 (`tools.jackson.*`)

## 4. 현재 코드 상태 (아두이노 전까지 구현)

빌드·테스트: `./gradlew build` (단위·API·화면 테스트 189개). 서버를 실행해 브라우저로도 확인함.
테스트도 같은 계층 패키지(`src/test/java/com/springboot/controller|service|calculator|client|tool`, 공용 도구는 `support`).
`src/test/js/editor-parity.js`: 브라우저 JS와 서버 계산 일치 확인 (node 로 실행)

**데이터 생성 프로그램 (① tool 패키지 — 웹 서버 없이 실행 후 종료, DB 설정은 application.properties)**
- `./gradlew importBuildings -Pgpkg=경로` / `./gradlew importPlants` / `./gradlew collectWeather -Pfrom= -Pto=` / `./gradlew importTerrain -Pdem=경로.asc` (건물 먼저)

**주의할 점 (작업하며 확인한 것)**
- SQLite에서 id가 자동 번호(rowid)가 되려면 칸 타입이 정확히 `integer` → 모든 엔티티 id에 `@Column(columnDefinition = "integer")`. `SchemaTest`가 검사
- 테스트 DB는 `ddl-auto=update` (create-drop 은 타입 없는 id 칸을 만들어 자동 번호가 안 붙음)
- 벌크 delete 쿼리 대신 엔티티 단위 삭제(`deleteAll(find...)`) — SQLite가 번호를 재사용해 영속성 컨텍스트와 충돌함
- SQLite는 한 번에 한 프로그램만 쓸 수 있다 → 가져오기 프로그램(importBuildings 등)은 서버를 끄고 실행
- 계산 결과(가림 각도·칸별 햇빛·추천)는 HouseEvents/ObstructionChanged/PlantEvents 로 낡으면 지우고 다음 조회 때 다시 계산
- Thymeleaf `<script>` 안의 `[(`, `[[` 는 인라인 문법 → 스크립트에 `th:inline="none"`
- `@RequestParam`·`@PathVariable` 에는 이름을 꼭 적는다(`@RequestParam("dong")`) — 이클립스 컴파일러는 `-parameters` 가 꺼져 있어 이름을 못 읽는다
- 건물을 다시 가져올 때 house.building_id 를 끊었다가 동·지번으로 다시 연결한다(외래 키 때문)
- PDF 한글 글꼴: `app.report.font`(없으면 윈도 맑은 고딕 → 맥 → 리눅스 나눔·Noto 순)

**검증된 기준값** (테스트 기대값으로 사용 중)
- 부산(35.10N, 128.97E) 정오 최고 고도: 춘분 54.7°, 하지 78.3°, 동지 31.5°, 방위각 약 180°
- 2026-11-08 부산 일출 계산값 약 06:52
- EPSG:5186 (200000, 600000) → (38.0, 127.0)
- 하단동 1160-6 중심점 (378731.47, 281659.43) → (35.1154, 128.9605), 면적 약 300㎡, 높이 18.75m(MEASURED)
- 사하구 가져오기 결과: MEASURED 15,360 / FLOOR_ESTIMATE 9,444 / DEFAULT 16,467 (합계 41,271) — 실제 파일로 재확인 필요

## 5. 데이터 소스

API 키·관리자 비밀번호는 `src/main/resources/application-local.properties`에 둔다 (`.gitignore` 대상, 절대 커밋 금지, 예시는 같은 폴더의 `.example`).
`application.properties`에서 `spring.config.import=optional:classpath:application-local.properties`로 읽는다.

| 데이터 | 방식 | 상태 | 키 이름 |
|---|---|---|---|
| 국토부 GIS 건물통합정보 | 파일(브이월드), SHP → QGIS에서 사하구만 GeoPackage로 저장 | 확보 | - |
| 한국천문연구원 출몰시각 | 공공데이터포털 API, XML | 키 발급 완료 | `api.kasi.service-key` |
| 기상청 ASOS 시간자료 | 공공데이터포털 API, JSON/XML | 키 발급 완료 | `api.kma.service-key`, `api.kma.station-id` |
| 농촌진흥청 실내정원용 식물 | 농사로 Open API, XML | 키 승인, 가져오기 확인 | `api.nongsaro.api-key` |
| 지형(땅 높이) | 수치표고모델(DEM) 파일 → QGIS 에서 EPSG:5186, Arc/Info ASCII Grid(.asc) 로 저장 | 코드 완료, **사용자 PC에서 파일 받아 importTerrain 실행 필요** | - |
| 브이월드 주소 검색 | 검색 API(주소, EPSG:5186 좌표) | 키 발급, **도로명주소로 건물 찾기에 사용** | `api.vworld.key` (`api.vworld.domain` 필요 시) |

### 5-1. GIS 건물통합정보
- 좌표계 **EPSG:5186** (Korea 2000 / Central Belt 2010, 미터). 거리 계산은 이 좌표 그대로 사용
- 원본 인코딩 EUC-KR (QGIS 저장본은 UTF-8)
- 칸 이름이 A0~A28 (추정 의미, 공식 정의서로 확인 필요): A2 PNU, A3 법정동코드(앞 2자리 26=부산), **A4 법정동명**("부산광역시 사하구 하단동"), **A5 지번**, **A9 용도**, A13 사용승인일, **A16 높이(m)**, **A24 건물명**(비어 있는 경우 많음), A25 동명, **A26 지상층수**, A27 지하층수
- 사하구 품질: 높이 있음 38%, 층수 있음 60%, 둘 다 없음 40%(대부분 건축물대장 미연계 소형 건물, 면적 가운데값 33㎡). **10층 이상 721동 중 93%는 높이 있음**
- 입력 오류 존재(예: 높이 15,400m)
- 파일명 규칙: `AL_D010_26_YYYYMMDD` = 부산 전체, `CH_...` = 일별 변경분(U/I/D)

**FloorRule (층 검증)**: 집 등록·수정·비회원 예측에서 층 > 고를 수 있는 최대 층이면 400 (`InvalidValueException`)
- 최대 층 = 지상층수(A26), 없고 높이 MEASURED 면 높이 ÷ 2.5m 올림, 둘 다 없으면 막지 않고 응답 `floorWarning` 으로 안내
- 응답에 `maxFloor`·`floorWarning`, 건물 데이터를 다시 가져와 층수가 줄면 기존 집은 경고만

**HeightRule (구현됨)**
- 높이 > 0 이고 ≤ 200m 이고 (층수 있으면) 높이/층수 ≤ 10m → 그대로 (MEASURED)
- 아니고 층수 있으면 → 층수 × 층고 (공동주택 3.0, 단독주택 3.5, 그 외 3.6) (FLOOR_ESTIMATE)
- 둘 다 없으면 → 3.5m (DEFAULT)

**사용자 집 위치 찾기** (두 가지)
- **도로명주소(또는 지번주소) 검색**: `GET /api/address/search?query=` → 브이월드 검색 API(도로명, 없으면 지번) → 좌표(EPSG:5186) → building 외곽선 안의 건물, 없으면 30m 안 가장 가까운 건물 → 그 건물의 동·지번 (`address/AddressService`, 화면 `static/js/address.js`)
- **동 선택 + 지번 입력** → building 테이블 검색 (주소 검색 키가 없을 때도 동작)
- 건물명 검색은 건물명이 비어 있는 경우가 많아 쓰지 않음

### 5-1-0. 창별 관측점 (동 안 세대 위치)
- 층고 기본값 2.8m (`HouseService.DEFAULT_FLOOR_HEIGHT`, 아파트 보통 2.8~2.9m, 비회원 예측도 같은 값). 창 눈 높이 = (층−1) × 층고 + 1.2m
- 집 `unit_position`(−1~1): 건물 긴 축(외곽선을 감싸는 최소 사각형의 긴 변, 0~180°)을 따라 우리 세대 위치. +1 = 긴 축 방향 끝 (예: 축 90° → 동쪽 끝)
- 창마다 관측점 = 세대 위치에서 창 방향으로 나가 처음 만나는 우리 건물 외벽 + 0.5m (`BuildingFacade`). 외곽선 없으면 건물 중심
- 관측점이 우리 건물 바깥이면 **우리 건물도 가림물로 넣음** (ㄷ자·ㄱ자의 다른 날개 그림자). 창이 붙은 벽은 관측점보다 뒤라 창 뒤쪽 방향으로만 잡혀 영향 없음. 집 화면 스카이라인(건물 가운데)은 계속 뺌
- 햇빛 계산(SunlightService)·리포트·비회원 예측은 창별 관측점 값, 집 화면 스카이라인은 건물 가운데 기준(센서 실측은 집 단위로 모든 창에 적용)
- 방 화면 창문 표: `GET /api/rooms/{id}/window-obstructions` (창 앞 ±45° 평균, ±85° 안 최대, 방향 칸마다 `bins`: 각도·원인 OWN/BUILDING/TERRAIN/SENSOR·원인 건물 이름·거리)
- 방 화면 **창별 스카이라인** 그림: 원인 4색(우리 건물 회색·옆 건물 초록·산 갈색·센서 주황), 창 뒤쪽 반원 빗금, 동지·춘분·하지 해 경로(`GET /api/sun/paths`)와 계절별 "해 드는 시간 · 창 뒤쪽 · 원인별 가림 시간" 요약 (칸마다 가장 높게 가린 원인 하나)
- 화면 선택: 끝(±0.85)·중간(±0.45)·가운데, 이름은 긴 축 방향으로 "동쪽 끝/서쪽" 등

### 5-1-2. 안쪽 창 선 (발코니 + 거실)
- 바깥 창 → 발코니 → 거실 창 → 거실 구조: 발코니+거실을 한 방으로, 창문은 바깥 창. 방에 `inner_glass_wall`·`inner_glass_distance`(cm)·`inner_glass_transmittance`(유리 0.75 / +얇은 커튼 0.45 / +블라인드 0.38)
- `PUT/DELETE /api/rooms/{id}/inner-glass` (벽 번호·방 깊이 검사, 바뀌면 RoomChanged). 방 모양이 바뀌어 벽이 없어지거나 깊이보다 멀면 자동 삭제
- 계산: 그 벽에서 거리 > distance 인 칸만 하늘빛(lux)에 투과율을 곱함, 직사광 시간은 그대로 (`SunlightCalculator.innerGlassFactor`, 에디터 `light.js` 같은 식, parity 확인)
- 농사로 배치장소 거리(발코니 창측 0~50cm …)가 바깥 창 기준이라 그대로 맞음. 화면: 방 화면 입력, 평면도·햇빛·추천·에디터·배치도 PNG·리포트 PDF 에 파란 점선 (PDF 는 안쪽 창 설명 한 줄도)

### 5-1-1. 지형 (땅 높낮이)
- `importTerrain -Pdem=`: ASCII Grid 에서 건물 범위 + 5km 만 잘라 → `terrain_grid`·`terrain_point` (150만 점 넘으면 몇 칸씩 건너뜀, 다시 가져오면 바꿔 넣음)
- 고도 API(Open-Meteo)는 점 하나 = 호출 1번(시간당 5,000·하루 10,000)이라 사하구 5.6만 점에서 한도에 걸려 **버림** → 파일 방식
- 파일: Copernicus DEM GLO-30(30m, 무료) 또는 국토지리정보원 DEM. QGIS 「워프(재투영)」 EPSG:5186·해상도 한 값 → 「다른 이름으로 저장」 Arc/Info ASCII Grid
- 가림 각도 = max(건물, 지형). 건물은 (그 건물 땅 높이 − 우리 땅 높이)를 높이에 더함(경사지), 지형(산)은 300m~10km, 지구 곡률·굴절 보정
- 300m 안은 지형으로 세지 않음: Copernicus DEM 은 건물·나무가 섞인 표면 높이라 건물을 두 번 세게 됨
- 지형 자료를 나중에 넣으면 집 가림 각도를 다음 조회 때 자동으로 다시 계산 (obstruction_angle 출처 TERRAIN)
- 설정: `plantspot.terrain.buffer`(5000) / `.max-points`(1500000)

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
- 새 클래스는 역할에 맞는 계층 패키지에 둔다 (엔티티 → domain, 리포지토리 → repository, 서비스 → service, 컨트롤러 → controller)
- `.gitignore`에 포함: `.gradle/`, `build/`, `bin/`, `.project`, `.classpath`, `.settings/`, `.idea/`, `**/application-local.properties`, `data/*.db`, `data/uploads/`
- 제출할 때는 데이터를 넣은 `data/plantspot.db` 를 따로 낸다(저장소에는 올리지 않음)
- 화면 변경은 templates·static·PageController, API를 바꾸면 화면 스크립트도 함께 확인
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

1. `src/main/resources/application-local.properties.example` → 같은 폴더에 `application-local.properties` 로 복사, 키·관리자 비밀번호 입력
2. Window → Preferences → Gradle → Gradle distribution: **Gradle wrapper**
3. File → Import → Gradle → Existing Gradle Project → **plantspot 폴더**
4. `com.springboot.PlantSpotApplication` 우클릭 → Run As → Java Application
5. 브라우저 http://localhost:8080 (DB 파일은 data/plantspot.db 에 자동 생성)
