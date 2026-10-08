#!/usr/bin/env python3
"""Builds src/main/bx/assets/icons.svg, a sprite of the Phosphor icons the console and bar use.

Phosphor Icons are MIT licensed, see src/main/bx/assets/ICONS-LICENSE.txt. The sprite is inlined into each page, so nothing is fetched from outside.

  npm pack @phosphor-icons/core && tar -xzf phosphor-icons-core-*.tgz
  python3 tools/build-icons.py package/assets
"""
import re
import sys
from pathlib import Path

ICONS = [
    "squares-four", "list-bullets", "gear", "sign-out", "lock-key", "warning", "warning-circle", "warning-diamond", "check-circle", "x",
    "caret-right", "caret-down", "caret-up", "arrow-clockwise", "pulse", "magnifying-glass", "funnel", "copy", "download-simple", "cpu",
    "memory", "lightning", "clock-countdown", "timer", "stack", "plug", "database", "globe", "bug", "terminal-window", "eye", "eye-slash",
    "sun", "moon", "circle-half", "dots-six-vertical", "arrow-square-out", "shield-check", "key", "play", "pause", "info", "layout",
    "list-checks", "tree-structure", "gauge", "hard-drives", "queue", "lock-simple", "arrows-clockwise", "dots-three", "browsers", "arrow-line-down",
    "package", "file-text", "hourglass", "chart-bar", "sparkle", "sliders-horizontal", "table", "trash", "clipboard-text",
]

src = Path(sys.argv[1]) / "regular"
out = ['<svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">']
for name in ICONS:
    svg = (src / f"{name}.svg").read_text()
    inner = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    out.append(f'<symbol id="ph-{name}" viewBox="0 0 256 256" fill="currentColor">{inner}</symbol>')
out.append("</svg>")
root = Path(__file__).resolve().parent.parent / "src/main/bx/assets"
(root / "icons.svg").write_text("\n".join(out) + "\n")
print(f"wrote icons.svg with {len(ICONS)} icons")

# The bar is injected into every page, so it gets a small sprite with its own id prefix that cannot clash with the host page
BAR_ICONS = ["warning", "circle-half", "caret-up", "caret-down", "browsers", "arrow-line-down", "arrow-square-out", "dots-three", "copy"]
bar = ['<svg xmlns="http://www.w3.org/2000/svg" style="display:none" aria-hidden="true">']
for name in BAR_ICONS:
    svg = (src / f"{name}.svg").read_text()
    inner = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    bar.append(f'<symbol id="bxlens-ph-{name}" viewBox="0 0 256 256" fill="currentColor">{inner}</symbol>')
bar.append("</svg>")
(root / "bar-icons.svg").write_text("".join(bar) + "\n")
print(f"wrote bar-icons.svg with {len(BAR_ICONS)} icons")
