# ⚡︎ BoxLang Module: BX Lens

```
|:------------------------------------------------------:|
| ⚡︎ B o x L a n g ⚡︎
| Dynamic : Modular : Productive
|:------------------------------------------------------:|
```

<blockquote>
	Copyright Since 2023 by Ortus Solutions, Corp
	<br>
	<a href="https://www.boxlang.io">www.boxlang.io</a> |
	<a href="https://www.ortussolutions.com">www.ortussolutions.com</a>
</blockquote>

<p>&nbsp;</p>

A debug bar and a console for BoxLang web applications. The **bar** gives every HTML page a strip at the bottom with the request timeline, queries, templates, exceptions, HTTP calls, cache, modules, scopes, cost and more. The **console** is a password-protected page for one server with live requests, executor health, scheduled tasks (with Run now), JVM numbers, threads and a bar designer. Everything is collected by Java code in memory. Nothing is written to disk except the bar layout you save.

![BX Lens on an N+1 page](docs/assets/screenshots/overview.png)

Lens works with the BoxLang MiniServer, CommandBox and servlet deployments, because it hooks the request lifecycle that web support already provides.

## Install

```bash
# OS binary
install-bx-module bx-lens

# CommandBox
box install bx-lens
```

Lens is **off by default**. The bar and the console are switched on separately in `boxlang.json`, and each has its own access rule that defaults to loopback only:

```json
{
	"modules": {
		"bxLens": {
			"settings": {
				"bar": { "enabled": true },
				"console": { "enabled": true, "password": "bxsecret:..." }
			}
		}
	}
}
```

Make the password with `boxlang generatesecret "your password"`. The console is at `/~bxlens/index.bxm` (a bare `/~bxlens/` is not served). Keep the bar off on live servers and read [Running Lens in production](docs/guides/production.md) before you expose the console. See [Security](docs/security.md).

