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
| 모임 채팅 | 모임에 참여한 사람(주최자 포함)끼리 실시간으로 대화합니다. 지난 메시지를 불러오고, 연결이 끊기면 자동으로 다시 연결해 놓친 메시지를 채웁니다 |

## 기술 스택

| 영역 | 사용 기술 |
|---|---|
| 언어 / 프레임워크 | Java 21, Spring Boot 3.3.5 (Web, Data JPA, Validation, Cache) |
| 화면 | Thymeleaf, 순수 JavaScript, CSS |
| 로그인 | Spring Security 세션 로그인 (아이디 폼 로그인, 비밀번호 BCrypt 해시, "로그인 상태 유지" 14일 — DB 토큰) + OAuth2 Client (카카오·구글 로그인) |
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
google:
  client-id: "구글 클라이언트 ID"            # 구글 로그인용
  client-secret: "구글 클라이언트 보안 비밀번호"
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
- `V3__create_chat_message.sql`: 모임 채팅 메시지 테이블 `chat_message`
- `V4__add_public_ids.sql`: 모임·회원에 주소용 무작위 UUID(`public_id`) 추가
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
5. 키가 비어 있어도 앱은 뜹니다. 이때는 카카오 버튼만 나오지 않습니다. 소셜 로그인 키가 하나도 없으면 아이디 로그인만 쓸 수 있습니다.

