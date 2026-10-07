# Project Guidelines

## Purpose

BX Lens is a BoxLang module: a request level debug bar for web applications. Java collectors gather data in memory, a small Alpine.js UI is injected before `</body>` on HTML responses. Lens is request level and open source. BX Insights is the separate licensed observability product, so do not add persistence or cross request analytics here.

## Architecture

- `src/main/bx/ModuleConfig.bx` declares every setting with its default and wires the lifecycle. Keep it in sync with `docs/configuration.md`.
- `src/main/java/ortus/boxlang/modules/bxlens/`
  - `LensService`: singleton. Settings, collectors, history, injector. Collectors register as interceptors.
  - `LensConfig`, `AccessGuard`: parsed settings and the caller check (loopback and private networks by default).
  - `model/`: `LensRequest` (per request, attached to the request context), `Span`, `IssueEngine`, `Snapshot` (the JSON contract with the UI).
  - `interceptors/collectors/`: one class per panel. They never throw into a request.
  - `bifs/`: the `lens*` BIFs. They do nothing when the request is not tracked.
  - `ext/`: the data only extension API (`LensPanelBuilder`, `LensRegistry`, `CollectHandle`).
  - `store/RequestStore`: bounded in-memory ring buffer.
  - `web/WebExchange`: the only class that touches web-support types (compile only dependency).
- `src/main/bx/assets/`: `lens.html` (Alpine template), `lens.js`, `lens.css`, `alpine.min.js`. All CSS is scoped under `#bxlens`.
- `harness/`: demo app that produces every kind of data. `e2e/`: Playwright tests against it.

## Rules that matter

- Each BoxLang module has its own class loader. Tests on the test classpath see a different `LensService` than the module does, so integration tests reach the module's instance reflectively (see `IntegrationTest`).
- Collectors are shared across threads. Keep per request state in `LensRequest`, never in collector fields.
- Anything sent to the page goes through `Sanitizer` (redaction, depth and size caps) and `Json` (script safe). The UI renders only with `x-text`, never `x-html`.
- Core skips `onRequestEnd` for uncaught errors and aborts, so those requests are finished from `onError` and `onAbort` and get no bar. Do not fight this; document it.
- Core gaps (no `postBIFInvocation`, no `onComponentInvocation`, no cache read events, no SOAP events) are listed in `docs/reference/events.md`. Do not build collectors on events that never fire.
- The first run of a template includes compilation, so slow template warnings on first hit are expected. Tests warm pages up in `e2e/global-setup.ts`.

## Build and test

```bash
./gradlew downloadBoxLang      # once, downloads BoxLang and web support snapshots
./gradlew shadowJar test       # package the module, run unit and integration tests
./gradlew spotlessApply        # REQUIRED before every commit when Java changed
harness/start.sh               # run the demo (DEV=1 SKIP_BUILD=1 to edit the UI live)
cd e2e && npm run e2e          # Playwright. LENS_CHROMIUM=/path/to/chrome if needed
```

Gradle plugin and Maven Central rate limits in sandboxes: point Gradle at a mirror with an init script instead of changing `build.gradle`.

## Conventions

- Follow `.editorconfig` (tabs) and the Ortus formatter in `.ortus-java-style.xml`. Use the `this.` prefix for instance fields. New Java files carry the standard header.
- No em dashes in docs, comments or UI copy.
- New features need tests: JUnit for Java, Playwright for anything a user can see. Add a harness page when a feature needs a scenario.
- Keep `box.json`, `settings.gradle` and `gradle.properties` aligned when names or versions change. `boxlangVersion` in `gradle.properties` is the BoxLang version the module compiles against.

## Skills

Relevant BoxLang development skills live under `.agents/skills` (restore with `npx skills experimental_install`). Use them for module development, BIFs, interceptors, logging and runtime architecture.
