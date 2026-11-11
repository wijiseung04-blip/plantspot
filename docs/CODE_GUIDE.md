# plantspot 코드 설명서

이 문서는 plantspot 의 **코드가 어디서 무엇을 어떻게 하는지** 한 번에 이해할 수 있게 정리한 것입니다.
실행 방법과 화면 사용법은 [README](../README.md) 를 보세요.

- 1장: 전체 그림 (요청이 어떻게 흐르는지, 데이터가 어디서 와서 어디로 가는지)
- 2장: 평가 기준 ↔ 코드
- 3장: 패키지·파일별 설명 (Java)
- 4장: 화면 파일 설명 (Thymeleaf·JavaScript)
- 5장: 핵심 기능을 처음부터 끝까지 따라가기
- 6장: 테스트
- 7장: 작업하며 알게 된 주의점
- 8장: 한계와 남은 일

---

## 1. 전체 그림

### 1-1. 한 문장
공공데이터(건물·지형·해 위치·기상·식물)를 DB 로 바꿔 두고, 사용자가 등록한 방·창문에 대해
**해 위치 × 주변 가림 각도 → 25cm 칸마다 햇빛 → 식물 적합도** 를 계산해 보여 주는 Spring Boot 웹 서비스입니다.

### 1-2. 요청 흐름 (3계층)

```
브라우저 화면 (templates/*.html + static/js)
   │  fetch('/api/...')  ← static/js/api.js 가 CSRF 토큰·세션 쿠키를 붙임
   ▼
controller  (REST: @RestController, 화면 주소: PageController)
   │  요청 값 검증(Bean Validation), 로그인 회원(@AuthenticationPrincipal LoginMember)
   ▼
service     (비즈니스 규칙, 트랜잭션, 계산 결과 저장·재사용)
   │            └─ calculator (순수 계산: 해 위치, 가림 각도, 햇빛, 점수 …)
   │            └─ client     (외부 API: 농사로·천문연·기상청·브이월드)
   ▼
repository  (Spring Data JPA)
   ▼
domain      (JPA 엔티티) ──► SQLite 파일 data/plantspot.db
```

- **화면은 DB 를 직접 읽지 않습니다.** PageController 는 템플릿 이름만 돌려주고, 데이터는 화면 스크립트가 `/api/**` 로 받아 그립니다.
- 오류는 어디서 나든 `exception/GlobalExceptionHandler` 가 같은 모양의 JSON(`status, error, message, path`)으로 바꿉니다.

### 1-3. 데이터 흐름

```
[① 데이터 생성 프로그램 — tool 패키지, 서버 끄고 실행]
  GIS 건물통합정보 GeoPackage ──BuildingImporter──► building
  농사로 Open API(XML)        ──PlantImportService─► plant_species (코드 → lux·직사광·창 거리 변환)
  DEM ASCII Grid             ──TerrainService────► terrain_grid / terrain_point
  기상청 ASOS API(JSON)       ──PublicDataService──► weather_hour      (서버에서도 매일 자동)
  천문연 출몰시각 API(XML)     ──PublicDataService──► sun_event         (조회할 때 저장)

[회원 입력]
  house(집: 건물·층·동 안 위치) → room(방 모양) → room_window(창문)

[계산 결과 — 처음 조회할 때 계산해서 저장, 입력이 바뀌면 이벤트로 지움]
  obstruction_angle (방향별 가림 각도: BUILDING / TERRAIN / SENSOR)
  sunlight_summary + sunlight_cell (방·계절 대표일별 25cm 칸 햇빛)
  recommendation (방별 식물 추천 상위)

[파일]
  upload_file (메타 정보) + data/uploads/ (실제 파일)
```

### 1-4. 테이블 (16개, 모두 JPA 엔티티가 만듦)

| 테이블 | 엔티티 | 내용 |
|---|---|---|
| member | Member | 회원 (아이디, BCrypt 비밀번호, 닉네임, 권한 USER/ADMIN) |
| building | Building | 건물 (동·지번, 동 이름, 용도, 층수, 원래 높이·최종 높이·높이 출처, 넓이, 중심점, 외곽선 WKT, 위경도) |
| house | House | 집 (회원, 건물, 층, 층 높이, 동 안 위치 unit_position, 중심점·위경도) |
| room | Room | 방 (모양 "x,y;x,y;…", 평면도 위쪽 방향, 천장, 커튼, 겨울 최저 실내 온도, 평면도 이미지) |
| room_window | RoomWindow | 창문 (벽 번호, 벽 시작점에서 거리, 폭·높이·창턱, 방향은 계산) |
| obstruction_angle | ObstructionAngle | 집의 5° 방향별 가림 각도 (출처 BUILDING·TERRAIN·SENSOR, 원인 건물, 거리) |
| sunlight_summary / sunlight_cell | SunlightSummary / SunlightCell | 방·계절·연도별 요약(낮 길이, 창별 해 드는 시간)과 칸별 결과(직사광 분, lux, 밝기 단계) |
| plant_species | PlantSpecies | 식물 (농사로 원본 코드·이름 + 변환값, 관리자 조정 여부) |
| recommendation | Recommendation | 방별 추천 순위·점수·자리 |
| my_plant | MyPlant | 내 식물 (별명, 종, 방, 놓은 자리, 사진) |
| sun_event | SunEvent | 천문연 출몰시각 (일출·남중·일몰, 시민·항해·천문 박명) |
| weather_hour | WeatherHour | 기상청 ASOS 시간자료 (기온, 습도, 일조, 일사, 전운량) |
| terrain_grid / terrain_point | TerrainGrid / TerrainPoint | 지형 격자 정보와 점별 해발 높이 |
| upload_file | UploadFile | 업로드 파일 정보 (종류, 원래 이름, 저장 이름, 크기, 주인, 방) |

---

## 2. 평가 기준 ↔ 코드

