---
title: Licensing
order: 6
description: How BX Lens detects BoxLang+, what the license states mean, and what Plus or a trial adds today.
icon: lucide:badge-check
---

# Licensing

BX Lens is a product of Ortus Solutions. License terms apply, see the [BoxLang+ plans page](https://boxlang.io/plans).

## What Plus or a trial adds today

This is the current state and it may change. When it does, this page changes with it.

Lens checks the license in one place (`Licensing.has()`). Today two features need BoxLang+ or an active trial. Everything else is open in every license state, including the whole console, the AI help and the bar.

| | Free | BoxLang+ or trial |
|---|---|---|
| Requests kept in memory | 25 at most, even if `history.maxRequests` is higher | `history.maxRequests`, as configured (default 50) |
| Errors and Reports | In memory only. Totals start at zero after a restart. The minute series covers the last 60 minutes. | Saved to disk by the disk store. Totals since first install survive restarts and upgrades. The minute series covers `store.retentionHours`. |

The disk store (`store.enabled`, on by default) writes `errors.json` and `reports.json` to `store.dir` (default `lens-data` in the BoxLang home). It keeps data for `store.retentionHours` (72), limits the errors file to `store.maxMB` (50) and writes every `store.flushSeconds` (30). Each file is replaced atomically. See [Errors and Reports](console/errors-and-reports.md#the-disk-store).

Without the license the store settings have no effect, and the Errors and Reports pages say that the data is kept in memory only. If a trial ends or a license expires, the disk store stops writing and the request history returns to 25 at the next license check (the answer is cached for 5 minutes). The saved files stay on disk. Lens reads them at the next start if the license is valid then.

The split is not final. The pages of the console and the settings do not depend on it, so nothing you configure now has to change when it does.

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
