---
title: Integrations
order: 3
description: How Lens connects to other modules, the rules every integration follows and how to add one.
icon: lucide:plug
---

# Integrations

An integration lets Lens show what another module reports, for example the SQL of bx-orm. Every integration follows the same rules.

1. **Opt in, off by default.** The setting `collectors.<id>.enabled` is `false` until an admin turns it on.
2. **The module must be installed.** Until it is, Lens registers no listener and offers no switch. The console says "Install <module> to enable".
3. **Events only.** Lens listens to the events the module announces. It never reflects into another module's classes when events exist. If a module offers a documented service call for tools (bx-orm's `ORMService.getStatistics`), Lens may call that, reflectively because the module has its own class loader, and has no dependency on the module.
4. **No restart.** The setting is live, and a module that is installed later starts to be heard when it finishes loading.

## The status

| Status | Meaning |
|---|---|
| `notInstalled` | The module is not loaded. No switch. |
| `available` | Installed, switched off. No listener. |
| `on` | Installed and switched on. The listeners are registered. |

The console lists every integration in the **Integrations** section at the top of the [Modules](../console/modules.md) page, with its status and a switch that is offered only when the module is installed (admin only, live, audited as `integration.change`). The page of the integration (for example [ORM](../panels/orm.md)) shows the same status.

The server enforces the rules: `POST /api/integrations/{id}` answers 409 when the module is not installed, 403 for a viewer, in read-only mode (`console.readOnly`) and without the CSRF token, and 404 for an unknown id.

## Current integrations

| Id | Module (as listed on the Modules page) | Setting | Listens to |
|---|---|---|---|
| `orm` | `orm` (bx-orm 1.7.2 or later) | `collectors.orm.enabled` | `onORMQuery`, `onORMFlush`, `onORMException` |

## Adding an integration

1. **Describe it** in `Integrations.ALL` (`Integrations.java`): an id, a display name, the module name the module registers under (the `moduleName` of its `box.json`, as the Modules page shows it), what to install and a one line description.
2. **Write the collector** in `interceptors/collectors/`. Extend `BaseCollector`, give it `@InterceptionPoint` methods named like the events, return the integration id from `integration()` and the same id from `id()`, and return `false` from `enabledByDefault()`. `LensService.reconcileCollectors` registers a collector with an integration only while `Integrations.on( id )` is true, and removes it when the setting is switched off or the module goes away. A new module event (`postModuleLoad`, `postModuleUnload`) already triggers that, so nothing else is needed.
3. **Add it to the builtins** in `LensService.builtIns()`. The id gets its `collectors.<id>.enabled` setting in `SettingsRegistry` (live, default `false`) from the collector list. Declare `<id> : { enabled : false }` in `ModuleConfig.bx` and add the row to `docs/configuration.md`.
4. **Count and store with bounds.** Per request data goes into `LensRequest` (spans, `queries` and so on). Totals across requests go into a bounded store like `OrmTotals`. Never keep parameter values, and redact before storing.
5. **Show it.** Add the page or section to `console.html` and `console.js`. If the page is gated, add the feature to `Licensing.PLUS_FEATURES`. The integration list and its switch need no new code.
6. **Test it.** Unit tests drive the collector with fake events (see `OrmCollectorTest`), the status logic needs no module (`IntegrationsTest` passes a fake "installed" answer), and a Playwright test covers the page on a harness server with the module and on one without (`orm.spec.ts`, `free.spec.ts`).
7. **Document it** on a panel page and in the table above, and add a harness scenario when the integration needs one.

Where the module needs to be in the harness, extend `harness/start.sh` the way `WITH_ORM=1` does.