| 평가 항목 | 어디에 |
|---|---|
| 로그인·회원가입 | `controller/AuthApiController`(JSON 로그인·가입·CSRF), `service/MemberService`, `dto/SignupForm`, `config/PasswordConfig`(BCrypt) |
| 회원 / 비회원 / 관리자 | `config/SecurityConfig`, `security/LoginMember`, 서비스의 본인 확인(`HouseService.checkOwner` 등), `config/AdminInitializer` |
| 파일 업로드·다운로드 | `controller/FileApiController`, `service/FileService`·`FileStorage`, `controller/ReportApiController`·`service/RoomReportService`(PDF·PNG·CSV) |
| 3계층 구조 | 패키지 `controller → service → repository → domain` |
| REST 설계 | 자원 중심 주소 `/api/houses/{id}/rooms`, `/api/rooms/{id}/windows` 등, 만들기 201 + Location |
| Bean Validation | `dto/HouseRequests`, `SignupForm`, 서비스 안의 `record` 요청들 (`@NotBlank @Min @DecimalMax …`) |
| 공통 예외 처리 | `exception/GlobalExceptionHandler` + 예외 클래스 5종 |
| 로그 | 각 서비스의 SLF4J `log.info/warn` (가져오기 결과, 계산, 실패 원인) |
| DB 파일 제출 | SQLite `data/plantspot.db` (`application.properties`) |
| **① DB 데이터 생성 프로그램** | `tool/*`(건물·식물·지형·기상 가져오기), `service/PlantImportService`·`TerrainService`·`PublicDataService`, 분석 후 저장(`ObstructionService`·`SunlightService`·`RecommendationService`) |
| **② DB 와 무관한 추가 프로그램** | `static/editor/`(editor.js, light.js, solar.js) + `templates/editor.html` |

---

## 3. 패키지·파일별 설명 (Java)

`src/main/java/com/springboot/` 아래입니다.

### 3-1. 시작과 설정

| 파일 | 설명 |
|---|---|
| `PlantSpotApplication` | 시작점. `@EnableScheduling`(매일 ASOS 자동 저장) |
| `resources/application.properties` | 포트 8080, SQLite `jdbc:sqlite:data/plantspot.db`, 커넥션 1개, `ddl-auto=update`, 업로드 폴더 `data/uploads`, 10MB 제한, `application-local.properties`(키) 읽기 |
| `build.gradle` | Spring Boot 4.1.2, Java 26 툴체인(`-PjavaVersion=25` 가능), 가져오기 작업 `importBuildings / importPlants / importTerrain / collectWeather` 를 `JavaExec` 로 등록(웹 서버 없이 `--plantspot.import=…` 로 실행) |

### 3-2. config — 보안·초기화

| 파일 | 설명 |
|---|---|
| `SecurityConfig` | 주소별 권한. 비회원: `/`, `/login`, `/signup`, `/try`, `/plants/**`, `/editor` 와 조회 API(`GET /api/sun/**, /api/buildings/**, /api/plants/**, /api/address/**`, `POST /api/preview`). 회원: `/my/**` 와 나머지 `/api/**`. 관리자: `/admin/**`, `/api/admin/**`. `/api/**` 는 401·403 을 JSON 으로, 화면은 로그인 화면으로 보냄. CSRF 는 `XSRF-TOKEN` 쿠키 방식. 웹 서버일 때만 만들어짐(`@ConditionalOnWebApplication`) |
| `PasswordConfig` | `PasswordEncoder`(BCrypt), `AuthenticationManager`, `SecurityContextRepository`(세션). 가져오기 프로그램에서도 회원 서비스가 돌도록 보안 설정과 분리 |
| `AdminInitializer` | 관리자가 하나도 없고 `app.admin.password` 가 있으면 관리자 계정 생성 (기본 비밀번호를 코드에 두지 않음) |
| `DataDirectoryInitializer` | DB 연결 전에 `data/` 폴더 만들기 (SQLite 드라이버는 폴더를 안 만듦). `META-INF/spring.factories` 로 등록 |
| `SchemaUpgrade` | 예전 DB 파일 고치기: Hibernate 가 enum 칸에 만든 `check (source in ('BUILDING','SENSOR'))` 에 새 값 `TERRAIN` 이 없으면, 같은 구조의 새 테이블로 옮겨 담음(실측값 보존) |

### 3-3. security · event · exception

| 파일 | 설명 |
|---|---|
| `security/LoginMember` | 로그인 회원(`UserDetails`). 컨트롤러에서 `@AuthenticationPrincipal LoginMember me` 로 받음. `id, loginId, nickname, admin` |
| `service/MemberUserDetailsService` | 아이디로 회원을 읽어 `LoginMember` 로 바꿈 (Spring Security 로그인용) |
| `event/HouseEvents` | `HouseChanged`, `HouseDeleting`, `RoomChanged`, `RoomDeleting` — 집·방이 바뀌면 딸린 계산 결과를 지우게 알림 |
| `event/PlantEvents` | `PlantChanged`, `PlantDeleting` — 식물 변환값이 바뀌면 추천을 지움 |
| `exception/*` | `NotFoundException`(404), `ForbiddenException`(403), `ConflictException`(409, 아이디 중복 등), `InvalidValueException`(400, 예: 건물 층수보다 높은 층), `UnavailableException`(503, 외부 API 키 없음·연결 실패) |
| `exception/GlobalExceptionHandler` | 위 예외와 검증 실패(`MethodArgumentNotValidException`, `HandlerMethodValidationException`), 형식 오류, 파일 크기 초과, `IllegalArgumentException`(400), 그 밖(500, 로그 남김)을 `ErrorResponse` JSON 으로 |

### 3-4. domain — 엔티티와 값

엔티티는 1-4 표 참고. 공통 규칙: **모든 id 는 `@Column(columnDefinition = "integer")`** (SQLite 자동 번호 조건, `SchemaTest` 가 검사).

