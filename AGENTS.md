# Project Guidelines

## Purpose

BX Lens is a BoxLang module with two surfaces: a request level debug bar (a small Alpine.js UI injected before `</body>` on HTML responses) and a standalone console for one server at `/~bxlens/index.bxm`. Java collectors and services gather data in memory. BX Lens is a product of Ortus Solutions and is not open source: do not write open source or Apache claims in code, docs or `box.json`. BX Insights is the separate observability product for clusters, history and alerting, so do not add persistence or cross node analytics here.

## Architecture

- `src/main/bx/ModuleConfig.bx` declares every setting with its default, the public mapping `~bxlens` (`usePrefix: false`) and wires the lifecycle. Keep it in sync with `docs/configuration.md`. The schema is `bar`, `console`, `collect`, `tabs`, `access`, plus the older blocks. `checks.*` and `dev.*` are read with defaults and are not declared.
- `src/main/java/ortus/boxlang/modules/bxlens/`
  - `LensService`: singleton. Settings, collectors, history, injector. Collectors register as interceptors.
  - `LensConfig`: parsed, immutable settings, including the `bxsecret:` console password. `AccessGuard`: one guard per surface (`bar.access`, `console.access`) plus `access.allowedHosts` and `access.requireHeader`; `all` is downgraded to loopback for the bar unless `bar.allowAllIPs`.
  - `ConsoleRouter`: all console routes, security headers and the SSE stream. `ConsoleAuth`: password check, in-memory sessions, CSRF, per-IP lockout. `ConsoleData`: executors, tasks, system and threads from core services and JDK beans. `LayoutStore`: the saved bar layout (`config/bxlens-layout.json`). `Licensing`: BoxLang+ detection through `bx-plus`.
  - `interceptors/TaskOutcomes`: remembers how each task's last run ended (core does not).
  - `util/Cost`: CPU time and allocation of the request thread. `model/SecurityChecks`: header and cookie Notes (severity `info`).
  - `model/`: `LensRequest` (per request, attached to the request context), `Span`, `IssueEngine`, `Snapshot` (the JSON contract with the UI).
  - `interceptors/collectors/`: one class per panel. They never throw into a request.
  - `bifs/`: the `lens*` BIFs. They do nothing when the request is not tracked. `lensConsole()` runs the console and is called only from `src/main/bx/public/index.bxm`.
  - `ext/`: the data only extension API (`LensPanelBuilder`, `LensRegistry`, `CollectHandle`).
  - `store/RequestStore`: bounded in-memory ring buffer.
  - `web/WebExchange`: the only class that touches web-support types (compile only dependency).
- `src/main/bx/assets/`: the bar (`lens.html`, `lens.js`, `lens.css`, scoped under `#bxlens`) and the console (`console.html`, `login.html`, `setup.html`, `console.js`, `console.css`), plus `alpine.min.js` and the icon sprites.
- `harness/`: demo app that produces every kind of data (console password `lens-demo`, `LENS_LICENSE` env, `load.bxm`, `stall.bxm`, `home/config/tasks.json`). `e2e/`: Playwright tests against it.

## Rules that matter

- The console is Java behind the `lensConsole()` BIF. `index.bxm` contains only that call. Add routes in `ConsoleRouter`, data in `ConsoleData`. Every API route needs a session, and every non-GET needs the `X-Lens-CSRF` token. Login needs `X-Lens-Login`. A caller who fails `console.access` gets a plain 404.
- No external requests, ever. The console and the bar must not load scripts, styles, fonts, images or data from another origin, and the Content-Security-Policy stays strict. Alpine.js is vendored, fonts are system fonts, and an e2e test checks this.
- Icons are Phosphor (MIT). Do not link them from a CDN. `tools/build-icons.py` builds `icons.svg` (ids `ph-*`) and `bar-icons.svg` (ids `bxlens-ph-*`, the bar's prefixed subset). Keep `ICONS-LICENSE.txt`.
- Settings: every new setting goes in `ModuleConfig.bx` with a default, is read through `LensConfig` with a default, and is added to `docs/configuration.md`. A change to a name needs a line in the migration table there.
- Licensing: detection must never break Lens. `Licensing.has()` returns true for every feature until the free and Plus split is decided. Do not gate a feature or invent a split.
- The console collects every request when enabled, the bar only for allowed callers. Keep production safe: `collect.level: light` skips heavy collectors via `ILensCollector.heavy()`.

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
