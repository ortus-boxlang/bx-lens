---
title: Licensing
order: 6
description: How BX Lens detects BoxLang+, what the license states mean, and what is not decided yet.
icon: lucide:badge-check
---

# Licensing

BX Lens is a product of Ortus Solutions. License terms apply, see the [BoxLang+ plans page](https://boxlang.io/plans).

## Which features need BoxLang+

Not decided yet. The split between free features and BoxLang+ (Plus) features has not been made, so **today every feature is available in every license state**. The license state is detected and shown, but it does not switch any feature on or off. Nothing on this site should be read as a feature split. This page will change when the split is decided.

## License states

Lens detects the state through the `bx-plus` module, the same way other BoxLang+ modules do. It asks the global `BoxlangLicenseService` whether the license is valid and whether it is a trial (`isValidLicense`, `isTrialMode`), and reads the expiry from `BoxlangLicenseInfo`.

| State | Label in the console | When |
|---|---|---|
| Plus active | `BoxLang+ active` | A valid license that is not a trial. |
| Trial | `Trial` with the days left | A valid trial license. The console shows a banner: you are on a trial, Plus features stop working when it ends and need a license, with a link to [boxlang.io/plans](https://boxlang.io/plans). |
| Expired | `License expired` | The license is present but not valid. The banner says Plus features may stop working. |
| Free | `Free` | `bx-plus` is not installed, or the check failed. |

![The trial banner in the console](assets/screenshots/license-trial.png)

The state shows in the console header and on the [Settings](console/settings.md) page.

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
