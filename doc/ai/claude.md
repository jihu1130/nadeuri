# Nadeuri — AI Context

Read this first in every session, then `doc/ai/rules.md`. Update the "State" and "Session log" sections at the end of each working session.

## Product
Korean web app: find people nearby (or with the same purpose) to do outdoor activities/events together at a specific place. Map + weather are core. UI language: Korean. User (project owner) communicates in Korean; reply in Korean.

## Stack (fixed by owner — do not change without asking)
- Java 21, Spring Boot 3.3.5, Gradle 8.14 wrapper (`gradlew.bat` on Windows)
- Thymeleaf SSR + vanilla JS (no React)
- MySQL 8 (local account + password in `application-local.yml` — never copy the password into docs, DB `nadeuri`, auto-created), H2 for tests (`h2` profile)
- Kakao Maps JS SDK (map, `services` lib for place search + reverse geocode)
- Kakao Login via Spring Security + OAuth2 Client
- KMA (기상청) API Hub: `https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst`, param `authKey`
- Caffeine cache, Lombok
- Server port: 8888

## Environment
- OS: Windows 10, PowerShell 5.1. Project root: `C:\Users\User\Desktop\나들이\nadeuri` (moved by owner before 2026-11-18; IntelliJ project opened at `Desktop\나들이`).
- Git: repo at https://github.com/jihu1130/nadeuri (branch `main`, first push 2026-11-18). Repo-local author `jihu1130 <jihu1130@users.noreply.github.com>` (keeps the owner's real email out of history). Commit/push only when the owner asks; scan for secrets before each commit.
- No Docker installed. No global Gradle — use `.\gradlew.bat`. No `mysql` CLI on PATH. `python` is the Windows Store stub — use `py -I`.
- **Gradle tests fail with `ClassNotFoundException` because of the Korean path.** Workaround: `robocopy <project> <scratchpad>\nadeuri /MIR /XD build .gradle .idea`, then run `.\gradlew.bat test` in the copy. Running the app from IntelliJ is unaffected.
- Owner usually runs the app from IntelliJ on :8888. If port 8888 is busy, the owner's instance is running — test against it, or ask before starting another one. For UI checks of unreleased Java changes, run the scratchpad copy with `--server.port=8899 --spring.profiles.active=h2` (Kakao map tiles won't load there: JS SDK domain is only :8888).
- Browser pane available for UI verification (`http://localhost:8888`).

## Secrets / config
- `src/main/resources/application.yml`: no secrets. `spring.profiles.default=local`.
- `src/main/resources/application-local.yml` (gitignored): DB creds, `kma.service-key`, `kakao.javascript-key`, `kakao.rest-api-key`, `kakao.client-secret` (the two login keys were added empty on 2026-11-18; owner fills them). Never print the KMA key, REST API key or client secret back to the user; the Kakao JS key is public-by-design (domain-restricted).
- `.env` (gitignored): `MYSQL_ROOT_PASSWORD` for docker-compose.
- Prod: env vars `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KMA_SERVICE_KEY`, `KAKAO_JS_KEY`, `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET`, `DDL_AUTO` (default `validate`).

## External API status (as of 2026-11-18)
- KMA API Hub approvals are per operation. Approved: `getVilageFcst`, `getUltraSrtNcst`, plus typ01 `nph-dfs_shrt_grd` (whole-grid file), `fct_shrt_reg`, `fct_afs_ds` (unused). NOT approved: `getUltraSrtFcst`, `getFcstVersion`.
- New approvals take a few minutes; until then HTTP 403 JSON `{"result":{"status":403,"message":"활용신청이 필요한 API 입니다..."}}`.
- Kakao app "나들이": JS SDK domain `http://localhost:8888` registered, Kakao Map enabled.
- Kakao Login: code done, **console not yet configured by owner**. Needed: Kakao Login ON; on the REST API key, redirect URI `http://localhost:8888/login/oauth2/code/kakao` and client secret; consent items `profile_nickname`, `profile_image`.

