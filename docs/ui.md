---
title: Keyboard and UI
order: 4
description: Shortcuts, layout, themes, the floating window and copy actions.
icon: lucide:keyboard
---

# Keyboard and UI

## Health strip

The bar starts as a collapsed strip with status, request time, memory, query count and template count.

![The collapsed health strip](assets/screenshots/strip-collapsed.png)

The strip border and an issues chip turn amber or red when the request is slow, has an N+1, catches an exception, or returns a 4xx or 5xx status. Click a chip to open the matching tab.

When an exception was caught, Lens opens the panel on Issues for you. Turn this off with `ui.autoOpenOnException`.

![The panel opened on Issues after a caught exception](assets/screenshots/issues-exception.png)

## Shortcuts

| Key | Action |
|---|---|
| ``Ctrl+` `` | Toggle the panel. Change it with `ui.hotkey`. |
| `1` to `9` | Switch to the tab in that position. |
| `/` | Focus the Timeline search. |

## Layout

- The panel docks at the bottom of the page.
- Drag the top edge to resize it.
- The detach button turns the panel into a floating window inside the page. The Dock button puts it back. Disable this with `ui.allowDetach`. It is not a separate browser window.

![The panel detached as a floating window](assets/screenshots/popout.png)

Lens saves the open tab, height, collapsed state, theme and detached state in the browser's `localStorage`.

## Themes

Lens has a light and a dark theme. `ui.theme` accepts `auto`, `light` or `dark`. `auto` follows your system. You can also toggle the theme in the bar.

![The panel in the light theme](assets/screenshots/light-theme.png)

## Small screens

The bar works at phone width without horizontal page scroll.

![The bar at phone width](assets/screenshots/phone.png)

## Search and filters

The Timeline filters by type (template, function, query, HTTP, transaction, timer and custom) and has a text search.

## Copy and editor links

Copy actions cover SQL (with its params in the Queries tab), a `file:line` reference, the request as JSON and a cURL command. Rows with a source location open in your editor. Set `editor.linkPattern`, and use `editor.remoteBase` and `editor.localBase` to map container paths. See [Configuration](configuration.md#editor).