| 파일 | 특이점 |
|---|---|
| `Building` | 가져오기 프로그램만 만듦(생성자 없음). `heightSource`: MEASURED / FLOOR_ESTIMATE / DEFAULT |
| `House` | 건물에서 중심점·위경도를 복사. `floorElevation() = (층−1) × 층 높이`, `unitPosition()`(없으면 0) |
| `Room` | 모양은 문자열로 저장, `points()` 로 꺼냄. 창문 목록(`@OneToMany`, cascade). 안쪽 창 선(발코니+거실: 벽 번호·거리·투과율) |
| `RoomWindow` | 방향(`azimuthDeg`)은 벽의 방향과 평면도 위쪽 방향으로 계산 |
| `PlantSpecies` | `applyNongsaro()`(농사로 값 반영, 관리자가 조정한 변환값은 유지), `adjust()`(관리자 수정), `manual()`(직접 추가) |
| `SunEvent` | `isNaturalLight(t)`: 시민박명 사이인가 (센서 분석에서 인공조명 빼기용) |
| `WeatherHour` | `isClear()`: 일조 0.5시간 이상 (센서 분석에서 흐린 시간 빼기용) |
| enum | `Role`, `HeightSource`, `ObstructionSource`(BUILDING·TERRAIN·SENSOR), `LightLevel`(밝음: 직사광 120분↑ 또는 1,500lux↑ / 보통: 30분↑ 또는 800lux↑ / 어두움), `SeasonDay`(춘분·하지·추분·동지 날짜), `CurtainType`(투과율), `FileCategory`(센서 기록·평면도·식물 사진과 허용 확장자), `PlantSource` |
| `Point` | 방 모양 꼭짓점 (cm) |

### 3-5. repository

Spring Data JPA 인터페이스 16개. 메서드 이름으로 쿼리를 만들고, 필요한 곳만 `@Query`:
- `BuildingRepository.findInBox(minX, maxX, minY, maxY)`: 중심점이 사각형 안인 건물 (가림 각도·주소 찾기), `findExtent()`: 건물 전체 범위(지형 자르기), `findDistinctDongs()`: 동 목록
- `TerrainPointRepository.findValues(gridId)`: 지형 점 전체를 `[ix, iy, 높이]` 로 한 번에
- `ObstructionAngleRepository.deleteByHouseAndSource`, `SunlightCellRepository.findByRoomIdAndSeasonAndYearOrderByIyAscIxAsc` 등

### 3-6. dto

| 파일 | 설명 |
|---|---|
| `HouseRequests` | `CreateHouse`, `UpdateHouse`, `SaveRoom`, `SaveWindow` — 검증 애너테이션 포함 (층 1~80, 층 높이 2.2~6m, 세대 위치 −1~1 …) |
| `HouseResponses` | `HouseResponse`(최대 층 `maxFloor`, 층 경고 `floorWarning`, 건물 긴 축 `buildingAxis` 포함), `RoomResponse`, `WindowResponse` |
| `SignupForm` | 아이디(영문 소문자·숫자·밑줄 4~20), 비밀번호 8~64, 비밀번호 확인 일치 |
| `NongsaroPlant`, `KasiRiseSet` | 외부 API 응답을 옮겨 담는 값 |

### 3-7. controller — REST API

| 컨트롤러 | 주소 | 권한 |
|---|---|---|
| `AuthApiController` | `GET /api/auth/csrf`, `POST /api/auth/login`, `POST /api/auth/signup` (로그아웃은 `POST /api/auth/logout`, SecurityConfig) | 누구나 |
| `MemberApiController` | `GET /api/members/me` | 회원 |
| `AdminApiController` | `GET /api/admin/members`, `PUT /api/admin/members/{id}/role` | 관리자 |
| `BuildingController` | `GET /api/buildings?dong&jibun`(한 지번의 건물들, 긴 축 `axis` 포함), `/api/buildings/dongs`, `/api/buildings/nearby` | 누구나 |
| `AddressApiController` | `GET /api/address/search?query=` | 누구나 |
| `HouseApiController` | 집 `POST/GET /api/houses`, `GET/PUT/DELETE /api/houses/{id}` · 방 `POST/GET /api/houses/{id}/rooms`, `GET/PUT/DELETE /api/rooms/{id}`, 안쪽 창 `PUT/DELETE /api/rooms/{id}/inner-glass` · 창문 `POST/GET /api/rooms/{id}/windows`, `PUT/DELETE /api/windows/{id}` | 회원(본인) |
| `ObstructionApiController` | `GET /api/houses/{id}/obstructions`, `POST …/recalculate`, `PUT/DELETE …/measured`(센서 실측 저장·지우기), `GET /api/rooms/{id}/window-obstructions`(창별: 평균·최대와 방향 칸마다 각도·원인·원인 건물·거리) | 회원 |
| `SunlightApiController` | `GET /api/rooms/{id}/sunlight?season=&year=` 또는 `?date=&calibrated=`, `POST …/sunlight/calculate` | 회원 |
| `RecommendationApiController` | 추천 `GET /api/rooms/{id}/recommendations`, `POST …/refresh`, `GET …/recommendations/{plantId}`(칸별 적합도), `GET /api/rooms/{id}/plant-scores`(모든 식물 점수) · 내 식물 `POST/GET /api/my-plants`, `PUT/DELETE /api/my-plants/{id}`, `PATCH …/position`, `GET /api/my-plants/alerts` · 비회원 `POST /api/preview` | 회원 / preview 는 누구나 |
| `PlantApiController` | `GET /api/plants?keyword&light&level&page&size`, `GET /api/plants/{id}` · 관리자 `POST /api/admin/plants`, `PUT …/{id}/conversion`, `POST …/conversion/reset`, `DELETE …/{id}`, `POST/GET /api/admin/plants/import`(가져오기 시작·진행 상황) | 누구나 / 관리자 |
| `PublicDataApiController` | `GET /api/sun-events?date&lat&lon`, `GET /api/sun-events/compare`, `GET /api/weather/hours?date` · 관리자 `POST /api/admin/weather/collect`, `GET /api/admin/public-data/status`, `GET /api/admin/terrain/status` | 회원 / 관리자 |
| `FileApiController` | `POST /api/files`(multipart), `GET /api/files`, `GET /api/files/{id}`, `GET /api/files/{id}/content`(다운로드·`inline=true`면 바로 보기), `DELETE /api/files/{id}` | 회원(본인) |
| `ReportApiController` | `GET /api/rooms/{id}/report`(PDF), `…/sunlight/image`(PNG), `…/sunlight/csv`, `GET /api/weather/hours/csv` | 회원 |
| `SunController` | `GET /api/sun?dateTime&lat&lon` (해 위치·출몰), `GET /api/sun/paths?lat&lon&year` (계절 대표일 해 경로 15분 간격, 창별 스카이라인 그림용) | 누구나 |
| `PageController` | 화면 주소 → 템플릿 이름. 로그인 회원 정보를 `me` 로 넘김 (머리글 표시용) | 주소별 |
| `Codes` | 화면 선택 목록(빛 요구, 계절, 커튼 등)을 모델로 |

