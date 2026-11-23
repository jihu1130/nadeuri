# 🌤️ 나들이 (Nadeuri)

지도와 날씨를 연동해서 **동네 사람들 또는 같은 목적을 가진 사람들과 특정 장소에서 함께할 모임(나들이, 행사, 산책, 러닝 등)을 찾는** 웹 서비스입니다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| 주변 모임 지도 | 내 위치 반경 5km 안의 모임을 카카오맵 위에 이모지 핀으로 표시하고, 아래쪽 목록으로도 보여 줍니다 |
| 지금 날씨 | 내 위치의 기상청 초단기실황(실제 관측한 기온·습도·강수)에 단기예보의 하늘상태·강수확률을 더해 상단에 표시합니다. 관측값을 못 받으면 예보만 보여 줍니다 |
| 모임 만들기 | 카카오 장소 검색이나 지도 클릭으로 장소를 고르고 활동 종류, 시작 시간, 진행 시간(30분~12시간), 인원을 입력합니다 |
| 모임 상태 | 시작·끝나는 시간과 인원으로 **모집 중 / 모집 마감 / 진행 중 / 종료**를 계산해 배지로 보여 줍니다. 진행 중인 모임은 주변 검색에 남고 늦게 합류할 수 있으며, 끝난 모임은 검색에서 빠집니다 |
| 모임 상세 | 모임 시각의 날씨 예보를 보여 주고, 야외 활동에 비 예보가 있으면 ☔ 경고를 띄웁니다. 카카오맵 길찾기도 연결됩니다 |
| 로그인 | 아이디 + 비밀번호로 가입·로그인하거나 카카오로 로그인합니다. 둘러보기는 누구나 가능하고, 모임 만들기와 참여는 로그인이 필요합니다 |
| 참여하기 | 정원 초과와 중복 참여를 막습니다. 회원 ID로 참여 기록을 남깁니다 |

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 프레임워크 | Java 21, Spring Boot 3.3.5 (Web, Data JPA, Validation, Cache) |
| 화면 | Thymeleaf, 순수 JavaScript, CSS |
| 로그인 | Spring Security (아이디 폼 로그인, 비밀번호 BCrypt 해시) + OAuth2 Client (카카오 로그인) |
| 지도 | 카카오맵 JavaScript SDK (지도, 장소 검색, 주소 변환) |
| 날씨 | 기상청 API허브의 동네예보 조회서비스 `VilageFcstInfoService_2.0/getVilageFcst` |
| DB | MySQL 8 (운영·로컬), H2 (테스트) |
| 캐시 | Caffeine (격자와 발표시각 단위로 날씨를 캐싱) |
| 빌드 | Gradle 8.14 (Wrapper 포함) |

## 실행 방법

### 1. 준비물
- JDK 21
- MySQL 8 (포트 3306). Docker를 쓴다면 `.env.example`을 `.env`로 복사하고 `docker compose up -d`를 실행하세요.
- 카카오 JavaScript 키 (카카오맵)
- 기상청 API허브 인증키 (날씨)

### 2. 로컬 설정 파일
`src/main/resources/application-local.yml.example`을 `application-local.yml`로 복사한 뒤 값을 채우세요. 이 파일은 `.gitignore`에 들어 있어서 저장소에 올라가지 않습니다.

```yaml
spring:
  datasource:
    username: root
    password: "비밀번호"
kma:
  service-key: "기상청 API허브 authKey"
kakao:
  javascript-key: "카카오 JavaScript 키"
  rest-api-key: "카카오 REST API 키"        # 로그인용
  client-secret: "카카오 클라이언트 시크릿"  # 로그인용
```

### 3. 실행
- IntelliJ에서 `NadeuriApplication`을 실행합니다. 기본 프로필은 `local`입니다.
- 또는 터미널에서 `./gradlew bootRun`을 실행합니다.
- 실행한 뒤 http://localhost:8888 에 접속하세요.
- DB가 비어 있으면 여의도·마포 일대 샘플 모임 5개가 자동으로 만들어집니다.
- MySQL 없이 실행하려면 `--spring.profiles.active=h2` 옵션을 주세요.

