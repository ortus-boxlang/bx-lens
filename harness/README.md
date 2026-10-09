# BX Lens demo harness

A small shop app that produces every kind of data BX Lens can show. Use it to try the bar, to demo it, and as the target of the Playwright tests in `../e2e`.

```bash
./start.sh                       # builds the module, starts MiniServer on http://localhost:8085
SKIP_BUILD=1 ./start.sh          # reuse build/modules/bx-lens
DEV=1 SKIP_BUILD=1 ./start.sh    # serve the UI files from src/main/bx/assets, edit and refresh
PORT=9000 BOXLANG_VERSION=1.19.0-snapshot ./start.sh
```

It downloads the BoxLang, MiniServer, web support and Derby jars into `.cache/` once, assembles a BoxLang home in `.run/`, and seeds an in-memory Derby database on the first request. Both folders are git ignored.

## Scenarios

| Page | What it shows |
|---|---|
| `/orders.bxm` | A healthy page: one joined query, `lensMeasure`, a cache `getOrSet`, two messages. No issues, green strip |
| `/n-plus-one.bxm` | One query per order. N+1 warning, repeated statement grouped, caller line linked to the editor |
| `/slow.bxm` | A nested loop join of about 150 ms. Critical slow query |
| `/caught-exception.bxm` | An exception handled in code and sent with `lensException()`. The panel opens on Issues by itself |
| `/error.bxm` | An uncaught error. Core's error page has no bar, the request is kept in History with status 500 |
| `/http.bxm` | Two outgoing HTTP calls, one 200 and one 404 (warning) |
| `/cache.bxm` | Five hits and three misses against the default cache, shown as this request's hit rate |
| `/timers.bxm` | `lensStart/Stop`, `lensMeasure`, `lensAddMeasure`, `lensMessage` at every level, `lensDump` |
| `/functions.bxm` | Nested user functions (the functions collector is on in this harness) |
| `/orm.bxm` | bx-orm entities: `?save=1` inserts a book and flushes, `?fail=1` makes an insert fail. Needs `WITH_ORM=1` (bx-orm 1.7.2 or later: `BX_ORM_VERSION`, or `BX_ORM_DIR` to build a checkout) |
| `/forms.bxm` | A POST with a password field. The password is masked in the Scopes tab and absent from the page data |
| `/session.bxm` | Session and request scope snapshots, with a token key masked |
| `/transaction.bxm` | One committed and one rolled back transaction on the waterfall |
| `/extend.bxm` | Panels from application code with `lensPanel()` (table and spans) |
| `/xss.bxm` | Markup sent to Lens must show as text and never run |
| `/api/orders.json.bxm`, `/api/rates.json.bxm`, `/events.bxm` | JSON and SSE endpoints: recorded in History, no bar injected |

The `demoLens` module in `home/modules/demoLens` shows how another module adds a panel to every request through `onLensRegister` and `onLensCollect`, with no JavaScript.

## Settings

`home/config/boxlang.json` enables the bar and the console (login at `/~bxlens/index.bxm`, password `lens-demo`), defines the `demo-pool` executor, takes `dev.license` from the `LENS_LICENSE` environment variable (`trial`, `plus`, `expired`, `none`), lowers the slow query limit to 15 ms so the demo is deterministic, enables the functions and logs collectors, captures the session and request scopes, and sets `dev.reloadAssets` so `DEV=1` works.

## Presenting

1. Open `/orders.bxm`. Point at the collapsed strip: status, time, memory, SQL and template counts. It is green.
2. Open `/n-plus-one.bxm`. The strip turns amber. Click the issues chip, then View, and show the waterfall. Click a query row, open it in the editor.
3. Open `/caught-exception.bxm`. The panel opens on Issues because something was caught.
4. Open `/cache.bxm`, then the Cache tab. Open the Modules tab.
5. Open `/extend.bxm` and show the custom panels, then the demo module panel that every page has.
6. Hit `/api/orders.json.bxm` and `/events.bxm` in another tab, then open History on any page.

## Trying the AI features

```bash
python3 mock-ai.py &                         # a stand-in for a local Ollama server on localhost:11434
WITH_AI=1 SKIP_BUILD=1 ./start.sh            # bx-ai is inside the module; this sets ai.enabled
```

Then open the console, go to Ask Lens, or open an error and press Explain with AI. The mock answers with the start of the prompt it received.

`LENS_LICENSE=plus` (the default in the e2e config), `trial`, `expired` or `none` shows each license state. Plus and trial turn on the disk store (`harness/.run/home/lens-data`) and the longer request history.
