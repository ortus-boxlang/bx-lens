---
title: Licensing
order: 6
description: How BX Lens detects BoxLang+, what the license states mean, and what Plus or a trial adds today.
icon: lucide:badge-check
---

# Licensing

## The license

BX Lens ships under a BoxLang+ proprietary license, in the `LICENSE` file of the module. It is freeware with limits: the free features can be used without a subscription, including in production, but not redistributed or modified. The BoxLang+ features and the removal of the free limits need a BoxLang+ subscription or an active trial. The same terms apply to every Ortus BoxLang+ module, such as bx-redis. The exact text is the `LICENSE` file, not this page.

## Free and BoxLang+

This is the current split. When it changes, this page changes with it. Lens checks the license in one place (`Licensing.has()`).

| Feature | Free | BoxLang+ or trial |
|---|---|---|
| The bar, every tab, the bar designer | yes | yes |
| Console: Overview, Requests, In flight, Queries, Errors, Reports | yes, in memory | yes, saved to disk (see below) |
| Console: Executors, Tasks (with run, pause, resume, reload), Datasources, Caches, Logs, Environment, Modules, System, Threads | yes | yes |
| Live settings, read-only mode, viewer role, audit log | yes | yes |
| Heap dump, GC, thread dump, diagnostic bundle, log download | yes | yes |
| Copy a prompt and open ChatGPT or Claude | yes | yes |
| Requests kept in memory | 25 at most, even if `history.maxRequests` is higher | `history.maxRequests` as configured (default 50) |
| Disk store: errors and reports survive restarts, totals since first install | no | yes |
| AI from the server: Explain with AI and Ask Lens through bx-ai | no | yes |

The disk store (`store.enabled`, on by default) writes `errors.json` and `reports.json` to `store.dir` (default `lens-data` in the BoxLang home). It keeps data for `store.retentionHours` (72), limits the errors file to `store.maxMB` (50) and writes every `store.flushSeconds` (30). Each file is replaced atomically. See [Errors and Reports](console/errors-and-reports.md#the-disk-store). Without the license the store settings have no effect, and the Errors and Reports pages say that the data is kept in memory only. The bar layout file and the settings overrides file are small files for your own choices, and they work on Free.

If a trial ends or a license expires, the disk store stops writing, the request history returns to 25 and the AI calls stop at the next license check (the answer is cached for 5 minutes). The saved files stay on disk. Lens reads them at the next start if the license is valid then. Settings you configured do not have to change.

BoxLang AI (bx-ai) ships inside the module, in its `modules` folder, so there is nothing else to install. It is Apache 2.0 licensed. Using it from Lens still needs `ai.enabled` and, for the server calls, BoxLang+.

## License states

Lens detects the state through the `bx-plus` module, the same way other BoxLang+ modules do. It asks the global `BoxlangLicenseService` whether the license is valid and whether it is a trial (`isValidLicense`, `isTrialMode`), and reads the expiry from `BoxlangLicenseInfo`.

| State | Label in the console | When |
|---|---|---|
| Plus active | `BoxLang+ active` | A valid license that is not a trial. |
| Trial | `Trial` with the days left | A valid trial license. The console shows a banner: you are on a trial, Plus features stop working when it ends and need a license, with a link to [boxlang.io/plans](https://boxlang.io/plans). |
| Expired | `License expired` | The license is present but not valid. The banner says Plus features may stop working. |
| Free | `Free` | `bx-plus` is not installed, or the check failed. |

![The trial banner in the console](assets/screenshots/license-trial.png)

The state shows in the console header.

## Safe by design

- The answer is cached for 5 minutes.
- A missing, invalid or failing license check never breaks Lens. It reports Free and carries on.
- Lens makes no network call for licensing. It only talks to the `bx-plus` module in the same runtime.

## Show a state for a demo

Set `dev.license` to `trial`, `plus`, `expired` or `none` to force a state. An empty value detects the real one. The harness reads the `LENS_LICENSE` environment variable for this:

```bash
LENS_LICENSE=trial harness/start.sh
```

## Third-party notices

The console and the bar use Alpine.js and Phosphor Icons. Phosphor Icons are MIT licensed, the notice is in `src/main/bx/assets/ICONS-LICENSE.txt`.
