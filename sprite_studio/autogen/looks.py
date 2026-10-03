"""Looks: numbered variants of a row, stepped through by the Studio's
RECOLOUR button (REGENERATE on the Attacks tab), `autogen.py --only <row> --look next`.

designs.py draws each creature one way, so redrawing a row always gives the
same GIFs. A look keeps that drawing and changes its colours: the armour
(every dull colour in the design) is re-toned, and the glowing accent moves
a little along the colour wheel, staying in its line's family (Tech stays
blue-green, Social stays pink). For a move effect, a look re-rolls its random
parts (effects.py adds `reseed` to the move's seed). Look 0 is the design as
written.

The current look of each row is in looks.json, next to this file, so a
plain `autogen.py` run redraws every row in the look it was left in.
"""
import colorsys
import functools
import json
import os
import random

from pixelkit import rgb

LOOKS_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'looks.json')

# Never recoloured: the red the eyes turn when angry or hurt has to stay red.
FIXED = {rgb('#FF4D5E')}
RED_HUE = 0.98

# Armour tones: (name, hue or None to keep the design's, saturation, lightness x).
# All dark, like the cacheon kit.
ARMOURS = [
    ('obsidian', None, 0.10, 0.62),
    ('bronze', 0.08, 0.34, 0.95),
    ('verdigris', 0.46, 0.26, 0.95),
    ('navy', 0.62, 0.42, 0.85),
    ('oxide', 0.03, 0.30, 0.80),
    ('moss', 0.24, 0.24, 0.90),
    ('amethyst', 0.76, 0.28, 0.90),
    ('pale steel', None, 0.12, 1.30),
]
# How far the accent moves round the colour wheel (a full turn is 1.0).
ACCENT_SHIFTS = [-0.09, -0.06, -0.035, 0.035, 0.06, 0.09]
# Rows that are people, not creatures: skin and clothes keep their colours.
PLAIN_ROWS = {'player', 'poacher'}


def load():
    """{row: look number} for every row that isn't on look 0."""
    if not os.path.exists(LOOKS_FILE):
        return {}
    with open(LOOKS_FILE) as f:
        return {k: int(v) for k, v in json.load(f).items() if int(v)}


def save(looks):
    looks = {k: v for k, v in sorted(looks.items()) if v}
    if not looks:  # everything is back on look 0
        if os.path.exists(LOOKS_FILE):
            os.remove(LOOKS_FILE)
        return
    with open(LOOKS_FILE, 'w') as f:
        json.dump(looks, f, indent=1)
        f.write('\n')


_current = None


def current(row):
    """The look this row is on (0 unless it was changed)."""
    global _current
    if _current is None:
        _current = load()
    return _current.get(row, 0)


def step(rows, how):
    """Move rows to their next or previous look, or back to the original, and save."""
    global _current
    _current = load()
    for row in rows:
        n = _current.get(row, 0)
        _current[row] = {'next': n + 1, 'prev': max(0, n - 1), 'original': 0}[how]
    save(_current)


def reseed(row):
    """What a move effect adds to its seed in its current look."""
    return 1000 * current(row)


def describe(row, look):
    """('bronze', +0.06) for a look, or None for look 0."""
    if not look:
        return None
    rng = random.Random(f'{row}/{look}')
    start = random.Random(row).randrange(len(ARMOURS))  # each row walks the list from its own place
    return ARMOURS[(start + look - 1) % len(ARMOURS)][0], rng.choice(ACCENT_SHIFTS)


@functools.lru_cache(maxsize=None)
def recolor(row, look, accent):
    """A colour -> colour function for this row's look (None for look 0).
    `accent` is the row's own accent colour: its shift is turned round if it
    would land on the angry-eye red."""
    if not look:
        return None
    armour, dh = describe(row, look)
    _, hue, sat, lightx = next(a for a in ARMOURS if a[0] == armour)
    r, g, b = (v / 255 for v in rgb(accent))
    ah = colorsys.rgb_to_hls(r, g, b)[0]
    if abs((ah + dh - RED_HUE + 0.5) % 1.0 - 0.5) < 0.07:
        dh = -dh
    cache = {}

    def fn(c):
        c = rgb(c)
        if c not in cache:
            cache[c] = _recolor(c, hue, sat, lightx, dh)
        return cache[c]
    return fn


def _recolor(c, hue, sat, lightx, dh):
    if c in FIXED:
        return c
    h, l, s = colorsys.rgb_to_hls(*(v / 255 for v in c))
    if s >= 0.4 and l >= 0.2:   # the accent and its glow
        h = (h + dh) % 1.0
    elif l < 0.8:               # armour, joints, shadow (teeth and eye whites are lighter)
        h = h if hue is None else hue
        s = sat
        l = min(0.78, l * lightx)
    return tuple(round(v * 255) for v in colorsys.hls_to_rgb(h, l, s))
