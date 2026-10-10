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
    "robot", "paper-plane-right", "chat-circle-dots", "stop", "arrow-counter-clockwise",
]

src = Path(sys.argv[1]) / "regular"
# Not display:none, or the logo gradient inside the sprite would not render
out = ['<svg xmlns="http://www.w3.org/2000/svg" style="position:absolute;width:0;height:0;overflow:hidden" aria-hidden="true">']
for name in ICONS:
    svg = (src / f"{name}.svg").read_text()
    inner = re.search(r"<svg[^>]*>(.*)</svg>", svg, re.S).group(1)
    out.append(f'<symbol id="ph-{name}" viewBox="0 0 256 256" fill="currentColor">{inner}</symbol>')
BX_MARK = '''<defs>
<linearGradient id="bxg" x1="8" y1="8" x2="92" y2="92" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#1af5b0"/><stop offset=".5" stop-color="#00d4ff"/><stop offset="1" stop-color="#2f6bff"/></linearGradient>
</defs>
<g fill="none" stroke="url(#bxg)" stroke-linecap="round">
<path d="M14 47 A36 36 0 0 1 47 14" stroke-width="7"/>
<path d="M57 14 A36 36 0 0 1 82 62" stroke-width="7"/>
<path d="M14 57 A36 36 0 0 0 47 86" stroke-width="7"/>
<circle cx="46" cy="46" r="25" stroke-width="7.5"/>
<path d="M33 40 A15 15 0 0 1 45 31" stroke-width="3.6" opacity=".9"/>
</g>
<path d="M66 66 L84 84" stroke="url(#bxg)" stroke-width="12" stroke-linecap="round" fill="none"/>
<rect x="52" y="74" width="5.5" height="5.5" rx="1.6" transform="rotate(35 54.7 76.7)" fill="#1af5b0"/>'''
out.append('<symbol id="bx-mark" viewBox="0 0 100 100">' + BX_MARK + '</symbol>')
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