> 이클립스 컴파일러는 `-parameters` 가 꺼져 있어, 모든 `@PathVariable("id")`·`@RequestParam("dong")` 에 이름을 적었습니다.

### 3-8. service — 비즈니스 로직

| 서비스 | 하는 일과 구현 |
|---|---|
| `MemberService` | 가입(아이디 중복이면 409), 회원 조회, 권한 변경 |
| `BuildingService` | 동·지번으로 건물 찾기. `choose(dong, jibun, buildingId)`: 고른 건물이 그 지번 것인지 확인, 없으면 가장 높은 건물 |
| `AddressService` | 브이월드로 도로명 검색(없으면 지번) → 같은 주소·동 이름이면 합침 → 좌표(EPSG:5186)를 **품은 건물 외곽선**, 없으면 30m 안 가장 가까운 건물 |
| `HouseService` | 집·방·창문 만들기·고치기·지우기. 본인(또는 관리자)만 (`checkOwner`). 층이 건물 범위를 넘으면 `InvalidValueException`(`FloorRule`). 방 넓이 1㎡ 이상, 창문이 벽 밖으로 나가면 오류. 눈 높이·세대 위치·방·창이 바뀌면 `HouseEvents` 발행 |
| `ObstructionService` | **가림 각도**. 집 단위(건물 가운데 기준)로 계산해 `obstruction_angle` 에 BUILDING·TERRAIN 행 저장, 센서 실측(SENSOR)은 덮어씀. `windowProfile(house, 창 방향)`: 창마다 우리 건물 외벽 바로 앞(`BuildingFacade`)에서 다시 계산(메모리 캐시). 건물 높이에 땅 높이 차(지형)를 더함. 이벤트를 받아 낡은 값 지움 |
| `TerrainService` | DEM ASCII Grid 읽기 → 건물 범위 + 5km 만 잘라 JDBC 일괄 저장(150만 점 넘으면 칸 건너뜀), 좌표계가 다르면 EPSG:5186 안내. `model()` 로 격자를 메모리에 올리고 `groundAt(x, y)` 로 땅 높이 |
| `SunService` | 한국 시각으로 해 위치(`SolarCalculator` 감싸기) |
| `SunlightService` | 방의 계절 대표일 4개를 `SunlightCalculator` 로 계산해 `sunlight_summary/cell` 에 저장하고 다음엔 읽기만. 다른 날짜는 저장 없이 계산. 창마다 다른 가림 각도를 넘김. `calibrated=false` 면 센서 보정 전 값 |
| `PlantService` | 식물 검색(이름·빛 요구·관리 수준, 쪽 나눔), 관리자 변환값 수정·되돌리기·직접 추가·삭제(+`PlantEvents`) |
| `PlantImportService` | **농사로 가져오기**: 목록(50개씩) 전체 → 상세 전부 → `PlantConverter` 로 변환해 저장. 호출 사이 0.3초 쉼, 관리자가 고친 변환값은 유지, 실패는 모아서 보고 |
| `PlantImportJob` | 관리자 화면의 "가져오기 시작"을 뒤에서(별도 스레드) 돌리고 진행 상황 제공. 한 번에 하나 |
| `RecommendationService` | 계절별 칸 결과 × 모든 식물 → `PlantScorer` 점수 → 상위 10개를 `recommendation` 에 저장. `allScores`: 모든 식물 순위(저장 안 함), `fit`: 한 식물의 칸별 점수 지도. 방·식물·가림 각도가 바뀌면 지움 |
| `MyPlantService` | 내 식물 등록·이동·삭제. 알림: 놓은 자리 점수가 50점 미만인 계절이 있으면 "더 알맞은 자리로" + 추천 자리, 겨울 실내 온도가 식물 최저온도보다 낮으면 경고 |
| `PreviewService` | **비회원 간단 예측**: 표준 방(4×3.5m, 창 180×150cm)을 창 방향으로 돌려 놓고, 고른 건물·층·세대 위치에서 가림 각도(건물+지형) → 계절별 창가·가운데·안쪽 햇빛 → 식물 상위 6개. 저장 안 함 |
| `PublicDataService` | 천문연 출몰시각(같은 날짜는 DB 에서), ASOS 기간 받기(날짜별 호출, 같은 시각은 갱신), 매일 10:47 어제 자료 자동 저장(`@Scheduled`), 우리 계산과 천문연 비교, 센서 분석용 `isNaturalLight`·`isClear` |
| `FileService` / `FileStorage` | 업로드: 종류별 확장자 확인 → 저장 이름(UUID)으로 `data/uploads/` 에 저장 → `upload_file` 기록. 다운로드·삭제는 주인(또는 관리자)만. 내려줄 형식은 브라우저가 보낸 값이 아니라 확장자로 정함 |
| `RoomReportService` | **리포트 PDF**(1. 집과 방 2. 계절별 햇빛 표·배치도 3. 식물 추천 4. 내 식물 + 계산 방법과 출처), 계절별 배치도 PNG, 칸별 CSV, ASOS CSV (CSV 는 엑셀용 BOM) |

