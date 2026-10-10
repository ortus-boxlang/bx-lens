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

# Lensy, the Box Agent: a friendly box with a lens for one eye. Four states, each its own symbol so it stays crisp at 16, 24 and 64 px.
# The gradients live in one defs block of the sprite. The thinking state blinks with SMIL, and lensy-think-still is the same face without
# the motion, which the console uses under prefers-reduced-motion.
LENSY_DEFS = '''<defs>
<linearGradient id="lensy-g" x1="6" y1="6" x2="58" y2="58" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#1af5b0"/><stop offset=".5" stop-color="#00d4ff"/><stop offset="1" stop-color="#2f6bff"/></linearGradient>
<linearGradient id="lensy-lid" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1af5b0"/><stop offset="1" stop-color="#00d4ff"/></linearGradient>
</defs>'''
LENSY_BODY = '''<rect x="9" y="19" width="46" height="37" rx="9" fill="#0b1d33" stroke="url(#lensy-g)" stroke-width="3"/>
<rect x="6" y="9" width="52" height="13" rx="5" fill="url(#lensy-lid)"/>
<rect x="29" y="9" width="6" height="13" fill="#060f1b" opacity=".28"/>'''
LENSY_EYE = '''<circle cx="40" cy="37" r="10.5" fill="#060f1b" stroke="url(#lensy-g)" stroke-width="3.5"/>'''
def lensy(state):
    iris = '<circle cx="40" cy="37" r="5.6" fill="#00d4ff"/><circle cx="40" cy="37" r="2.7" fill="#060f1b"/><circle cx="42.2" cy="34.6" r="1.7" fill="#ffffff"/>'
    small = '<circle cx="22" cy="36" r="3.4" fill="#1af5b0"/>'
    smile = '<path d="M22 47.5 Q32 54.5 42 47.5" fill="none" stroke="#1af5b0" stroke-width="3" stroke-linecap="round"/>'
    if state == "think" or state == "think-still":
        blink = '' if state == "think-still" else '<animate attributeName="r" values="5.6;5.6;0.8;5.6" keyTimes="0;.82;.9;1" dur="2.8s" repeatCount="indefinite"/>'
        iris = '<circle cx="40" cy="37" r="5.6" fill="#00d4ff">' + blink + '</circle><circle cx="40" cy="37" r="2.7" fill="#060f1b"/><circle cx="42.2" cy="34.6" r="1.7" fill="#ffffff"/>'
        smile = '<path d="M26 49 H38" fill="none" stroke="#1af5b0" stroke-width="3" stroke-linecap="round"/><circle cx="52" cy="6" r="1.8" fill="#00d4ff"/><circle cx="57" cy="3" r="1.3" fill="#00d4ff" opacity=".7"/>'
    elif state == "happy":
        small = '<path d="M18.5 37.5 Q22 32.5 25.5 37.5" fill="none" stroke="#1af5b0" stroke-width="3" stroke-linecap="round"/>'
        smile = '<path d="M20 45.5 Q32 58.5 44 45.5 Z" fill="#1af5b0"/>'
        iris = '<circle cx="40" cy="37" r="6" fill="#00d4ff"/><circle cx="40" cy="37" r="2.7" fill="#060f1b"/><circle cx="42.2" cy="34.6" r="1.7" fill="#ffffff"/><circle cx="35.5" cy="40.5" r="1" fill="#ffffff"/>'
    elif state == "error":
        small = '<path d="M19.5 33 L24.5 39 M24.5 33 L19.5 39" fill="none" stroke="#ff5d6c" stroke-width="3" stroke-linecap="round"/>'
        smile = '<path d="M22 51 Q27 46 32 51 T42 51" fill="none" stroke="#ff5d6c" stroke-width="3" stroke-linecap="round"/>'
        iris = '<circle cx="40" cy="37" r="4.2" fill="#ff5d6c"/><circle cx="40" cy="37" r="1.9" fill="#060f1b"/><circle cx="41.6" cy="35.4" r="1.2" fill="#ffffff"/>'
    return LENSY_BODY + LENSY_EYE + iris + small + smile
LENSY_STATES = ["idle", "think", "think-still", "happy", "error"]
out.append(LENSY_DEFS)
for st in LENSY_STATES:
    out.append(f'<symbol id="lensy-{st}" viewBox="0 0 64 64">' + lensy("idle" if st == "idle" else st) + '</symbol>')
(Path(__file__).resolve().parent.parent / "docs/assets").mkdir(exist_ok=True)
(Path(__file__).resolve().parent.parent / "docs/assets/lensy.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="256" height="256" role="img" aria-label="Lensy, the Box Agent">' + LENSY_DEFS + lensy("idle") + "</svg>\n")
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
# Lensy for the Ask link of the bar, with its own gradient ids so it cannot clash with the host page
bar.append('<symbol id="bxlens-lensy" viewBox="0 0 64 64">' + LENSY_DEFS.replace("lensy-g", "bxlens-lensy-g").replace("lensy-lid", "bxlens-lensy-lid") + lensy("idle").replace("lensy-g", "bxlens-lensy-g").replace("lensy-lid", "bxlens-lensy-lid") + '</symbol>')
bar.append("</svg>")
(root / "bar-icons.svg").write_text("".join(bar) + "\n")
print(f"wrote bar-icons.svg with {len(BAR_ICONS)} icons")