### 4. 테스트
```bash
./gradlew test
```
> Windows에서 프로젝트 경로에 한글이 있으면(예: `바탕화면\나들이\nadeuri`) Gradle 테스트가 `ClassNotFoundException`으로 실패합니다. 영문 경로로 옮기거나 복사해서 실행하세요. IntelliJ에서 앱을 실행하는 데는 문제가 없습니다.

## DB 테이블 관리 (Flyway)
- 테이블 구조는 `src/main/resources/db/migration/` 안의 SQL 파일로 관리합니다. 앱이 뜰 때 Flyway가 아직 적용하지 않은 파일을 순서대로 실행합니다.
- `V1__init.sql`: 처음 테이블(gathering, participant, users)
- `V2__add_gathering_end_at.sql`: 모임 끝나는 시간 `end_at` 추가 (기존 모임은 시작 + 2시간으로 채움)
- **테이블을 바꿀 때는 기존 파일을 고치지 말고** `V2__설명.sql`처럼 새 파일을 추가하세요. 예: `V2__add_gathering_cover_image.sql`
- Hibernate는 테이블을 직접 바꾸지 않고 코드와 맞는지 **확인만**(`ddl-auto: validate`) 합니다. 맞지 않으면 앱이 뜨지 않으니, 엔티티를 바꾸면 SQL 파일도 같이 추가하세요.
- Flyway 도입 전부터 쓰던 DB는 V1을 실행하지 않고 "이미 V1"로 기록만 합니다(`baseline-on-migrate`). 데이터는 그대로입니다.
- H2(테스트)는 Flyway를 끄고 Hibernate가 테이블을 만듭니다.

## 외부 API 설정

