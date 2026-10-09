---
title: Configuration
order: 7
description: The effective BoxLang configuration by area, with the source of each value.
icon: lucide:list-checks
---

# Configuration

The Configuration page shows the settings BoxLang is running with, grouped by area, with a filter box. It reads the real configuration object from the runtime, so a key that BoxLang does not have is not listed.

| Area | Keys (when present) | Viewer |
|---|---|---|
| Runtime | version, debug mode, compiler, time zone, locale, caching and class settings, valid extensions | yes |
| Requests and sessions | request, application and session timeouts, session management and storage, cookies, global error template | yes |
| Data | default datasource, datasources, caches, queries, return formats | yes |
| Executors and tasks | executors, scheduler, watcher | yes |
| Experimental | the experimental flags | yes |
| Paths | mappings, modules and component directories, class and library paths | admin only |
| Security | allow and deny lists | admin only |
| Logging | loggers | admin only |
| Modules | module settings and runtimes | admin only |

Each value has a **source**:

| Source | Meaning |
|---|---|
| default | The value is the one BoxLang ships in its own `boxlang.json`. |
| boxlang.json | The value differs from the shipped default, so a configuration file (or an override) set it. |
| environment variable | An environment variable named `BOXLANG_<KEY>` exists for the key. |
| unknown | BoxLang does not ship a default for the key, so Lens cannot tell. |

Values whose names look secret are hidden by the shared matcher. The environment variables themselves stay on the [Environment](environment.md) page, which is for admins.

The same data feeds the short list on the Runtime tab of the bar. Turn the page off with `collectors.configuration.enabled`.