### 3-9. calculator — 계산 (스프링 없는 순수 코드, 단위 테스트 대상)

| 파일 | 하는 일 · 공식 |
|---|---|
| `SolarCalculator` | NOAA 태양 위치 공식(Meeus 근사): 율리우스일 → 태양 황경·적위·균시차 → 시간각 → **고도·방위각**, 일출·남중·일몰. 기준값: 부산 정오 고도 춘분 54.7°, 하지 78.3°, 동지 31.5° |
| `SolarPosition` | 고도·방위각 값 (북 0°, 동 90°, 시계 방향) |
| `TmConverter` | **EPSG:5186 ↔ 위경도** (GRS80, 원점 38°N·127°E, 가산값 200,000 / 600,000m, Snyder 횡메르카토르 공식) |
| `Wkt` | 건물 외곽선 `MULTIPOLYGON` 문자열 → 점 목록 |
| `HeightRule` | 건물 최종 높이: 높이 0~200m 이고 층당 10m 이하 → 그대로(MEASURED) / 아니고 층수 있으면 층수 × 층고(공동 3.0·단독 3.5·그 외 3.6m, FLOOR_ESTIMATE) / 둘 다 없으면 3.5m(DEFAULT) |
| `FloorRule` | 고를 수 있는 최대 층: 지상층수, 없고 높이 실측이면 높이 ÷ 2.5m 올림, 둘 다 없으면 모름(막지 않음) |
| `ObstructionCalculator` | **건물 가림 각도**: 주변 건물 외곽선을 0.25m 간격 점으로 잘라, 점마다 방향(5° 칸)과 `atan((건물 높이 − 눈 높이) / 수평 거리)` 를 구해 칸마다 최댓값과 원인 건물·거리를 남김 |
| `TerrainModel` | 지형 격자(메모리), 네 점 **이중 선형 보간**으로 땅 높이 |
| `TerrainCalculator` | **산 가림 각도**: 5° 방향마다 300m~10km 를 격자 반 칸씩 나가며 `atan((땅 높이 − 곡률 낮아짐 − 눈 높이) / 거리)` 최댓값. 곡률 낮아짐 = 거리²/(2R)×(1−0.13 굴절). 300m 안은 건물 데이터가 맡음(DEM 에 건물이 섞여 있어서) |
| `BuildingFacade` | **창별 관측점**: 외곽선을 감싸는 최소 사각형의 긴 변 = 긴 축 → 세대 위치(−1~1)만큼 축을 따라 옮김 → 창 방향으로 나가 처음 만나는 외벽 + 0.5m. 오목한(ㄷ자) 건물도 처리. `canSeeOwnBuilding`: 관측점이 우리 건물 바깥이면 우리 건물도 가림물로 넣음(ㄷ자·ㄱ자의 다른 날개) |
| `ObstructionProfile` | 계산에 쓰는 72칸 가림 각도: 공공데이터 값(건물·지형 중 큰 값)과 센서 값. 센서 값이 있으면 센서 우선. `averageAngle(방향, ±폭)` |
| `Shape` | 방 다각형 넓이·방향 이름·저장 문자열 변환, 벽 방향 계산 |
| `SunlightCalculator` | **칸별 햇빛** (아래 5-3 자세히). 직사광: 15분마다 해가 창 앞쪽이고 가림 각도보다 높으면 창 네 모서리를 바닥에 투영 → 사각형 안 칸에 15분. 하늘빛: CIE 흐린 하늘 하늘 성분(창을 20cm 조각으로 적분, 가린 하늘은 건물 반사 20%) + BRE 내부 반사 → 주광률 × 실외 확산광(5,000 + 10,000 × sin 정오 고도) × 유리 0.75 × 커튼 투과율. `calculatePerWindow`: 창마다 다른 가림 각도 |
| `Seasons` | 날짜 → 계절 대표일 |
| `NongsaroCodes` | 농사로 코드표: 광요구도(055001 300~800 / 055002 800~1,500 / 055003 1,500~10,000 lux), 배치장소(창에서 거리), 겨울 최저온도 |
| `PlantConverter` | 농사로 코드 → 추천 기준: lux 범위, 하루 직사광(높은 광도만이면 최소 120분, 낮은 광도만이면 최대 60분, 중간이 최대면 180분), 창에서 거리, 겨울 최저 ℃ |
| `PlantScorer` | **식물 점수 0~100** (아래 5-4 자세히). 칸 점수 = 빛 × 직사광 × 창 거리, 방 점수 = 최고 자리 × 0.9 + 잘 맞는 자리 비율 × 10, 고정 자리 / 계절 이동 판단, 겨울 온도 경고 |

### 3-10. client — 외부 API

| 파일 | 설명 |
|---|---|
| `AddressApi` / `VworldAddressApi` | 브이월드 검색 API (`crs=EPSG:5186`, 도로명·지번). 키 `api.vworld.key` |
| `NongsaroApi` / `NongsaroHttpApi` | 농사로 `gardenList`·`gardenDtl` (http, `apiKey`). 첫 응답 원문을 `data/nongsaro-sample/` 에 남겨 형식 확인, 읽지 못한 응답은 `-error.xml` 로 |
| `NongsaroXml` | 농사로 XML 읽기 (외부 엔티티 XXE 막음), `resultCode` 확인, 목록·상세 → `NongsaroPlant`(사진 주소 조합 포함) |
| `PublicDataApi` / `PublicDataHttpApi` | 공공데이터포털: 천문연 출몰시각(XML), 기상청 ASOS 시간자료(JSON) |
| `PublicDataParser` | 위 응답 읽기 (박명 시각 "0652" → 06:52, 빈 값 처리) |

> 외부 API 는 모두 인터페이스로 두고, 테스트에서는 `@MockitoBean` 또는 로컬 가짜 서버로 바꿉니다.

### 3-11. tool — ① 데이터 생성 프로그램 (웹 서버 없이 실행 후 종료)