### 카카오맵
1. [Kakao Developers](https://developers.kakao.com)에서 앱을 만듭니다. "앱 대표 도메인"은 비워 두세요. localhost를 받아 주지 않습니다.
2. **플랫폼 키 → JavaScript 키 → JavaScript SDK 도메인**에 `http://localhost:8888`을 등록합니다.
3. **제품 설정 → 카카오맵 → 사용 설정**을 **ON**으로 바꿉니다. 이걸 켜지 않으면 지도가 회색으로만 나와요.

### 카카오 로그인
1. **제품 설정 → 카카오 로그인 → 사용 설정**을 **ON**으로 바꿉니다.
2. **플랫폼 키 → REST API 키**에서 아래를 설정합니다.
   - 카카오 로그인 리다이렉트 URI: `http://localhost:8888/login/oauth2/code/kakao`
   - 클라이언트 시크릿: 사용함으로 두고 값을 복사합니다.
3. **제품 설정 → 카카오 로그인 → 동의항목**에서 **닉네임(profile_nickname)**, **프로필 사진(profile_image)**을 설정합니다.
4. REST API 키와 클라이언트 시크릿을 `application-local.yml`의 `kakao.rest-api-key`, `kakao.client-secret`에 넣고 앱을 다시 시작합니다.
5. 키가 비어 있어도 앱은 뜹니다. 이때는 로그인 화면에 "설정되지 않았어요" 안내가 나오고, 모임 만들기와 참여만 할 수 없습니다.

### 기상청 (API허브)
1. [기상청 API허브](https://apihub.kma.go.kr)에 가입해서 인증키(authKey)를 발급받습니다.
2. **예특보 → 단기예보 → 동네예보(초단기실황·초단기예보·단기예보) 조회**에서 기능마다 따로 활용신청을 해야 합니다.
   - 필수: `getVilageFcst` (단기예보조회)
   - 승인된 상태: `getUltraSrtNcst` (초단기실황)
3. 승인 후 반영까지 몇 분 걸릴 수 있습니다. 그동안은 HTTP 403 "활용신청이 필요한 API 입니다"가 나옵니다.
4. 인증키가 없어도 앱은 정상 동작하고, 날씨만 "예보 없음"으로 표시됩니다.

> 공공데이터포털 키를 쓰려면 `kma.base-url`을 `https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst`로, `kma.key-param`을 `serviceKey`로 바꾸고 Decoding 키를 넣으세요.

## 운영 배포
설정 파일 대신 환경변수로 값을 넣습니다.

| 환경변수 | 설명 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | DB 접속 정보 (기본값 없음) |
| `KMA_SERVICE_KEY` | 기상청 인증키 |
| `KAKAO_JS_KEY` | 카카오 JavaScript 키 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 카카오 로그인용 키 (운영 도메인의 리다이렉트 URI도 따로 등록해야 합니다) |
| `DDL_AUTO` | 기본값은 `validate`입니다. 테이블은 앱이 뜰 때 Flyway가 `db/migration` 의 SQL로 만들고 바꿉니다 |

## 프로젝트 구조

```
nadeuri
├─ src/main/java/com/example/nadeuri
│  ├─ gathering                 모임
│  │  ├─ controller             GatheringPageController(화면), GatheringApiController(JSON)
│  │  ├─ service                GatheringService (반경 검색, 생성, 참여)
│  │  ├─ repository             GatheringRepository, ParticipantRepository
│  │  ├─ domain                 Gathering, Participant (엔티티), ActivityTag
│  │  ├─ dto                    GatheringCreateRequest, GatheringResponse
│  │  └─ exception              AlreadyJoinedException
│  ├─ weather                   날씨
│  │  ├─ controller             WeatherApiController
│  │  ├─ service                WeatherService (위치·시각에 맞는 예보 선택)
│  │  ├─ client                 KmaForecastClient (기상청 API 호출, 캐싱)
│  │  ├─ domain                 KmaGrid (위경도→격자), WeatherInfo
│  │  └─ dto                    WeatherResponse
│  ├─ user                      회원 / 카카오 로그인
│  │  ├─ controller             LoginController (/login, /signup 화면)
│  │  ├─ service                UserService (아이디 가입, 카카오 가입·프로필 갱신),
│  │  │                         LocalUserDetailsService (아이디 로그인), KakaoOAuth2UserService (카카오 로그인)
│  │  ├─ repository             UserRepository
│  │  ├─ domain                 User (엔티티, 테이블 이름 users. provider = local | kakao)
│  │  ├─ dto                    SignUpRequest (가입 폼), KakaoProfile (카카오 응답 해석)
│  │  └─ exception              DuplicateEmailException
│  └─ global                    공통
│     ├─ config                 SecurityConfig (접근 권한, 카카오 OAuth2), ViewModelAdvice (화면 공통 값: 카카오 키, 로그인 사용자)
│     ├─ security               LoginUser (세션에 저장되는 로그인 정보)
│     ├─ util                   Times (KST 시간)
│     └─ init                   SampleDataLoader (샘플 데이터)
├─ src/main/resources
│  ├─ templates                 index.html, login.html, signup.html, gathering/new.html, gathering/detail.html, fragments/auth.html
│  ├─ static/css/app.css
│  ├─ application.yml           공통 설정 (비밀값 없음)
│  └─ application-local.yml     로컬 비밀값 (git 제외)
├─ doc
│  ├─ user                      사람이 읽는 문서 (작업 일지: YYMMDD.md)
│  └─ ai                        AI 작업자용 문서 (claude.md, rules.md)
└─ CLAUDE.md                    AI 작업자 진입점 (doc/ai 안내)
```

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/` | 홈 (지도 + 주변 모임) |
| GET | `/login` | 로그인 화면 (아이디 로그인 폼, 카카오 로그인 버튼) |
| POST | `/login` | 아이디 로그인 (`username`, `password`. Spring Security가 처리) |
| GET | `/signup` | 회원가입 화면 |
| POST | `/signup` | 회원가입 (`username`, `nickname`, `password`, `passwordConfirm`) |
| GET | `/oauth2/authorization/kakao` | 카카오 로그인 시작 (Spring Security가 처리) |
| POST | `/logout` | 로그아웃 |
| GET | `/gatherings/new` | 모임 만들기 화면 🔒 |
| POST | `/gatherings` | 모임 생성 🔒 |
| GET | `/gatherings/{id}` | 모임 상세 (날씨 예보 포함) |
| GET | `/gatherings/{id}/sign-in` | 로그인한 뒤 상세 화면으로 돌아오기 🔒 |
| POST | `/gatherings/{id}/join` | 참여 (끝나기 전까지. 진행 중이면 늦게 합류) 🔒 |
| POST | `/gatherings/{id}/leave` | 참여 취소 (시작 전, 주최자 제외) 🔒 |
| POST | `/gatherings/{id}/delete` | 모임 삭제 (주최자만) 🔒 |
| GET | `/gatherings/{id}/edit` | 모임 수정 화면 (주최자만, 시작 전) 🔒 |
| POST | `/gatherings/{id}/edit` | 모임 수정 저장 🔒 |
| GET | `/me/gatherings` | 내 모임 (만든 모임 / 참여한 모임) 🔒 |
| GET | `/api/weather/forecast?lat=&lng=` | 날짜별·시간별 예보 JSON (모임 시간 고르기용) |
| GET | `/api/gatherings?lat=&lng=&radius=` | 끝나지 않은 주변 모임 JSON (반경 100~20,000m, 좌표가 범위를 벗어나면 400). `endAt`, `status`(RECRUITING·FULL·ONGOING·ENDED), `statusLabel` 포함 |
| GET | `/api/weather?lat=&lng=` | 지금 날씨 JSON (기온·습도·1시간 강수량은 초단기실황 관측값, 하늘상태·강수확률은 예보. `observed`, `observedAt` 포함) |

🔒 표시는 로그인이 필요합니다. 로그인하지 않았으면 `/login`으로 이동합니다. POST 요청에는 CSRF 토큰이 필요하고, Thymeleaf의 `th:action`이 토큰을 자동으로 넣어 줍니다.

## 로드맵
- [x] 지도와 주변 모임 검색 (반경 검색, 날짜변경선 처리)
- [x] 기상청 단기예보 연동
- [x] 카카오맵, 장소 검색, 길찾기 링크
- [x] 동시성을 고려한 참여 처리 (원자적 증가, 중복 참여 차단)
- [x] 홈 화면 "지금 날씨"를 초단기실황(`getUltraSrtNcst`)으로 표시
- [x] 카카오 로그인 (Spring Security OAuth2). 게스트 쿠키 대신 회원 ID로 참여 기록
- [x] 아이디 + 비밀번호 회원가입·로그인
- [x] 지도에서 내 위치로 바로 이동 (지도 안 버튼, 위치 표시 강조, 위치 오류 안내)
- [ ] 아이디 계정과 소셜 계정 연결, 비밀번호 찾기(이메일 등록 필요), 로그인 시도 횟수 제한
- [x] 모임 상세에 주최자·참여자 목록, 내 모임 화면, 참여 취소, 모임 삭제
- [x] 모임 수정 (주최자)
- [x] 지도 이동 후 "이 지역에서 다시 검색"
- [ ] WebSocket(STOMP) 모임 채팅
- [ ] 데이터가 늘어나면 MySQL 공간 인덱스(`ST_Distance_Sphere`)로 검색 전환
- [x] DB 마이그레이션 도구 도입 (Flyway)
- [x] 한국어 오류 화면 (404·400·403·500)
- [x] 모임 끝나는 시간과 상태 (모집 중·모집 마감·진행 중·종료)
- [ ] 끝난 모임 후기, "다음에 또 만나요"

## 작업 기록
날짜별 작업 내용은 [`doc/user`](doc/user) 폴더에 `YYMMDD.md` 형식으로 남깁니다.