### 구글 로그인
1. [Google Cloud Console](https://console.cloud.google.com/)에서 프로젝트를 만들고 **API 및 서비스 → OAuth 동의 화면**을 설정합니다. (사용자 유형: 외부)
2. **사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**에서 유형을 **웹 애플리케이션**으로 고릅니다.
3. **승인된 리디렉션 URI**에 `http://localhost:8888/login/oauth2/code/google`을 추가합니다.
4. 클라이언트 ID와 클라이언트 보안 비밀번호를 `application-local.yml`의 `google.client-id`, `google.client-secret`에 넣고 앱을 다시 시작합니다.
5. 요청하는 권한은 `profile`(이름·프로필 사진)뿐입니다. 이메일은 받지 않습니다.
6. 동의 화면이 "테스트" 상태이면 **테스트 사용자로 등록한 계정만** 로그인할 수 있습니다. 둘 중 하나라도 비어 있으면 구글 버튼만 나오지 않습니다.

### AI 도우미 (Google Gemini, 무료 등급)
1. [Google AI Studio](https://aistudio.google.com)에서 **Get API key**로 키를 받습니다.
2. `application-local.yml`의 `gemini.api-key`에 넣고 앱을 다시 시작합니다. (운영: 환경변수 `GEMINI_API_KEY`, 모델을 바꾸려면 `GEMINI_MODEL`, 기본 `gemini-3.5-flash-lite`)
3. 키가 비어 있으면 도우미 버튼이 나오지 않습니다.
4. 무료 등급은 입력 내용이 구글 서비스 개선에 쓰일 수 있어서, 회원 닉네임·아이디는 보내지 않고 화면에 "개인정보는 적지 마세요" 안내를 띄웁니다. 무료 한도는 AI Studio의 사용량 화면에서 확인하세요.

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
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | 구글 로그인용 키 (운영 도메인의 리다이렉트 URI를 Google Cloud Console에 따로 등록해야 합니다. 운영은 HTTPS 필요) |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | 운영(HTTPS)에서 `true`: 세션 쿠키를 HTTPS에서만 보냅니다 ("로그인 상태 유지" 쿠키는 HTTPS면 자동으로 그렇게 됩니다) |
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
│  ├─ user                      회원 / 카카오·구글 로그인
│  │  ├─ controller             LoginController (/login, /signup 화면)
│  │  ├─ service                UserService (아이디 가입, 소셜 가입·프로필 갱신),
│  │  │                         LocalUserDetailsService (아이디 로그인), SocialOAuth2UserService (카카오·구글 로그인)
│  │  ├─ repository             UserRepository
│  │  ├─ domain                 User (엔티티, 테이블 이름 users. provider = local | kakao | google)
│  │  ├─ dto                    SignUpRequest (가입 폼), SocialProfile (카카오·구글 응답 해석)
│  │  └─ exception              DuplicateEmailException
│  └─ global                    공통
│     ├─ config                 SecurityConfig (접근 권한, 카카오·구글 OAuth2), ViewModelAdvice (화면 공통 값: 카카오 키, 로그인 사용자)
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
| GET | `/login` | 로그인 화면 (아이디 로그인 폼, 카카오·구글 로그인 버튼) |
| POST | `/login` | 아이디 로그인 (`username`, `password`, 선택 `remember-me`. Spring Security가 처리) |
| GET | `/signup` | 회원가입 화면 |
| POST | `/signup` | 회원가입 (`username`, `nickname`, `password`, `passwordConfirm`) |
| GET | `/oauth2/authorization/kakao` | 카카오 로그인 시작 (Spring Security가 처리) |
| GET | `/oauth2/authorization/google` | 구글 로그인 시작 (Spring Security가 처리) |
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
| POST | `/gatherings/{id}/review` | 끝난 모임 후기 쓰기·고치기 (`rating` 1~5, `content` 200자). 참여자만, 끝나고 14일 안 🔒 |
| POST | `/gatherings/{id}/review/delete` | 내 후기 지우기 🔒 |
| POST | `/gatherings/{id}/meet-again` | 함께한 사람에게 "다음에 또 만나요" 보내기·거두기 (`to` = 회원 공개 UUID) 🔒 |
| GET | `/users/{회원 UUID}` | 회원 프로필: 자기소개·평판, 연 모임·참여한 끝난 모임(본인이 고른 공개 범위대로) |
| POST | `/me/profile/visibility` | 활동 기록 공개 범위 (`hosted`, `joined` = PUBLIC·MEMBERS·PRIVATE) 🔒 |
| GET | `/gatherings/{id}/chat` | 모임 채팅 화면 (참여자만) 🔒 |
| GET | `/api/gatherings/{id}/messages` | 지난 채팅 메시지 JSON (최근 50개, `?before=id` 더 이전, `?after=id` 그 뒤). 참여자가 아니면 403 |
| WS | `/ws/gatherings/{id}/chat` | 채팅 WebSocket. 보내기 `{"content":"..."}`, 받기 `{"type":"message","message":{...}}` / `{"type":"error","message":"..."}`. 내 메시지 삭제 `{"type":"delete","id":N}` → 모두에게 `{"type":"deleted","id":N}`, 수정 `{"type":"edit","id":N,"content":"..."}` → `{"type":"edited","message":{...}}` 🔒 |
| GET | `/api/gatherings/{id}/messages/{메시지 id}/revisions` | 메시지 수정 기록 (이전 내용들 + 지금 내용, 오래된 순). 참여자만 🔒 |
| GET | `/api/weather/forecast?lat=&lng=` | 날짜별·시간별 예보 JSON (모임 시간 고르기용) |
| GET | `/api/gatherings?lat=&lng=&radius=` | 끝나지 않은 주변 모임 JSON (반경 100~20,000m, 좌표가 범위를 벗어나면 400). `endAt`, `status`(RECRUITING·FULL·ONGOING·ENDED), `statusLabel` 포함 |
| POST | `/api/assistant/chat` | AI 도우미 질문 (`{"messages":[{"role":"user","text":"..."}],"lat":..,"lng":..}` → `{"reply":"..."}`). 로그인 필요(아니면 401), 1분 6번·하루 100번(넘으면 429), Gemini 키가 없거나 오류면 503 🔒 |
| GET | `/api/weather?lat=&lng=` | 지금 날씨 JSON (기온·습도·1시간 강수량은 초단기실황 관측값, 하늘상태·강수확률은 예보. `observed`, `observedAt` 포함) |

🔒 표시는 로그인이 필요합니다. 로그인하지 않았으면 `/login`으로 이동합니다. POST 요청에는 CSRF 토큰이 필요하고, Thymeleaf의 `th:action`이 토큰을 자동으로 넣어 줍니다.

주소의 `{id}`는 모임의 **UUID**입니다 (예: `/gatherings/19b80fb1-6fbf-4450-8bcb-cefbbd653124`). 예전처럼 숫자를 넣으면 400, 없는 UUID는 404입니다.

## 로드맵
- [x] 지도와 주변 모임 검색 (반경 검색, 날짜변경선 처리)
- [x] 기상청 단기예보 연동
- [x] 카카오맵, 장소 검색, 길찾기 링크
- [x] 동시성을 고려한 참여 처리 (원자적 증가, 중복 참여 차단)
- [x] 홈 화면 "지금 날씨"를 초단기실황(`getUltraSrtNcst`)으로 표시
- [x] 카카오 로그인 (Spring Security OAuth2). 게스트 쿠키 대신 회원 ID로 참여 기록
- [x] 아이디 + 비밀번호 회원가입·로그인
- [x] 구글 로그인 (2026-11-25. 실제 구글 계정 확인은 키 설정 후)
- [x] 지도에서 내 위치로 바로 이동 (지도 안 버튼, 위치 표시 강조, 위치 오류 안내)
- [ ] 아이디 계정과 소셜 계정 연결, 비밀번호 찾기(이메일 등록 필요), 로그인 시도 횟수 제한
- [x] 모임 상세에 주최자·참여자 목록, 내 모임 화면, 참여 취소, 모임 삭제
- [x] 모임 수정 (주최자)
- [x] 지도 이동 후 "이 지역에서 다시 검색"
- [x] 모임 채팅 (WebSocket)
- [ ] 채팅 안 읽은 메시지 표시, 알림
- [ ] 데이터가 늘어나면 MySQL 공간 인덱스(`ST_Distance_Sphere`)로 검색 전환
- [x] DB 마이그레이션 도구 도입 (Flyway)
- [x] 한국어 오류 화면 (404·400·403·500)
- [x] 모임 끝나는 시간과 상태 (모집 중·모집 마감·진행 중·종료)
- [x] 끝난 모임 후기, "다음에 또 만나요"

### 앞으로 할 일 (2026-11-23 추가)
**채팅**
- [x] 입력 중 표시 ("○○님이 입력 중…")
- [x] 보낸 메시지 삭제 (모두의 화면에 "삭제된 메시지예요")
- [x] 메시지 수정 (인스타그램처럼 "수정됨"을 누르면 수정 전 내용 확인)
- [ ] 멘션 (`@닉네임`)
- [x] 너무 빠른 전송 제한 (1초에 5개 넘게 보내면 3초 동안 전송 불가)
- [ ] 채팅방 설정: 글자 크기, 알림 켜기/끄기, 채팅방 나가기, 특정 사람 차단·무시
- [ ] 채팅방 나가기 (채팅만 숨길지, 모임 참여 취소까지 할지 결정 필요)
- [ ] 챗봇 (어떤 서비스를 쓸지, 무엇을 도와줄지 결정 필요)

**프로필·친구**
- [x] 별명(닉네임) 변경
- [x] 프로필 설정
- [x] 끝난 모임 리뷰·별점 → 프로필에 평판 표시
- [x] 지금까지의 활동 기록을 프로필에 표시 (어디까지 공개할지 본인이 선택)
- [ ] 친구 추가

**날씨**
- [ ] 날씨 칸에 설명 창 (강수확률·강수량 등이 어느 정도인지 쉽게 설명)

**주소·식별자**
- [x] 주소의 번호를 UUID로 바꾸기 (예: `/gatherings/19b80fb1-…/chat`)
- [x] 회원마다 UUID 만들기

**코드 정리**
- [x] 프론트엔드 코드를 HTML·CSS·JS 파일로 분리 (화면 안의 `<script>`·`style="…"`·`onsubmit` 없음, 테스트로 지킴)

## 작업 기록
날짜별 작업 내용은 [`doc/user`](doc/user) 폴더에 `YYMMDD.md` 형식으로 남깁니다.