| 파일 | 설명 |
|---|---|
| `BuildingImporter` | `./gradlew importBuildings -Pgpkg=` · GeoPackage 를 SQLite 로 직접 열어 도형 레이어 찾기 → `GpkgGeometryReader` 로 외곽선 → 넓이·중심점·위경도, A2(PNU)·A4(법정동명)·A5(지번)·A9(용도)·A13(사용승인일)·A16(높이)·A24(건물명)·A25(동 이름)·A26·A27(층수) → `HeightRule` → 일괄 INSERT. 다시 실행하면 비우고 채운 뒤 집(house)을 동·지번으로 다시 연결 |
| `GpkgGeometryReader` | GeoPackage 도형 바이너리(GP 헤더 + WKB) 중 Polygon·MultiPolygon 읽기 (Z·M 은 버림, 바이트 순서 처리) |
| `PlantImportRunner` | `./gradlew importPlants` → `PlantImportService` |
| `TerrainImportRunner` | `./gradlew importTerrain -Pdem=` → `TerrainService.importFile` |
| `AscGridReader` | ESRI ASCII Grid(.asc) 머리말(ncols·nrows·xll/yll corner·center·cellsize·NODATA) + 값을 한 행씩 읽기 (파일이 커도 메모리 적게) |
| `WeatherCollectRunner` | `./gradlew collectWeather -Pfrom= -Pto=` → ASOS 저장 |

실행 구조: `build.gradle` 의 `runner()` 가 `--spring.main.web-application-type=none --plantspot.import=buildings` 처럼 넘기고, 각 Runner 는 `@ConditionalOnProperty(name="plantspot.import", havingValue=…)` 일 때만 만들어져 일을 끝내고 `System.exit`.

### 3-12. report — PDF·PNG

| 파일 | 설명 |
|---|---|
| `PdfWriter` | PDFBox 로 A4 에 위에서 아래로 쓰는 도구: 제목·문단(자동 줄바꿈)·표·그림·쪽 넘김 |
| `KoreanFont` | PDF 한글 글꼴 찾기: `app.report.font` → 윈도 맑은 고딕 → 맥 → 리눅스 나눔·Noto |
| `RoomPlanRenderer` | 방 평면도 + 칸 색 + 창문 + 표시점을 PNG 로 (글자 없이 그려 글꼴과 무관) |

---

## 4. 화면 파일 설명

### 4-1. 공통 (static/js, css, fragments)

| 파일 | 설명 |
|---|---|
| `templates/fragments/layout.html` | 머리말(head)·머리글(메뉴, 로그인 표시 `me`) 조각 |
| `static/js/api.js` | `api(url, {method, json, body})`: fetch + 세션 쿠키 + **CSRF 헤더**(처음에 `/api/auth/csrf` 로 받음) + 오류 JSON 의 message 로 예외. `escapeHtml`, `logout`, `fillUnitPosition`(동 안 위치 선택칸을 건물 긴 축 방위 이름으로 채움) |
| `static/js/address.js` | `addressSearch`(도로명 검색 상자), `buildingChooser`(한 지번의 건물 고르기: 동 번호 순·부속 시설 접기·이름 없는 건물 표시), `parcelLookup`(동·지번 직접 입력해도 건물 목록), `selectDong` |
| `static/js/roomplan.js` | 방 평면도와 칸 결과를 SVG 로 그리기 (햇빛·추천 화면 공용), 점수 색 `scoreColor` |
| `static/css/style.css` | 색 변수(밝은·어두운 모드), 카드·표·탭·뱃지·팝업(dialog) |
| 모든 템플릿 `<script th:inline="none">` | Thymeleaf 가 JS 의 `[[`·`[(` 를 인라인 문법으로 읽지 않게 |

### 4-2. 화면별 (templates)

| 화면 | 템플릿 | 부르는 API |
|---|---|---|
| 첫 화면 | `index.html` | - |
| 로그인·가입 | `login.html`, `signup.html` | `/api/auth/login`, `/api/auth/signup` |
| 간단 예측 | `try.html` | `/api/buildings/dongs`, `/api/address/search`, `/api/buildings`, `POST /api/preview` |
| 식물 검색·상세 | `plants/search.html`, `plants/detail.html` | `/api/plants`, `/api/plants/{id}` |
| 햇빛 에디터 | `editor.html` + `static/editor/*` | (회원이 방을 열 때) `/api/rooms/{id}`, `/api/houses/{id}`, `/api/houses/{id}/obstructions`, `/api/my-plants`, `/api/plants` |
| 우리 집 목록·등록 | `my/houses.html` | `/api/houses`, `/api/buildings…`, `/api/address/search` |
| 집 | `my/house.html` | `/api/houses/{id}`, `…/obstructions`(스카이라인 SVG), `…/rooms` |
| 방 | `my/room.html` | `/api/rooms/{id}`, `…/windows`, `…/window-obstructions`, `/api/sun/paths`, `/api/files`(평면도 올리기). **창별 스카이라인**: 원인 4색(우리 건물·옆 건물·산·센서), 창 뒤쪽 빗금, 동지·춘분·하지 해 경로, 계절별 해 드는 시간·원인별 가림 시간 |
| 계절별 햇빛 | `my/sunlight.html` | `/api/rooms/{id}/sunlight`, `…/sunlight/image`, `…/sunlight/csv` |
| 식물 추천 | `my/recommendations.html` | `…/plant-scores`(목록·관리 수준·검색), `…/recommendations/{plantId}`(적합도 지도), `/api/plants/{id}`(팝업), `…/refresh` |
| 내 식물 | `my/plants.html` | `/api/my-plants`, `…/alerts`, `/api/files`(사진) |
| 내 파일 | `my/files.html` | `/api/files` |
| 관리자 | `admin/members.html`, `admin/plants.html`, `admin/public-data.html`, `admin/nav.html` | `/api/admin/**`, `/api/sun-events/compare`, `/api/weather/hours` |
| 오류 | `error.html` | 403·404 등 화면 오류 |

