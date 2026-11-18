# Nadeuri — Working Rules for AI

## Communication
- Reply to the owner in Korean, plain and concise. Explain non-obvious terms briefly (owner asked e.g. what `lat` is).
- Use they/them if a pronoun is needed for the owner.
- Before changing tech stack, DB, ports, or external providers: ask. Owner decided: Java, Spring Boot, Thymeleaf, MySQL, Kakao Map, KMA API Hub.

## Code conventions
- Package by feature, then layer: `<feature>/{controller,service,repository,domain,dto,exception,client}`; shared code in `global/{config,util,web,init}`. New feature (e.g. `user`, `chat`) follows the same layout.
- Entities in `domain`, `@NoArgsConstructor(access = PROTECTED)`, no public setters; state changes via methods or atomic repository queries.
- Request/response objects are Java `record`s in `dto`; requests may expose `toEntity()`, responses `static from(entity)`.
- Controllers: page controllers return Thymeleaf views; JSON under `/api/**` in `*ApiController`.
- External HTTP calls live in `<feature>/client`, never in services. Cache at the client (`@Cacheable`) to avoid self-invocation proxies.
- Time: never `LocalDateTime.now()`; use `Times.now()` (KST).
- Validation: Bean Validation on DTOs and `@RequestParam`; don't silently clamp invalid input from APIs.
- Concurrency-sensitive counters: single conditional UPDATE or DB constraint, not read-modify-write in Java.
- Comments and user-facing strings in Korean.
- Front-end: vanilla JS inside templates; escape user text with `esc()` before `innerHTML`; Kakao SDK via `th:src="@{//dapi.kakao.com/v2/maps/sdk.js(appkey=${kakaoJsKey},autoload=false)}"` + `kakao.maps.load`.

## Secrets
- Never commit or echo secrets. Secrets go in `application-local.yml` (gitignored) or env vars. Keep `application-local.yml.example` and `.env.example` in sync when adding keys.
- `application.yml` must not contain real credentials or default passwords.

## Workflow
- Windows PowerShell: chain with `;`, use `.\gradlew.bat`. Use `--console=plain -q` for builds.
- After code changes: `.\gradlew.bat test` must pass. Add/adjust tests for logic changes (service/client level).
- For UI changes: verify in the browser pane at `http://localhost:8888` (owner's IntelliJ instance usually running; ask owner to restart after Java changes).
- When an external API fails, call it directly (curl.exe) to read the real error body before changing code.
- Files can disappear if the owner moves folders — if edits fail with "file does not exist", list the tree and ask before recreating.

## Documentation duties (every working session)
1. Update `doc/ai/claude.md` → "State", "External API status", "Next candidates", append to "Session log".
2. Write/append `doc/user/YYMMDD.md` (Korean, for the owner): what was done, technologies used, decisions and why, how to verify, next steps.
3. Update `README.md` if setup, structure, API, or roadmap changed.