Upgrading? `enabled` is now `bar.enabled` and `access.allowedIPs` is now `bar.access`. See [Configuration](docs/configuration.md#moving-from-older-settings).

## What you get

| Panel | Shows |
|---|---|
| **Issues** | Everything suspicious, ranked: exceptions, N+1 queries, slow queries, slow templates, failed HTTP calls |
| **Timeline** | One waterfall of templates, functions, queries, HTTP calls and transactions. Hover, click for detail, zoom, pan, filter, search, open the file in your editor |
| **Queries** | Every statement with its parameters, rows, time, datasource and the template line that ran it. Copy SQL |
| **Templates** | The include and call tree |
| **HTTP** | Outgoing HTTP calls with status, size and time |
| **Exceptions** | Caught and uncaught exceptions with BoxLang locations and the Java stack |
| **Messages and Timers** | What your code sends with `lensMessage()`, `lensDump()`, `lensMeasure()` |
| **Cache** | Every BoxCache cache with hit rate, objects, evictions and what this request did to it |
| **Modules** | Loaded modules with version, author, what they provide and activation time |
| **Request, Scopes, Runtime** | Request and response headers, redacted scope snapshots, memory, GC, threads, versions, and the CPU time and allocation of the request |
| **History** | The last 50 requests, including JSON and SSE, recycled in memory |
| Your panels | Applications and other modules add panels with `lensPanel()` or the `onLensCollect` interception point |

The Issues tab also names where a slow request was stuck (a stack sample after `thresholds.slowRequestMs`) and lists security Notes for missing headers and cookie flags.

The console pages are Overview, Requests, Executors, Tasks, System, Threads, Bar designer and Settings. The console makes no request to any other site, so it works air gapped. See [Console](docs/console/index.md).

A collapsed health strip turns amber or red when something is wrong and opens on Issues when an exception was caught. Resize it, detach it as a floating window, switch themes, and use the keyboard: <kbd>Ctrl</kbd>+<kbd>`</kbd> toggles, <kbd>1</kbd> to <kbd>9</kbd> switch tabs, <kbd>/</kbd> searches.

## Send things to the bar

```javascript
lensMessage( "Loaded #orders.len()# orders", "info" );
lensDump( order, "order after pricing" );

id = lensStart( "price calculation" );
// ...
lensStop( id );

orders = lensMeasure( "load orders", () => orderService.list() );

try {
	risky();
} catch ( any e ) {
	lensException( e );
}

lensPanel( "orm", "ORM" ).columns( [ "Entity", "ms" ] ).rows( rows ).badge( rows.len(), "none" );
```

## Documentation

The full documentation is built with [bx-sites](https://github.com/ortus-boxlang/bx-sites) from the `docs/` folder: configuration reference, every panel, the BIFs, extending Lens from your own module, security, the core event inventory, and troubleshooting.

```bash
bxSites serve
```

## Try it: the demo harness

`harness/` is a small shop app with a Derby in-memory database that produces every kind of data Lens can show: a healthy page, an N+1 loop, a slow query, caught and uncaught errors, outgoing HTTP, cache hits and misses, JSON and SSE endpoints, a form with a password, a session, transactions and custom panels.

```bash
./harness/start.sh          # builds the module and starts MiniServer on http://localhost:8085
DEV=1 SKIP_BUILD=1 ./harness/start.sh   # serve the UI files from src/main/bx/assets, edit and refresh
LENS_LICENSE=trial ./harness/start.sh   # show a license state (trial, plus, expired, none)
```

The console is at `http://localhost:8085/~bxlens/index.bxm` and the demo password is `lens-demo`. `/load.bxm` saturates a small executor and `/stall.bxm` shows the slow request sample. `harness/home/config/tasks.json` defines the demo scheduled tasks.

## Development

```bash
./gradlew downloadBoxLang          # BoxLang and web support jars, once
./gradlew shadowJar test           # build the module and run the Java tests
./gradlew spotlessApply            # Ortus Java formatting, run before every commit

cd e2e
npm ci
npx playwright install chromium
npm run e2e                        # starts the harness and drives every feature in a browser
npx playwright test screens.spec.ts   # regenerates docs/assets/screenshots
```

Set `LENS_CHROMIUM` to use a Chromium you already have. CI runs the Java tests on Linux and Windows and the Playwright suite on Linux, and uploads the report and the screenshots.

How it fits together:

- `src/main/java/.../LensService` owns the settings, the collectors, the in-memory history, the injector and the slow request watchdog.
- `ConsoleRouter`, `ConsoleAuth` and `ConsoleData` are the console: routes and security headers, login and sessions, and the data for executors, tasks, system and threads. `AccessGuard` checks callers for the bar and the console. `Licensing` detects BoxLang+. `LayoutStore` keeps the bar layout.
- `interceptors/collectors/*` are the Java collectors, one per panel. They run as BoxLang interceptors.
- `model/` holds the per request data, the issue engine and the snapshot that the UI reads.
- `src/main/bx/assets/` is the UI for the bar (`lens.*`) and the console (`console.*`): Alpine.js, vendored Phosphor icons, CSS and templates. The bar's data travels as JSON inside the page.
- `src/main/bx/ModuleConfig.bx` declares every setting with its default.

Lens works on one server. BX Insights is the separate observability product for clusters, history over time and alerting.

## License

BX Lens is a product of Ortus Solutions. License terms apply, see the [BoxLang+ plans page](https://boxlang.io/plans) and [Licensing](docs/licensing.md). Which features need BoxLang+ is not decided yet, so every feature works in every license state today. Phosphor Icons are MIT licensed, see `src/main/bx/assets/ICONS-LICENSE.txt`.

## Ortus Sponsors

BoxLang is a professional open-source project and it is completely funded by the [community](https://patreon.com/ortussolutions) and [Ortus Solutions, Corp](https://www.ortussolutions.com). Ortus Patreons get many benefits like a cfcasts account, a FORGEBOX Pro account and so much more. If you are interested in becoming a sponsor, please visit our patronage page: [https://patreon.com/ortussolutions](https://patreon.com/ortussolutions)

### THE DAILY BREAD

> "I am the way, and the truth, and the life; no one comes to the Father, but by me (JESUS)" Jn 14:1-12