### 4-3. ② 평면도 햇빛 에디터 (static/editor)

| 파일 | 설명 |
|---|---|
| `solar.js` | 해 위치 — 서버 `SolarCalculator` 와 **같은 공식** |
| `light.js` | 창 투영(`sunPatch`)·하루 칸 지도(`dayAt`)·하늘빛·식물 점수(`plantScore`) — 서버 `SunlightCalculator`·`PlantScorer` 와 같은 방법 |
| `editor.js` | 캔버스 편집기: 상태(방 꼭짓점·창문·식물·날짜·시각) → `recompute()` → `draw()`. 직사각형·ㄱ자·직접 그리기, 꼭짓점·창문·식물 끌기, 벽 두 번 눌러 꼭짓점 추가, Delete, 날짜·시각 슬라이더와 하루 재생, 하루 직사광 지도, 식물 놓기(계절별 점수), 배치도 PNG·방 파일(JSON) 내보내기·불러오기, 비회원은 localStorage 에 자동 저장, 회원은 `?roomId=` 로 우리 방·실제 가림 각도·센서 보정 전후 겹쳐 보기 |

DB 없이 브라우저에서만 돌아가는 독립 프로그램이라 ② 에 해당합니다. 서버와 같은 결과가 나오는지는 `src/test/js/editor-parity.js` 로 확인합니다.

---

## 5. 핵심 기능 따라가기

### 5-1. 로그인 (세션 + CSRF)
1. 화면이 처음 쓰기 요청을 할 때 `api.js` 가 `GET /api/auth/csrf` 로 토큰을 받음
2. `POST /api/auth/login` (JSON) → `AuthenticationManager` 가 `MemberUserDetailsService` 로 회원을 읽고 BCrypt 로 비밀번호 확인
3. 성공하면 `SecurityContext` 를 세션에 저장(`SecurityContextRepository`) → 이후 요청은 세션 쿠키로 `LoginMember` 가 됨
4. 쓰기 요청마다 그 토큰을 헤더(이름도 서버가 알려 줌, 기본 `X-XSRF-TOKEN`)에 붙임. 없으면 403

### 5-2. 집 등록 → 가림 각도
1. 화면: 도로명 검색(`AddressService`) 또는 동·지번 → `GET /api/buildings` → 건물(동) 고르기, 동 안 위치 선택
2. `POST /api/houses` → `HouseService.createHouse`: `BuildingService.choose` 로 건물 확인 → `FloorRule` 로 층 검증 → `House` 저장
3. 집 화면이 `GET /api/houses/{id}/obstructions` → `ObstructionService.profile`: 저장된 값이 없으면 `recalculate`
   - 우리 건물을 뺀 반경 300m 건물 → (지형이 있으면) 건물 높이 + (그 건물 땅 높이 − 우리 땅 높이) → `ObstructionCalculator`
   - 지형이 있으면 `TerrainCalculator` 로 300m~10km 산 스카이라인
   - 72칸 × (BUILDING, TERRAIN) 행 저장
4. 층·세대 위치를 바꾸면 `HouseChanged` 이벤트 → 가림 각도·햇빛·추천을 지우고 다음 조회 때 다시 계산

### 5-3. 칸별 햇빛 (`SunlightService` → `SunlightCalculator`)
1. 방의 창문마다 `ObstructionService.windowProfile(집, 창 방향)`: 창별 관측점(`BuildingFacade`)에서 건물·지형 가림 각도(+ 집의 센서 실측). 관측점이 외벽 바깥이라 **우리 건물도 넣어** ㄷ자 건물의 반대쪽 날개 그림자까지 계산 (창이 붙은 벽은 관측점 뒤라 창 뒤쪽에만 잡혀 영향 없음)
2. 방 바닥을 25cm 칸으로 나눔
3. **직사광**: 0시~24시 15분마다 해 위치 → 창마다 해가 창 앞쪽(±90°)이고 그 방향 가림 각도보다 높으면 창 네 모서리를 해 반대 방향으로 바닥(또는 선반 높이)에 투영한 사각형 → 사각형 안 칸에 15분 (빛이 방의 다른 벽을 지나면 제외)
4. **하늘빛**: 칸마다 창 조각(20cm)에서 보이는 하늘을 CIE 흐린 하늘 휘도로 적분(가린 부분은 20%) + 내부 반사 → 주광률 × 그날 실외 확산광 × 유리 × 커튼 × (안쪽 창 선보다 안쪽이면 안쪽 창 투과율, 발코니+거실 구조)
5. **밝기 단계**: `LightLevel.of(lux, 직사광분)`
6. 계절 대표일 4개는 `sunlight_summary`·`sunlight_cell` 에 저장, 다음엔 읽기만

### 5-4. 식물 추천 (`RecommendationService` → `PlantScorer`)
1. 식물 변환값(`PlantConverter`): lux 범위, 하루 직사광 최소·최대, 창에서 거리 범위, 겨울 최저 ℃
2. **칸 점수** = 100 × 빛 × 직사광 × 거리
   - 빛: lux 가 범위(로그 눈금) 가운데 절반이면 1, 끝이면 0.8, 밖이면 더 낮게
   - 직사광: 최소보다 적으면 비율만큼, 최대보다 많으면 감점
   - 거리: 농사로 배치장소 범위에서 벗어난 만큼(150cm 이상이면 0.7)
3. **고정 자리**: 네 계절 최저 점수가 가장 높은 칸이 70점 이상이면, 아니면 계절마다 가장 좋은 칸(계절 이동, 수고만큼 ×0.9)
4. **방 점수** = 자리 점수 × 0.9 + (1년 내내 80점 이상인 칸 비율) × 10 — 범위가 넓은 식물이 모두 100점이 되는 것을 막음
5. 겨울 실내 최저 온도 < 식물 최저 온도면 경고와 ×0.7
6. 상위 10개를 저장, 화면은 모든 식물 점수(`plant-scores`)로 관리 수준 거르기·이름 찾기

