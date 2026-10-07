---
title: Templates
order: 4
description: The template and include call tree.
icon: lucide:file-code
---

# Templates

The Templates panel shows the tree of templates and includes the request ran. Each node shows its path and how long it took. Lens measures template time itself, because core does not report it.

- Templates slower than `thresholds.slowTemplateMs` (default 100 ms) raise an issue.
- `collectors.templates.max` caps the number of entries (default 300).
- Open any file in your editor from its row.

The same entries feed the [Timeline](timeline.md).