## Architecture
Package-by-feature, then by layer: `com.example.nadeuri.{gathering,weather,user,global}.{controller,service,repository,domain,dto,exception,client,config,security,util,init}`. See README "프로젝트 구조" for the full tree.

Key decisions (keep unless owner asks):
- Time: always `Times.now()` (KST). JVM default TZ set to Asia/Seoul in `main`; Hibernate `jdbc.time_zone=Asia/Seoul`.
- Radius search: native SQL haversine + bounding box; longitude split into two ranges across the antimeridian; full longitude near poles. Works on MySQL and H2. Radius clamped 100–20000 m.
- Join: first `UPDATE ... SET current_people = current_people + 1 WHERE current_people < max_people` (0 rows → `IllegalStateException`, nothing inserted), then insert `Participant` (unique `(gathering_id, user_id)`, no FK to users); a duplicate violation rolls back the increment too. Order changed 2026-11-18: insert-then-rollback on full gatherings made H2 reuse identity values under concurrency → flaky PK violations in the 20-thread test. Host gets a Participant row on create. Sample data has no host rows (fine).
- Identity: Kakao login via Spring Security `oauth2Login`. `global/config/SecurityConfig` builds the Kakao `ClientRegistration` in code only when `kakao.rest-api-key` is non-blank (`@ConditionalOnExpression`); otherwise oauth2Login is skipped and `/login` shows a "not configured" notice — the app still boots without keys. Client auth `client_secret_post`, scopes `profile_nickname, profile_image`.
- Local (아이디 + password) login — switched from email to username on 2026-11-18 at owner request: `formLogin` on POST `/login` with params `username`/`password`; failure → `/login?error=local` (Kakao failures → `/login?error`). `LocalUserDetailsService` looks up `users` where provider=`local`, provider_id=normalized username (strip + lowercase). Passwords hashed with `PasswordEncoderFactories.createDelegatingPasswordEncoder()` (`{bcrypt}...`, static bean in SecurityConfig). Signup `/signup` (`SignUpRequest`: username `^[A-Za-z][A-Za-z0-9_]{3,19}$`, password 8–64 with letter+digit, confirm, nickname ≤20) → `UserService.signUp` → redirect to `/login` with flash message + prefilled username (no auto-login, so the saved request survives). Success handler updates `last_login_at` and redirects to the saved request. `LoginUser` implements both `OAuth2User` and `UserDetails` + `CredentialsContainer` (hash erased after auth, not kept in session).
- Kakao login flow: `user/service/KakaoOAuth2UserService` → `KakaoProfile.from(attributes)` → `UserService.loginWithKakao` (upsert by provider + providerId into table `users`) → principal `global/security/LoginUser` (Serializable, holds our `users.id`, nickname, image). Controllers take `@AuthenticationPrincipal LoginUser`. `ViewModelAdvice` exposes `loginUser` to all views; header fragment `templates/fragments/auth.html`. Thymeleaf SpEL needs `${loginUser != null ...}` inside boolean expressions.
- Local and Kakao accounts are separate (no linking yet). Accounts created during the short email period have provider_id = email; they still log in by typing the email in the 아이디 field (lookup is not validated against the username pattern).
- Access: GET `/`, `/gatherings/*`, `/api/**`, `/login`, `/signup`, `/.well-known/**`, static → public. Custom `HttpSessionRequestCache` (SecurityConfig.REQUEST_CACHE, shared with formLogin/oauth2Login success) saves only GET page requests — excludes `/.well-known`, `/api`, `/error`, static, h2-console, XHR — because Chrome DevTools auto-requests `/.well-known/appspecific/com.chrome.devtools.json` and the default cache made login redirect there (bug found 2026-11-18). `/gatherings/new`, POST create/join, GET `/gatherings/{id}/sign-in` → login required (entry point `/login`). `/sign-in` exists so the request cache saves a GET and returns the user to the detail page after login (POST isn't cached). Matchers use `antMatcher` (H2 console servlet makes string matchers ambiguous). CSRF on; forms must use `th:action` so the token is injected. (The earlier `UserDetailsServiceAutoConfiguration` exclusion was removed; our `LocalUserDetailsService` bean replaces it.)
- Weather: `KmaForecastClient.fetch(nx, ny, base)` is `@Cacheable("weather")` keyed by grid + base time; `latestBaseTime` = latest of 02,05,…,23 with 10-min availability lag. `WeatherService.forecastAt` picks nearest hour within ±2h. Short-term forecast covers ~3 days only.
- Param validation: Spring 6.1 built-in method validation on `@RequestParam` (`@DecimalMin/@DecimalMax`) → 400.
- Kakao JS key is injected into all views by `global.config.ViewModelAdvice` as `kakaoJsKey`; templates load SDK with `autoload=false` and wrap code in `kakao.maps.load(...)`.

## Tests
- `gathering/service/GatheringServiceTest` (SpringBootTest, h2): duplicate join, 20-thread concurrency vs capacity, antimeridian search.
- `weather/client/KmaForecastClientTest`: grid conversion (Seoul 60,127 / Busan 97,74), base-time selection.
- `user/service/UserServiceTest`: upsert keeps one row and refreshes profile; KakaoProfile parsing, default nickname, missing id rejected.
- `global/config/SecurityConfigTest` (MockMvc): anonymous browsing OK; create/join → `/login`; POST without CSRF → 403; logged-in join works and header shows nickname.
- `global/config/KakaoLoginEnabledTest`: with dummy keys, `/login` shows the Kakao button and `/oauth2/authorization/kakao` redirects to `kauth.kakao.com` with client_id + redirect_uri.
- `weather/dto/ForecastResponseTest`: grouping by date, min/max/max POP, partial days, representative weather (rain wins, else most common daytime sky), unavailable.
- `user/controller/LocalLoginTest` (MockMvc): signup hashes + normalizes username + rejects duplicate; signup → login returns to saved request; wrong password / unknown username → `/login?error=local`; field validation messages; duplicate username on form; invalid usernames (space, @, Korean, too short) rejected; password not echoed back.
- Run: `.\gradlew.bat test --console=plain -q` from an ASCII-path copy (see Environment). All 28 pass as of 2026-11-18 (concurrency test also passed 8 repeated runs after the join reorder).

## Home map: "내 위치" (2026-11-18)
- Two buttons: header `#locateButton` = re-locate **and re-search** (`locate()`); in-map `#mapLocateButton` (bottom-right, above legend) = **pan only** to last known `myPos` (`focusMyLocation()`; zooms in to level 4 if zoomed out past 5, flashes the dot). If `myPos` unknown it falls back to `locate()`.
- Marker: `.my-location-marker` with ripple `.my-location-pulse` + Kakao `Circle` for `coords.accuracy` (only if ≤ 2 km). Button gets `.is-centered` (filled blue) on map `idle` when my position is within 24 px of the center.
- Geolocation: `enableHighAccuracy`, timeout 10 s, maximumAge 30 s. On error → `#mapToast` message by code (1 = permission, 3 = timeout) and keep the previous `myPos` instead of jumping to 여의도; only with no known position does it use DEFAULT. Keeps the other session's `searchPoint`/`searchIsCurrent` (radius change re-searches without re-locating).
- Known: list items still `panTo` the gathering on hover (other session's design) — moves the map away from you; the in-map button brings it back.
- IntelliJ serves templates from `build/resources` — after template/CSS edits the owner must rebuild (Ctrl+F9) or restart, otherwise :8888 serves a partial/stale copy (seen today).

## State (2026-11-18)
Latest: local signup/login switched from email to **아이디** (owner request); home map got the in-map "내 위치로" button. Both verified in the browser (ID signup → login → home on :8899 H2 copy; focus/zoom/centered state on :8888 with an injected mock position; permission-denied toast seen live on :8899). Owner must rebuild/restart :8888.

Main page refreshed with a responsive hero, nearby map/list layout, radius selector, location refresh, and loading/empty/error states. Browser verification was attempted but stopped because the computer-use integration could not reliably establish the current Chrome URL. No tests were run for this UI-only change.

Email + password signup/login implemented and verified in the browser on an H2 copy at :8899 (detail → 로그인하고 참여하기 → login → signup → login → back to detail → join 2/6). Owner asked for Google login next. Owner hit Kakao KOE205 (consent items not set) and was told to enable 닉네임/프로필 사진 consent — not yet re-verified. Owner's :8888 instance needs a restart for these changes; `users.password` column is added by `ddl-auto: update` (nullable, safe).

Kakao login implemented; tests pass; UI checked on an H2 copy at :8899 (detail shows "카카오 로그인하고 참여하기", `/gatherings/new` redirects to the login page, header shows 로그인). **Not yet verified against real Kakao.** Blockers for the owner's :8888 instance:
1. Owner configures Kakao console and fills `kakao.rest-api-key` / `kakao.client-secret`.
2. ~~Old `participant` table~~ — migrated 2026-11-18 (see session log 6).
3. Owner restarts the app in IntelliJ (new dependencies → Gradle reload).

Earlier (2026-11-16): home map + pins + weather chip, create page with Kakao place search / reverse geocode / weather preview, detail with forecast + rain warning + Kakao directions link, join with duplicate/capacity protection.

## Next candidates (owner chooses)
0. Google login (owner's request; same pattern as Kakao: conditional ClientRegistration — Google is a built-in `CommonOAuth2Provider`, scope `openid profile email`; provider `google`, providerId = `sub`). Then finish Kakao verification on :8888.
0b. Account polish: link local/social accounts, password reset (needs mail), login attempt limiting.
1. Home "지금 날씨" via `getUltraSrtNcst` (T1H, PTY, REH; no SKY → combine with forecast SKY).
2. Host nickname + participant list on detail; "my gatherings" page.
3. ~~"이 지역에서 다시 검색"~~ — done 2026-11-18.
4. Chat (WebSocket/STOMP).
5. Flyway (would have avoided the manual participant-table migration); MySQL spatial index.

## Session log
- 2026-11-16 — Planning → Spring Boot + Thymeleaf scaffold → MySQL → KMA (data.go.kr → API Hub) → fixed 6 review issues (TZ, atomic join, duplicate join, antimeridian, param validation, secrets) → Kakao Map + place search → package-by-layer refactor → docs. Details: `doc/user/261116.md`.
- 2026-11-18 — Kakao login: Spring Security + OAuth2 client, new `user` feature (User, UserService, KakaoOAuth2UserService, KakaoProfile, LoginController), `LoginUser` principal, login page + header fragment, Participant keyed by user_id, GuestKeyResolver removed, 10 new tests (15 total). Found the Korean-path Gradle test issue. Details: `doc/user/261118.md`.
- 2026-11-18 (2) — Email + password login: formLogin on /login, /signup page + SignUpRequest validation, LocalUserDetailsService, BCrypt delegating encoder, LoginUser also UserDetails/CredentialsContainer, users.password column, success handler (last_login_at). Join reordered (increment then insert) to fix flaky H2 concurrency test. 6 new tests (21 total). Browser-verified on :8899.
- 2026-11-18 (3) — Reworked the home page with a responsive neighborhood discovery layout, radius selection, location refresh, weather summary, and clear loading/empty/error states. Browser check was blocked by computer-use URL detection; no tests run.
- 2026-11-18 (4) — Home map "내 위치로" in-map button (pan/zoom to last known position, pulsing dot, accuracy circle, centered state, error toast; failures no longer jump to 여의도). Local login switched from email to 아이디 (username pattern, DuplicateUsernameException, form param `username`). 22 tests pass. Browser-verified.
- 2026-11-18 (5) — Removed the location pulse and accuracy circle so only one location dot remains. Raised the map legend above Kakao map layers and reduced map height on short viewports so the legend and controls remain visible. JavaScript syntax checked; no browser verification.
- 2026-11-18 (6) — The map legend was still obscured in the owner's screenshot. Moved both labels into the map header so Kakao map tiles cannot cover them; kept compact responsive styling. Inline JavaScript syntax checked.
- 2026-11-18 (7) — Restyled all existing screens with a white, thin-border layout and yellow primary color, using the supplied login page as a layout reference. Clarified search, forecast, and signup copy. Home/login/signup were browser-checked. Copied the updated stylesheet to the running build output because IntelliJ serves CSS from `build`.
- 2026-11-18 (5) — Fix: after login users were redirected to /.well-known/appspecific/com.chrome.devtools.json?continue (Chrome DevTools probe saved as the post-login target). Permitted /.well-known/** and restricted the request cache to page GETs. 2 new tests (24 total).
- 2026-11-18 (6) — Verified on :8888 after owner restart: 아이디 signup/login, post-login redirect fix, in-map locate button (real click). Migrated owner's local MySQL at owner's request (JDBC from scratchpad, creds read from application-local.yml, not printed): dropped participant.participant_key + uk_participant_gathering_key, deleted 1 guest row (user_id=0), recomputed gathering.current_people = 1 + non-host participants. Join and create now work on MySQL. Test data (user claude_test01, gathering #8, its join on #3) deleted afterwards at owner request; counts recomputed.
- 2026-11-18 (7) — Create page: weather date-time picker replaces datetime-local (hidden `startAt` still `yyyy-MM-ddTHH:mm`, server unchanged). New `GET /api/weather/forecast` → `ForecastResponse` (days with emoji/desc/min/max/max POP/partial + hourly). KMA short-term range measured live: hourly to D+3, 3-hourly on D+4 (base 20:00 → until D+4 24:00); UI shows exact `forecastUntil`, later dates selectable with '예보 없음'; 3-hourly gaps use ±1h nearest. Styles in separate `static/css/weather-picker.css` (also styles the create-map '내 위치로' button `#pickLocateButton` + toast). Weather source follows map center → my location → chosen place. 28 tests. Browser-verified on an :8899 copy (local profile + in-memory H2 via CLI args) incl. submit → detail 15:30. NOT verified: create-map locate button (Kakao map only loads on :8888) — needs owner restart. Note: app.css was deleted at 20:12 by something outside this session and reappeared at 20:19 (21 KB, rewritten).
- 2026-11-18 (8) — Home '이 지역에서 다시 검색': `#researchButton` (top-center of map) appears after a user **dragend** (or the in-map 내 위치로 button) moves the center ≥ max(300 m, 15% of radius) from `searchPoint`; programmatic pans (list hover) don't trigger it. Click → search at map center with `searchIsCurrent = 'area'` (label '지도에서 고른 지역 기준'); if the map is centered on me (locate button .is-centered) it searches as 현재 위치. Searched area drawn as dashed Kakao Circle (`drawSearchArea`, redrawn on every loadAt incl. radius change). Styles appended to app.css (.research-button). Verified on :8888 with real Kakao map (real drag 1.6 km → button → 4 results; back-to-me flow via injected copy of the final function — :8888 hadn't reloaded the last template edit). Home top '현재 위치' button was removed by the other redesign; only the in-map button remains.
- 2026-11-18 (9) — Picker cell states: past → dim '·' (disabled); exact forecast; **approx** (no exact value but ±1 h neighbour, looked up across day boundaries via `byTime` map) → value with '≈', faded, summary says '약' (explanatory note removed at owner request — it's a forecast anyway); truly unknown → ❔ '예보 없음'. Covers: current hour (release at HH:00 starts at HH+1), 3-hourly D+4, end-of-range day. Day card: no data or <4 hourly points (not today) → ❔ with '예보 없음' / '일부만 예보' instead of a misleading min/max. Verified on :8899 copy.
