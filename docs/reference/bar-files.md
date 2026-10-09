---
title: Bar Files
order: 2
description: What Lens adds to a page, the five files it loads, how they are cached and what a Content-Security-Policy needs.
icon: lucide:file-code
---

# Bar Files

Lens adds one block of about a kilobyte before the closing `</body>`, plus the data of the request. The styles, the script, Alpine.js, the markup and the icons are separate files that the browser fetches once and keeps.

```html
<!-- BX Lens -->
<link rel="stylesheet" id="bxlens-css" href="/~bxlens/index.bxm/assets/lens.css?v=3f9a1c07d2be">
<div id="bxlens" x-cloak data-base="/~bxlens/index.bxm/assets/" data-html="..." data-icons="..." data-alpine="..."></div>
<script type="application/json" id="bxlens-data">{ ... the data of this request ... }</script>
<script defer id="bxlens-js" src="/~bxlens/index.bxm/assets/lens.js?v=a41b9c2e7710"></script>
<!-- /BX Lens -->
```

| File | What it is |
|---|---|
| `lens.css` | The styles, all scoped under `#bxlens`. |
| `lens.js` | The bar component. It loads the next three files. |
| `lens.html` | The markup of the bar. |
| `bar-icons.svg` | The icon sprite (Phosphor, MIT). |
| `alpine.min.js` | Alpine.js. Loaded only when the page has no Alpine of its own. |

## Caching

The `?v=` value is the first 12 characters of the SHA-256 of the file, computed when Lens starts. A request that carries the current hash gets:

```
Cache-Control: public, max-age=31536000, immutable
ETag: "<hash>"
```

A new Lens build has new hashes, so the browser fetches the new files by itself. A request without the hash, or with an old one, gets `Cache-Control: no-cache` and the ETag, so it revalidates (a `304` when nothing changed). With `dev.reloadAssets` the files are read and hashed again on every page and are never cached, so you can edit them and refresh.

The files hold no data of any request and no secret. They are the same for everyone, so a CDN or a proxy may cache them.

## Who can fetch them

The bar files are served to anyone who can reach the URL, whatever `console.access` says, because the page of a caller who may see the bar has to load them. The check that decides who gets the bar (`bar.access`, `access.allowedHosts`, `access.requireHeader`) applies to the page, which is where the data of the request is. Console pages and the console's own files (`console.js`, `console.css`, the login page) keep the `console.access` rules and answer 404 to a caller who is not allowed.

The route needs the console path to reach BoxLang. On MiniServer that is the pass predicate in [The console URL](../guides/production.md#the-console-url). The URL is under `/~bxlens/`, which Lens never tracks.

## Alpine

`lens.js` checks for `window.Alpine`. If the page has one (started, or still loading: Lens waits for the page to finish loading before it decides), Lens registers its component with it and uses it. Otherwise it loads `alpine.min.js` from the same place. The page never gets two copies. Lens cannot know from the server whether the page has its own Alpine, which is why the decision is made in the browser.

## Content-Security-Policy

If your pages send a Content-Security-Policy, the bar needs:

| Directive | Value | Why |
|---|---|---|
| `script-src` | `'self'` and `'unsafe-eval'` | `lens.js` and `alpine.min.js` come from your own server. Alpine evaluates the expressions in the markup. The data block is `type="application/json"`, which is data and needs no `unsafe-inline`. |
| `style-src` | `'self'` | `lens.css` comes from your own server. |
| `connect-src` | `'self'` | `lens.js` fetches the markup and the icons from your own server. |

Lens sets a CSP only on its own console pages, where it is strict (`default-src 'none'` and the same-origin sources it needs). Nothing is loaded from another origin, ever.