### 5-5. 계산 결과 지우기 (이벤트)
| 바뀐 것 | 이벤트 | 지우는 곳 |
|---|---|---|
| 층·층 높이·세대 위치 | `HouseChanged` | 가림 각도(BUILDING·TERRAIN), 창별 캐시, 칸별 햇빛, 추천 |
| 방 모양·창문·커튼 | `RoomChanged` | 칸별 햇빛, 추천 |
| 센서 실측 저장·삭제 | `ObstructionChanged` | 칸별 햇빛, 추천, 창별 캐시 |
| 식물 변환값 | `PlantChanged` | 추천 |
| 집·방·식물 삭제 | `…Deleting` | 딸린 행 전부 (외래 키 때문에 먼저) |

### 5-6. 파일 업로드·다운로드
1. `POST /api/files` (multipart: file, category, roomId) → `FileService`: 종류별 확장자 확인, 10MB 제한
2. `FileStorage` 가 `data/uploads/` 에 UUID 이름으로 저장, `upload_file` 에 원래 이름·크기·주인 기록
3. `GET /api/files/{id}/content`: 주인(또는 관리자)만, `Content-Disposition` 에 원래 이름(한글 인코딩)
4. 리포트: `GET /api/rooms/{id}/report` → `RoomReportService` 가 `PdfWriter` + `RoomPlanRenderer` 로 PDF 를 만들어 바로 내려줌

### 5-7. 데이터 생성 프로그램 실행 구조
`./gradlew importTerrain -Pdem=…` → Gradle `JavaExec` 가 `PlantSpotApplication` 을 웹 서버 없이 실행 → `TerrainImportRunner`(조건부 빈) → `TerrainService.importFile` → 끝나면 `System.exit`. SQLite 는 한 번에 한 프로그램만 쓰므로 **서버를 끄고** 실행.

---

## 6. 테스트

`./gradlew build` 로 **187개** 실행 (메모리 SQLite, `src/test/resources/application.properties`).

| 패키지 | 무엇을 | 예 |
|---|---|---|
| calculator (69) | 계산 기준값 | 부산 정오 해 고도(춘분 54.7°·하지 78.3°·동지 31.5°), EPSG:5186 변환, 건물·산 가림 각도, 창 투영 위치, 칸별 직사광, 관측점(판상형·ㄷ자), 층 규칙, 점수 분포 |
| controller (71) | REST·권한·화면 | 가입·로그인·401/403, 집·방·창문, 남의 집 403, 층 검증 400, 끝 세대 vs 반대쪽 끝, 추천·모든 식물 점수, 파일 업·다운로드, PDF·PNG·CSV |
| client (18) | 외부 응답 읽기 | 농사로 XML(XXE 막기), 천문연·ASOS, 브이월드 주소, 응답 원문 저장 |
| service (13) | 공공데이터·지형 | ASOS 저장·맑음 판단, 지형 파일 자르기·보간·경사지 건물 |
| tool (12) | 가져오기 | GeoPackage 도형, 건물 가져오기 결과, ASCII Grid |
| config·기타 (4) | DB·시작 | 모든 id 가 integer, 예전 DB 제약 고치기, 앱 시작 |
| `src/test/js/editor-parity.js` | ② 에디터 | 브라우저 JS 와 서버 계산(해 위치·직사광·lux) 일치 |

외부 API 는 가짜(`@MockitoBean`, 로컬 `HttpServer`)로 바꾸고, 건물은 `support/TestBuildings` 로 넣습니다.

---

## 7. 작업하며 알게 된 주의점

- **SQLite 자동 번호**: id 칸 타입이 정확히 `integer` 여야 rowid 가 됨 → `@Column(columnDefinition = "integer")`, 테스트 DB 도 `ddl-auto=update`
- **벌크 delete 대신 엔티티 단위 삭제**: SQLite 가 번호를 재사용해 영속성 컨텍스트와 충돌 (지형 점처럼 엔티티로 읽지 않는 것만 JDBC 로 한 번에)
- **한 번에 한 프로그램만 쓰기**: 가져오기는 서버를 끄고 실행
- **enum check 제약**: `ddl-auto=update` 는 제약을 안 바꿈 → `SchemaUpgrade`
- **이클립스**: `@PathVariable("id")` 처럼 이름 필수, Gradle wrapper 사용
- **Thymeleaf**: 스크립트에 `th:inline="none"`
- **외부 API 한도**: 고도 API(Open-Meteo)는 점 하나가 호출 1번이라 5.6만 점에서 시간당 한도 초과 → DEM 파일 방식으로 바꿈
- **Copernicus DEM 은 표면 높이**(건물·나무 포함) → 300m 안은 지형으로 세지 않음
- **사하구 건물 데이터**: 높이 없음 62%, 동 이름 없는 건물 많음, 입력 오류(높이 15,400m) → `HeightRule`, "이름 없는 건물" 표시

---

## 8. 한계와 남은 일

- **아두이노 센서 보정 (예정)**: 실측 가림 각도 저장 API(`PUT /api/houses/{id}/obstructions/measured`)와 판단 함수(`isClear`, `isNaturalLight`)는 준비됨. 수집 프로그램(USB 시리얼)·기록 파일 업로드 분석·방 안 센서 비율 보정이 남음
- **가림 각도**: 우리 건물은 외곽선 하나·지붕 높이 하나로 보므로 층마다 모양이 다른 건물(넓은 저층 상가 위 아파트)이나 발코니·처마는 정확하지 않음. 나무·간판은 공공데이터에 없어 센서로 보정
- **하늘빛 lux**: 흐린 하늘 기준 추정이라 방향(남향 반사광) 차이가 작게 나옴 → 센서 비율로 보정 예정
- **지형**: 30m DEM 이라 아주 가까운 언덕·옹벽은 거침
- **집 화면 스카이라인**은 건물 가운데 기준(햇빛 계산은 창별 관측점)
