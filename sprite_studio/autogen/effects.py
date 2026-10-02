"""Battle effects: one-shot GIFs played over the target in battle.

These are not creatures (no idle/walk/faint): each is a single animation,
fx_<name>.gif, drawn on the 64 grid and written at 2x like the creatures. The
effect faces right (the attacker is on the left); the game mirrors it when
the enemy attacks the player. The last frame is empty, so the view can simply
be hidden once the GIF has played through once.

Moves link to their effect through SkillEngine.SKILL_DATABASE's `fx` field
in the Android app; items call playFx directly (the thrown net, in
GameSetup.kt). Change the art here, the linking there.
"""
import math
import random

import looks
import pixelkit as pk

G = 64

EFFECTS = {}


def effect(name):
    def wrap(fn):
        EFFECTS[name] = fn
        return fn
    return wrap


def canvas():
    return pk.Painter(size=G)


# Same dark kit as the creatures (see cacheon): steel, near-black, bone teeth,
# the red the creatures' eyes turn when angry.
STEEL, PLATE, JOINT, DARK = '#4A5263', '#646E82', '#2A303C', '#15181F'
JAW, TEETH, RED, HOT = '#0B0D12', '#D8DEE6', '#FF4D5E', '#FFC8C8'
BLOOD, GUM = '#8A1E2A', '#5A2A30'


def dots(img, pts, color):
    """pk.sprinkle with float positions rounded to pixels."""
    return pk.sprinkle(img, [(round(x), round(y)) for x, y in pts], color)


def spark(p, x, y, r, color):
    """A 4-point pixel star."""
    pts = [(x, y)] + [(x + i, y) for i in range(-r, r + 1)] + [(x, y + j) for j in range(-r, r + 1)]
    if r > 1:
        pts += [(x - 1, y - 1), (x + 1, y - 1), (x - 1, y + 1), (x + 1, y + 1)]
    p.px(color, pts)


@effect('laser')
def laser(BLOOD=BLOOD, RED=RED, HOT=HOT):
    """A charge flicker at the left edge, a beam that burns across to the
    target with a white-hot core, an impact flare and embers, then smoke."""
    rng = random.Random(7)
    cy = 32
    # (beam reach, beam half-height, impact radius)
    steps = [(0, 0, 0), (26, 1, 0), (46, 2, 3), (46, 4, 6), (46, 3, 8), (46, 1, 5), (0, 0, 0), (0, 0, 0)]
    embers = [(46 + rng.randint(-2, 10), cy + rng.randint(-12, 12)) for _ in range(10)]
    frames = []
    for i, (reach, h, burst) in enumerate(steps):
        p = canvas()
        if i == 0:  # charge: red pixels gathering at the muzzle
            p.px(BLOOD, [(2, cy - 3), (2, cy + 3), (5, cy)])
            p.px(RED, [(3, cy - 1), (3, cy + 1), (4, cy)])
            p.px(HOT, [(3, cy)])
        if reach:
            # dark-red halo, red body, pale core, 1px white centre
            p.rect(BLOOD, (0, cy - h - 2, reach, cy + h + 2), shade=False)
            p.rect(RED, (0, cy - h - 1, reach, cy + h + 1), shade=False, sep=False)
            if h > 1:
                p.rect(HOT, (0, cy - h + 1, reach, cy + h - 1), shade=False, sep=False)
            p.rect('#FFFFFF', (0, cy, reach, cy + (1 if h > 2 else 0)), shade=False, sep=False)
            for x in range(4 + i * 3 % 7, reach, 9):  # scan breaks: energy, not a bar
                p.px(BLOOD, [(x, cy - h - 1), (x + 1, cy + h + 1)])
        if burst:
            x0 = 46
            p.ell(BLOOD, (x0 - burst - 1, cy - burst - 1, x0 + burst + 1, cy + burst + 1), shade=False)
            p.ell(RED, (x0 - burst, cy - burst, x0 + burst, cy + burst), shade=False, sep=False)
            if burst > 3:
                p.ell(HOT, (x0 - burst + 3, cy - burst + 3, x0 + burst - 3, cy + burst - 3), shade=False, sep=False)
                spark(p, x0, cy, burst + 3, '#FFFFFF')
        img = p.done() if (reach or burst) else p.img
        if i == 6:  # smoke where it hit
            q = canvas()
            q.ell('#3A3F4A', (38, 24, 54, 40), shade=False)
            q.ell('#4A5263', (41, 21, 52, 31), shade=False, sep=False)
            img = pk.dither(q.img, 0.5)
        if 4 <= i <= 6:  # embers fly off the impact and dim (no outline: they're light)
            k = i - 3
            col = HOT if i == 4 else (RED if i == 5 else BLOOD)
            img = dots(img, [(x + (x - 46) * k // 2, y + (y - cy) * k // 2) for x, y in embers], col)
        frames.append(img)
    return frames, [60, 50, 50, 60, 60, 60, 80, 40]


def maw(p, gap, dx, angry, RED=RED):
    """A beast's steel maw from the side, snout to the right: wedge jaws
    hinged at the back, fangs biggest at the front. gap = how far open."""
    top, bot = 31 - gap, 33 + gap
    # throat: the dark hinge joining both jaws at the back
    p.poly(JAW, [(6 + dx, top - 4), (22 + dx, top), (22 + dx, bot), (6 + dx, bot + 4)], shade=False)
    # fangs first, so the jaws overlap their roots
    for x, n in ((50, 9), (42, 6), (35, 5), (28, 4), (22, 3)):
        p.poly(TEETH, [(x + dx, top), (x + 4 + dx, top), (x + 2 + dx, top + n)], shade=False)
    for x, n in ((46, 7), (39, 5), (32, 4), (25, 3)):
        p.poly(TEETH, [(x + dx, bot), (x + 4 + dx, bot), (x + 2 + dx, bot - n)], shade=False)
    # upper jaw: skull plate rising from the hinge to a hooked snout
    p.poly(STEEL, [(4 + dx, top - 6), (16 + dx, top - 15), (40 + dx, top - 12), (60 + dx, top - 4),
                   (61 + dx, top + 3), (56 + dx, top + 1), (54 + dx, top), (6 + dx, top)])
    p.poly(PLATE, [(14 + dx, top - 13), (38 + dx, top - 11), (50 + dx, top - 7), (20 + dx, top - 8)])
    for x0 in (12, 22, 32):  # blades along the skull
        p.poly(DARK, [(x0 + dx, top - 13), (x0 - 3 + dx, top - 21), (x0 + 5 + dx, top - 12)], shade=False)
    p.rect(GUM, (8 + dx, top - 1, 54 + dx, top), shade=False, sep=False)
    # slit eye, red when it bites
    p.px(RED if angry else HOT, [(40 + dx, top - 8), (41 + dx, top - 8), (42 + dx, top - 7), (43 + dx, top - 7)])
    # lower jaw: a blade tapering to the chin
    p.poly(STEEL, [(6 + dx, bot + 6), (6 + dx, bot), (56 + dx, bot), (52 + dx, bot + 5), (30 + dx, bot + 10), (12 + dx, bot + 11)])
    p.rect(GUM, (8 + dx, bot, 52 + dx, bot + 1), shade=False, sep=False)
    # the two front fangs of each jaw on top, so they interlock over the
    # other jaw when it snaps shut (the moment the bite has to read)
    p.poly(TEETH, [(50 + dx, top), (54 + dx, top), (52 + dx, top + 9)], sep=False)
    p.poly(TEETH, [(42 + dx, top), (46 + dx, top), (44 + dx, top + 6)], sep=False)
    p.poly(TEETH, [(46 + dx, bot), (50 + dx, bot), (48 + dx, bot - 7)], sep=False)
    p.poly(TEETH, [(39 + dx, bot), (43 + dx, bot), (41 + dx, bot - 5)], sep=False)


@effect('bite')
def bite(RED=RED):
    """Steel jaws lunge in from the attacker's side, snap shut on the target,
    grind once with a red spray, then pull apart and fade."""
    # (gap, lunge offset, flash)
    steps = [(10, -14, False), (10, -6, False), (4, 0, False), (0, 2, True), (1, 1, True), (0, 0, False), (6, -3, False), (9, -6, False)]
    rng = random.Random(3)
    spray = [(rng.randint(22, 58), rng.randint(18, 46)) for _ in range(14)]
    frames = []
    for i, (gap, dx, flash) in enumerate(steps):
        p = canvas()
        maw(p, gap, dx, angry=i >= 2, RED=RED)
        img = p.done()
        if flash:
            q = pk.Painter(size=G)
            q.img = img
            spark(q, 44, 32, 6, '#FFFFFF')
            spark(q, 30, 31, 2, HOT)
            img = dots(q.img, spray, RED)
        if i >= 6:  # fade out while pulling away
            img = pk.dither(img, 0.5 if i == 6 else 0.2)
        frames.append(img)
    return frames + [canvas().img], [60, 50, 40, 90, 50, 70, 50, 50, 30]


ROPE, ROPE_DARK, WEIGHT = '#8A7A52', '#5A4E34', '#4A5263'


def mesh(p, L, T, R, B, sag, cells=6):
    """A cord net from (L, T) to (R, B); the bottom edge sags by `sag` in the
    middle so it hangs like it's draped over something."""
    def y_at(y, x):  # drape: rows sink toward the middle, more at the bottom
        mid = 1 - abs((x - (L + R) / 2) / max(1, (R - L) / 2))
        return y + sag * mid * (y - T) / max(1, B - T)
    for i in range(cells + 1):
        x = L + (R - L) * i / cells
        col = [(x, y_at(T + (B - T) * j / 8, x)) for j in range(9)]
        p.line(ROPE_DARK, [(a + 1, b + 1) for a, b in col], sep=False)
        p.line(ROPE, col, sep=False)
        y = T + (B - T) * i / cells
        row = [(L + (R - L) * j / 8, y_at(y, L + (R - L) * j / 8)) for j in range(9)]
        p.line(ROPE_DARK, [(a + 1, b + 1) for a, b in row], sep=False)
        p.line(ROPE, row, sep=False)
    p.px('#3A3222', [(round(L + (R - L) * i / cells), round(y_at(T + (B - T) * j / cells, L + (R - L) * i / cells)))
                     for i in range(cells + 1) for j in range(cells + 1)])  # knots
    for x, y in ((L, T), (R, T), (L, y_at(B, L)), (R, y_at(B, R))):
        p.ell(WEIGHT, (x - 2, y - 2, x + 2, y + 2), shade=False)
        p.px('#AEB8C6', [(round(x) - 1, round(y) - 1)])


@effect('net')
def net():
    """A thrown net: a bundle flies in from the left, opens wide over the
    target, drops and drapes, the weights cinch it tight, then it fades."""
    # (L, T, R, B, sag) per frame; the first two are the flying bundle
    steps = [None, None, (14, 10, 50, 40, 0), (6, 4, 58, 50, 0), (8, 10, 56, 56, 4), (12, 14, 52, 58, 6),
             (14, 16, 50, 58, 7), (14, 16, 50, 58, 7), (14, 16, 50, 58, 7)]
    frames = []
    for i, box in enumerate(steps):
        p = canvas()
        if box is None:  # the balled-up net in flight, weights trailing
            x = 6 if i == 0 else 22
            y = 26 if i == 0 else 22
            p.ell(ROPE, (x - 4, y - 4, x + 4, y + 4), shade=True)
            p.px(ROPE_DARK, [(x - 1, y), (x + 1, y - 2), (x + 2, y + 1), (x - 2, y - 2)])
            for dx, dy in ((-7, 3), (-6, -4)):
                p.ell(WEIGHT, (x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1), shade=False)
        else:
            mesh(p, *box)
        img = p.img  # no outline: it's a see-through mesh
        if i == 7:
            img = pk.dither(img, 0.5)
        elif i == 8:
            img = canvas().img
        frames.append(img)
    return frames, [50, 50, 60, 70, 70, 80, 250, 80, 30]


# ================================================================ move effects
# Every move in SkillEngine.SKILL_DATABASE has its own fx_<move>.gif (the
# move's name in snake_case, e.g. "Data Drain" -> fx_data_drain.gif). They're
# built from a handful of families below, each coloured by the creature
# line's accent (the same accents as the creature Kits) and given its own
# seed/shape, so no two moves look the same. The table is MOVES at the end:
# change a move's look there, then regenerate it from the Studio's Attacks tab
# or with `autogen.py --only <move>`.

# (dark, mid, hot) ramps per creature line: the glow accents from designs.py
LINE = {
    'tech': ('#0E4A5A', '#2FD8F0', '#C8F8FF'),       # cyan
    'social': ('#5A1650', '#E040C8', '#FFC8F4'),     # magenta
    'viral': ('#3A5A10', '#A8F040', '#EEFFC8'),      # viralia lime
    'gaming': ('#3A1A6A', '#9A60FF', '#E0D0FF'),     # violet
    'stream': ('#6A2A0A', '#FF8A30', '#FFE0B8'),     # ember orange
    'flying': ('#1A3A5A', '#8CCBFF', '#EAF6FF'),     # ice blue
    'shop': ('#12502A', '#40D070', '#D0FFE0'),       # green
    'legend': ('#6A4A08', '#FFC940', '#FFF4C8'),     # gold
    'wild': (BLOOD, RED, HOT),                       # the angry-eye red
    'bone': ('#3A3F4A', '#AEB8C6', '#F2F4F8'),       # steel and bone
}


def erase(p, box):
    """Clear an ellipse back to transparent (hollows rings out)."""
    m = p._mask(lambda d: d.ellipse(box, fill=1))
    p.img.paste((0, 0, 0, 0), (0, 0), m)


def blank():
    return canvas().img


def fin(frames, durations):
    """Every effect ends on an empty frame (the game hides the view late)."""
    return frames + [blank()], durations + [30]


def lit(p):
    """Glow parts: no outline, they're light."""
    return p.img


def f_slash(col, n=3, angle=1, seed=1, claw=False):
    """n diagonal cuts rake across the target, one after another, then glow
    and fade. claw: curved, thicker marks; else long straight blade cuts."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    cuts = []
    for k in range(n):
        off = (k - (n - 1) / 2) * (9 if claw else 7) + rng.randint(-1, 1)
        x0, y0, x1, y1 = 14 + off, 10, 50 + off, 54
        if angle < 0:
            x0, x1 = x1, x0
        cuts.append((x0, y0, x1, y1))
    frames, durs = [], []
    for i in range(n + 4):
        p = canvas()
        for k, (x0, y0, x1, y1) in enumerate(cuts):
            age = i - k
            if age < 0:
                continue
            t = min(1.0, (age + 1) / 2)  # draws in over two frames
            xe, ye = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            mid = ((x0 + xe) / 2 + (6 if claw else 0) * (1 if angle > 0 else -1), (y0 + ye) / 2)
            pts = [(x0, y0), mid, (xe, ye)] if claw else [(x0, y0), (xe, ye)]
            fading = i >= n + 2
            w = (3 if claw else 2) if age < 3 else 1
            p.line(dk, [(a + 1, b) for a, b in pts], width=w + 1, shade=False, sep=False)
            p.line(ht if age < 2 and not fading else md, pts, width=w, shade=False, sep=False)
            if age == 1:
                spark(p, round(xe), round(ye), 3, '#FFFFFF')
        img = lit(p)
        if i == n + 3:
            img = pk.dither(img, 0.35)
        frames.append(img)
        durs.append(45 if i < n + 1 else 70)
    return fin(frames, durs)


def f_impact(col, rings=2, seed=2, dust=True, from_above=False):
    """A body blow: a streak rushes in (from the left, or down from the sky),
    a white flash on contact, shockwave rings, dust and grit kicked out."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    grit = [(rng.uniform(-1, 1), rng.uniform(-1, 1)) for _ in range(12)]
    cx, cy = 38, 34
    frames, durs = [], []
    for i in range(8):
        p = canvas()
        if i < 2:  # the rush in
            if from_above:
                p.rect(md, (cx - 3, 0, cx + 3, 10 + i * 12), shade=False)
                p.rect(ht, (cx - 1, 0, cx + 1, 10 + i * 12), shade=False, sep=False)
            else:
                for k in range(4):
                    y = cy - 6 + k * 4
                    p.line(md if k % 2 else ht, [(2 + i * 10 + k * 2, y), (14 + i * 14 + k * 2, y)], shade=False, sep=False)
        if i == 2:
            p.ell('#FFFFFF', (cx - 9, cy - 9, cx + 9, cy + 9), shade=False)
            spark(p, cx, cy, 13, ht)
        for r in range(rings):
            age = i - 2 - r
            if 0 <= age < 5:
                rad = 6 + age * 5
                c = ht if age < 2 else (md if age < 4 else dk)
                p.ell(c, (cx - rad, cy - rad // 2 - 2, cx + rad, cy + rad // 2 + 2), shade=False)
                if rad > 4:
                    erase(p, (cx - rad + 2, cy - rad // 2, cx + rad - 2, cy + rad // 2))
        img = lit(p)
        if i >= 3:
            k = (i - 2) * 4
            img = dots(img, [(cx + dx * k, cy + dy * k * 0.7) for dx, dy in grit], '#8A7A60' if dust else md)
        if i == 7:
            img = pk.dither(img, 0.4)
        frames.append(img)
        durs.append(40 if i < 3 else 60)
    return fin(frames, durs)


def f_waves(col, kind='arc', seed=3, count=4):
    """Sound or signal: arcs (a roar or a shriek) or full rings (a broadcast)
    rolling out from the attacker's side and breaking over the target."""
    dk, md, ht = LINE[col]
    frames, durs = [], []
    ox = 2 if kind == 'arc' else 34
    for i in range(9):
        p = canvas()
        for w in range(count):
            age = i - w * 1.5
            if age < 0 or age > 5:
                continue
            r = 8 + age * 9
            c = ht if age < 2 else (md if age < 4 else dk)
            thick = 3 if age < 3 else 2
            box = (ox - r, 32 - r, ox + r, 32 + r)
            p.ell(c, box, shade=False, sep=False)
            erase(p, (box[0] + thick, box[1] + thick, box[2] - thick, box[3] - thick))
        img = lit(p)
        if kind == 'arc':  # only the forward half of each ring
            img = img.copy()
            for x in range(0, ox + 4):
                for y in range(G):
                    img.putpixel((x, y), (0, 0, 0, 0))
        if i == 8:
            img = pk.dither(img, 0.4)
        frames.append(img)
        durs.append(55)
    return fin(frames, durs)


def f_zap(col, bolts=2, seed=4):
    """Stun: jagged bolts strike the target from above, crackle, and leave
    a ring of sparks spinning round its head."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    paths = []
    for b in range(bolts):
        x = 26 + b * 14 + rng.randint(-3, 3)
        pts, y = [(x, 0)], 0
        while y < 40:
            y += rng.randint(5, 9)
            x += rng.randint(-6, 6)
            pts.append((x, y))
        paths.append(pts)
    frames, durs = [], []
    for i in range(9):
        p = canvas()
        if i in (1, 2, 4):
            for pts in paths:
                p.line(dk, [(a + 1, b) for a, b in pts], width=3, shade=False, sep=False)
                p.line(md, pts, width=2, shade=False, sep=False)
                p.line('#FFFFFF' if i == 1 else ht, pts, shade=False, sep=False)
                spark(p, pts[-1][0], pts[-1][1], 4, ht)
        if i >= 4:  # dizzy sparks orbiting
            for k in range(3):
                a = (i * 0.9 + k * 2.1)
                x, y = 32 + round(math.cos(a) * 14), 14 + round(math.sin(a) * 4)
                spark(p, x, y, 2, ht if k else md)
        img = lit(p)
        if i == 8:
            img = pk.dither(img, 0.4)
        frames.append(img)
        durs.append(50 if i < 5 else 80)
    return fin(frames, durs)


def f_toxic(col, seed=5, drip=True):
    """Poison: a sickly splash bursts on the target, then bubbles rise off it
    and drips run down while it fades."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    blobs = [(rng.randint(18, 50), rng.randint(20, 44), rng.randint(3, 6)) for _ in range(7)]
    bubbles = [(rng.randint(20, 48), rng.randint(0, 20)) for _ in range(8)]
    frames, durs = [], []
    for i in range(9):
        p = canvas()
        grow = min(1.0, (i + 1) / 3)
        if i < 7:
            for x, y, r in blobs:
                rr = max(1, round(r * grow))
                p.ell(dk, (x - rr - 1, y - rr - 1, x + rr + 1, y + rr + 1), shade=False)
                p.ell(md, (x - rr, y - rr, x + rr, y + rr), shade=False, sep=False)
                if rr > 2:
                    p.px(ht, [(x - rr // 2, y - rr // 2)])
        if drip and i >= 3:
            for x, y, r in blobs[:4]:
                p.rect(md, (x, y, x + 1, min(62, y + (i - 2) * 3)), shade=False, sep=False)
        img = p.done() if i < 7 else p.img
        if i >= 3:
            img = dots(img, [(x, 44 - (y + (i - 3) * 6) % 44) for x, y in bubbles], ht)
        if i >= 6:
            img = pk.dither(img, 0.6 if i == 6 else 0.3)
        frames.append(img)
        durs.append(60)
    return fin(frames, durs)


def f_glitch(col, seed=6, heavy=False):
    """Corruption: the target area tears into offset colour bands and
    dead-pixel blocks, flickers, then snaps back clean."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    frames, durs = [], []
    for i in range(8):
        p = canvas()
        if i not in (0, 5):
            for _ in range(10 if heavy else 6):
                y = rng.randint(8, 56)
                h = rng.randint(1, 4)
                x0 = rng.randint(4, 30)
                p.rect(rng.choice((dk, md, md, ht)), (x0, y, x0 + rng.randint(10, 30), y + h), shade=False, sep=False)
            for _ in range(8 if heavy else 4):
                x, y = rng.randint(10, 52), rng.randint(10, 52)
                p.rect(rng.choice(('#0B0D12', '#FFFFFF', ht)), (x, y, x + 2, y + 2), shade=False, sep=False)
        img = lit(p)
        if i == 7:
            img = pk.dither(img, 0.3)
        frames.append(img)
        durs.append(rng.choice((40, 60, 80)))
    return fin(frames, durs)


def f_drain(col, seed=7):
    """Drain: motes are torn out of the target and stream back to the left
    (towards the attacker), a thin tether between."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    motes = [(rng.randint(34, 56), rng.randint(18, 46), rng.uniform(0.7, 1.3)) for _ in range(14)]
    frames, durs = [], []
    for i in range(9):
        p = canvas()
        if 1 <= i <= 6:
            p.line(dk, [(0, 32), (20, 30), (44, 32)], width=3, shade=False, sep=False)
            p.line(md, [(0, 32), (20, 30), (44, 32)], shade=False, sep=False)
        if i < 3:
            p.ell(md, (40 - i * 3, 26 - i * 3, 48 + i * 3, 38 + i * 3), shade=False)
            erase(p, (42 - i * 3, 28 - i * 3, 46 + i * 3, 36 + i * 3))
        img = lit(p)
        if i >= 2:
            pts = []
            for x, y, sp in motes:
                t = min(1.0, (i - 2) * 0.22 * sp)
                pts.append((x - x * t, y + (32 - y) * t))
            q = pk.Painter(size=G)
            q.img = img
            for k, (x, y) in enumerate(pts):
                spark(q, round(x), round(y), 2 if k % 3 == 0 else 1, ht if (i + k) % 2 else md)
            img = q.img
        if i == 8:
            img = pk.dither(img, 0.3)
        frames.append(img)
        durs.append(55)
    return fin(frames, durs)


def f_ward(col, shape='hex', seed=8):
    """A barrier: panels snap together into a hex plate (or a round dome),
    flare as it locks, then fade out. Guard and buff moves."""
    dk, md, ht = LINE[col]
    cx, cy, r = 32, 32, 24
    if shape == 'hex':
        pts = [(cx + round(r * math.cos(math.pi / 3 * k + math.pi / 6)), cy + round(r * math.sin(math.pi / 3 * k + math.pi / 6))) for k in range(6)]
    frames, durs = [], []
    for i in range(8):
        p = canvas()
        grow = min(1.0, (i + 1) / 3)
        if shape == 'hex':
            q = [(cx + (x - cx) * grow, cy + (y - cy) * grow) for x, y in pts]
            p.line(dk, q + [q[0]], width=4, shade=False, sep=False)
            p.line(ht if i in (2, 3) else md, q + [q[0]], width=2, shade=False, sep=False)
            if grow == 1:
                for x, y in pts:  # the joints between panels
                    p.line(md, [(cx, cy), (cx + (x - cx) // 3, cy + (y - cy) // 3)], shade=False, sep=False)
        else:
            rr = round(r * grow)
            p.ell(dk, (cx - rr - 1, cy - rr - 1, cx + rr + 1, cy + rr + 1), shade=False, sep=False)
            p.ell(ht if i in (2, 3) else md, (cx - rr, cy - rr, cx + rr, cy + rr), shade=False, sep=False)
            p.ell(dk, (cx - rr + 3, cy - rr + 3, cx + rr - 3, cy + rr - 3), shade=False, sep=False)
            erase(p, (cx - rr + 4, cy - rr + 4, cx + rr - 4, cy + rr - 4))
            if grow == 1:  # dome ribs
                p.line(md, [(cx - rr + 4, cy), (cx + rr - 4, cy)], shade=False, sep=False)
            p.px(ht, [(cx - rr // 2, cy - rr // 2 - 3), (cx - rr // 2 + 1, cy - rr // 2 - 4)])
        if i == 3:
            spark(p, cx - 12, cy - 14, 4, '#FFFFFF')
        img = lit(p)
        if i >= 6:
            img = pk.dither(img, 0.5 if i == 6 else 0.25)
        frames.append(img)
        durs.append(60 if i != 4 else 140)
    return fin(frames, durs)


def f_spiral(col, arms=2, seed=9):
    """Hypnotise: a spinning spiral opens over the target and pulls in."""
    dk, md, ht = LINE[col]
    frames, durs = [], []
    for i in range(10):
        p = canvas()
        size = min(1.0, (i + 1) / 4) * (1.0 if i < 7 else 1 - (i - 6) * 0.25)
        for a in range(arms):
            pts = []
            for k in range(40):
                t = k / 39
                ang = t * 4 * math.pi + i * 0.8 + a * 2 * math.pi / arms
                rad = t * 24 * size
                pts.append((32 + math.cos(ang) * rad, 32 + math.sin(ang) * rad))
            p.line(dk, [(x + 1, y + 1) for x, y in pts], width=2, shade=False, sep=False)
            p.line(ht if a == 0 else md, pts, width=1, shade=False, sep=False)
        frames.append(lit(p))
        durs.append(60)
    return fin(frames, durs)


def f_grab(col, prize='coin', seed=10):
    """Steal: a hooked claw lashes in from the left, snags the target, and
    yanks a prize (coin, spray can, potion, net) back out of frame."""
    dk, md, ht = LINE[col]
    reach = [10, 28, 44, 44, 34, 18, 2, -14]
    frames, durs = [], []
    for i, x in enumerate(reach):
        p = canvas()
        p.line(dk, [(0, 30), (x, 30)], width=3, shade=False, sep=False)  # the cable
        p.line(md, [(0, 30), (x, 30)], shade=False, sep=False)
        # the hook: two curved talons
        p.poly(STEEL, [(x - 6, 26), (x + 4, 24), (x + 7, 30), (x + 4, 36), (x - 6, 34)])
        p.poly(TEETH, [(x + 4, 24), (x + 10, 22), (x + 6, 28)], shade=False)
        p.poly(TEETH, [(x + 4, 36), (x + 10, 38), (x + 6, 32)], shade=False)
        if i >= 3:  # the prize, dragged along
            px, py = x + 6, 30
            if prize == 'coin':
                p.ell('#FFC940', (px - 4, py - 4, px + 4, py + 4))
                p.px('#FFF4C8', [(px - 1, py - 2)])
            elif prize == 'potion':
                p.ell('#E04070', (px - 4, py - 2, px + 4, py + 5))
                p.rect('#AEB8C6', (px - 1, py - 5, px + 1, py - 2))
            elif prize == 'spray':
                p.rect('#8CCBFF', (px - 3, py - 5, px + 3, py + 5))
                p.rect('#15181F', (px - 1, py - 7, px + 1, py - 5))
            else:  # net
                p.ell(ROPE, (px - 4, py - 4, px + 4, py + 4))
                p.px(ROPE_DARK, [(px - 1, py), (px + 1, py - 2), (px + 2, py + 1)])
        img = p.done()
        if i == 3:
            q = pk.Painter(size=G)
            q.img = img
            spark(q, x + 8, 30, 5, ht)
            img = q.img
        frames.append(img)
        durs.append(45 if i != 3 else 110)
    return fin(frames, durs)


def f_nova(col, seed=11, rays=8):
    """Ultimate: the target is pinned by a gathering glow, then a blinding
    detonation with rays, a big shockwave and falling embers."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    embers = [(rng.uniform(-1, 1), rng.uniform(-1, 0.4)) for _ in range(18)]
    cx, cy = 34, 32
    frames, durs = [], []
    for i in range(12):
        p = canvas()
        if i < 4:  # gather: motes converging
            for k in range(rays):
                a = 2 * math.pi * k / rays + i * 0.3
                d = 28 - i * 7
                spark(p, cx + round(math.cos(a) * d), cy + round(math.sin(a) * d), 1, ht)
            p.ell(md, (cx - i, cy - i, cx + i, cy + i), shade=False)
        elif i < 9:
            age = i - 4
            rad = min(29, 6 + age * 7)
            p.ell(dk, (cx - rad - 2, cy - rad - 2, cx + rad + 2, cy + rad + 2), shade=False)
            p.ell(md, (cx - rad, cy - rad, cx + rad, cy + rad), shade=False, sep=False)
            core = max(0, rad - 8 - age * 2)
            if core:
                p.ell(ht, (cx - core, cy - core, cx + core, cy + core), shade=False, sep=False)
            if age < 2:
                p.ell('#FFFFFF', (cx - core // 2, cy - core // 2, cx + core // 2, cy + core // 2), shade=False, sep=False)
                for k in range(rays):
                    a = 2 * math.pi * k / rays + seed
                    p.line('#FFFFFF', [(cx, cy), (cx + math.cos(a) * 34, cy + math.sin(a) * 34)], shade=False, sep=False)
            if age >= 3:  # hollow out into a ring
                inner = rad - 4 - age
                erase(p, (cx - inner, cy - inner, cx + inner, cy + inner))
        img = lit(p)
        if i >= 6:
            k = (i - 5) * 5
            img = dots(img, [(cx + dx * k, cy + dy * k + (i - 6) ** 2) for dx, dy in embers], ht if i < 9 else md)
        if i >= 10:
            img = pk.dither(img, 0.5 if i == 10 else 0.2)
        frames.append(img)
        durs.append(50 if i < 4 else (40 if i < 6 else 70))
    return fin(frames, durs)


def f_blocks(col, seed=12):
    """Deletion: square blocks rain onto the target and shatter into pixels
    (wipes, takedowns, cancellations)."""
    dk, md, ht = LINE[col]
    rng = random.Random(seed)
    cubes = [(rng.randint(14, 48), rng.randint(-30, 0), rng.randint(3, 6)) for _ in range(8)]
    frames, durs = [], []
    for i in range(9):
        p = canvas()
        shards = []
        for x, y0, s in cubes:
            y = y0 + i * 9
            if y < 40:
                p.rect(md, (x - s, y - s, x + s, y + s))
                p.rect(ht, (x - s + 1, y - s + 1, x - s + 2, y - s + 2), shade=False, sep=False)
            else:
                k = y - 40
                shards += [(x - k // 2, 40 + k // 3), (x + k // 2, 40 + k // 4), (x, 40 - k // 3)]
        img = p.done()
        img = dots(img, shards, ht)
        if i == 8:
            img = pk.dither(img, 0.35)
        frames.append(img)
        durs.append(55)
    return fin(frames, durs)


def f_timewarp(col, seed=13):
    """Time: a clock face sweeps, its hand spins a full turn and the ring
    shatters outwards (Timeshift, Chrono Blast)."""
    dk, md, ht = LINE[col]
    frames, durs = [], []
    for i in range(10):
        p = canvas()
        r = 18 + (0 if i < 7 else (i - 6) * 6)
        p.ell(dk, (32 - r - 2, 32 - r - 2, 32 + r + 2, 32 + r + 2), shade=False, sep=False)
        p.ell(md, (32 - r, 32 - r, 32 + r, 32 + r), shade=False, sep=False)
        erase(p, (32 - r + 2, 32 - r + 2, 32 + r - 2, 32 + r - 2))
        if i < 7:
            for k in range(12):  # ticks
                a = math.pi * k / 6
                p.px(ht, [(32 + round(math.cos(a) * (r - 4)), 32 + round(math.sin(a) * (r - 4)))])
            a = -math.pi / 2 + i * math.pi / 3
            p.line(ht, [(32, 32), (32 + math.cos(a) * (r - 5), 32 + math.sin(a) * (r - 5))], width=2, shade=False, sep=False)
        img = lit(p)
        if i >= 8:
            img = pk.dither(img, 0.4 if i == 8 else 0.2)
        frames.append(img)
        durs.append(55)
    return fin(frames, durs)


FAMILIES = {
    'beam': lambda col, **_: laser(*LINE[col]),
    'jaws': lambda col, **_: bite(LINE[col][1]),
    'slash': f_slash, 'impact': f_impact, 'waves': f_waves, 'zap': f_zap,
    'toxic': f_toxic, 'glitch': f_glitch, 'drain': f_drain, 'ward': f_ward,
    'spiral': f_spiral, 'grab': f_grab, 'nova': f_nova, 'blocks': f_blocks,
    'time': f_timewarp,
}

# Move name (as in SkillEngine) -> (family, colour line, extra args).
# The GIF is fx_<snake_case name>.gif.
MOVES = {
    # Tech (cyan)
    'Ping': ('beam', 'tech', {}),
    'Glitch': ('glitch', 'tech', {'seed': 21}),
    'Data Drain': ('drain', 'tech', {'seed': 22}),
    'Overclock': ('ward', 'tech', {'shape': 'dome'}),
    'Static': ('zap', 'tech', {'bolts': 3, 'seed': 23}),
    'Timeshift': ('time', 'tech', {}),
    'Short Circuit': ('zap', 'tech', {'bolts': 1, 'seed': 24}),
    'Digital Swipe': ('slash', 'tech', {'n': 3, 'seed': 25}),
    'System Wipe': ('blocks', 'tech', {'seed': 26}),
    'Firewall': ('ward', 'stream', {'shape': 'hex'}),
    'Fatal Exception': ('nova', 'tech', {'seed': 27, 'rays': 6}),
    # Social (magenta / lime)
    'Shriek': ('waves', 'social', {'kind': 'arc', 'count': 3}),
    'Screech': ('waves', 'social', {'kind': 'arc', 'count': 5}),
    'Silence': ('blocks', 'social', {'seed': 31}),
    'Doxx': ('grab', 'social', {'prize': 'coin'}),
    'Ensnare': ('grab', 'social', {'prize': 'spray'}),
    'Ratio': ('toxic', 'social', {'seed': 32}),
    'Viral Surge': ('toxic', 'viral', {'seed': 33}),
    'Deplatform': ('zap', 'social', {'bolts': 2, 'seed': 34}),
    'Overload': ('nova', 'social', {'seed': 35, 'rays': 12}),
    # Gaming (violet)
    'Rapid Fire': ('impact', 'gaming', {'rings': 3, 'dust': False, 'seed': 41}),
    'Tantrum': ('waves', 'gaming', {'kind': 'ring', 'count': 3}),
    'Rage': ('zap', 'gaming', {'bolts': 2, 'seed': 42}),
    'Venom Rush': ('toxic', 'viral', {'seed': 43}),
    'Power Surge': ('ward', 'gaming', {'shape': 'hex'}),
    'Plunder': ('grab', 'gaming', {'prize': 'net'}),
    'Critical Strike': ('slash', 'gaming', {'n': 1, 'seed': 44}),
    'Parry': ('ward', 'bone', {'shape': 'dome'}),
    'Final Boss': ('nova', 'gaming', {'seed': 45, 'rays': 10}),
    # Streaming (ember)
    'Lag': ('glitch', 'stream', {'seed': 51}),
    'Jump Cut': ('slash', 'stream', {'n': 2, 'angle': -1, 'seed': 52}),
    'Binge': ('jaws', 'stream', {}),
    'Autoplay': ('ward', 'stream', {'shape': 'dome'}),
    'Hijack': ('grab', 'stream', {'prize': 'coin'}),
    'Marathon': ('impact', 'stream', {'rings': 3, 'seed': 53}),
    'Hypnotize': ('spiral', 'stream', {'arms': 2}),
    'DMCA Takedown': ('blocks', 'stream', {'seed': 54}),
    # Reclaimed / legend (gold)
    'Cyber Strike': ('impact', 'legend', {'rings': 2, 'dust': False, 'seed': 61}),
    'Aero Beam': ('beam', 'flying', {}),
    'Mecha Dash': ('impact', 'legend', {'rings': 1, 'seed': 62}),
    'Pixel Slash': ('slash', 'legend', {'n': 2, 'seed': 63}),
    'Light Pulse': ('beam', 'legend', {}),
    'Chrono Blast': ('time', 'legend', {}),
    'Nova Shield': ('ward', 'legend', {'shape': 'hex'}),
    'Orbital Cannon': ('nova', 'legend', {'seed': 64, 'rays': 16}),
    'Cleanse': ('waves', 'legend', {'kind': 'ring', 'count': 4}),
    # Wild and Flying
    'Tackle': ('impact', 'bone', {'rings': 2, 'seed': 71}),
    'Bite': ('jaws', 'wild', {}),
    'Scratch': ('slash', 'wild', {'n': 3, 'claw': True, 'seed': 72}),
    'Growl': ('waves', 'wild', {'kind': 'arc', 'count': 4}),
    'Swipe': ('slash', 'bone', {'n': 4, 'claw': True, 'angle': -1, 'seed': 73}),
    'Ambush': ('impact', 'wild', {'rings': 1, 'seed': 74}),
    'Feral Strike': ('slash', 'wild', {'n': 5, 'claw': True, 'seed': 75}),
    'Apex Predator': ('nova', 'wild', {'seed': 76, 'rays': 8}),
    'Sky-Breaker': ('impact', 'flying', {'rings': 3, 'from_above': True, 'seed': 77}),
    # Shopping (green)
    'Snatch': ('grab', 'shop', {'prize': 'potion'}),
    'Price Gouge': ('slash', 'shop', {'n': 2, 'claw': True, 'seed': 81}),
    # Everyone
    'Basic Attack': ('impact', 'bone', {'rings': 1, 'seed': 91}),
    'Cataclysm': ('nova', 'wild', {'seed': 92, 'rays': 14}),
    'Obliterate': ('nova', 'bone', {'seed': 93, 'rays': 5}),
}


def slug(move):
    """"Data Drain" -> data_drain, "Sky-Breaker" -> sky_breaker (matches SkillEngine.fxName)."""
    return '_'.join(''.join(c if c.isalnum() else ' ' for c in move.lower()).split())


def _register(move, family, col, kw):
    name = slug(move)
    if 'seed' in kw:  # its look re-rolls the random parts (see looks.py)
        EFFECTS[name] = lambda: FAMILIES[family](col, **dict(kw, seed=kw['seed'] + looks.reseed(name)))
    else:
        EFFECTS[name] = lambda: FAMILIES[family](col, **kw)


for _m, (_fam, _col, _kw) in MOVES.items():
    _register(_m, _fam, _col, _kw)

# The Studio's Attacks tab: move effects in table order (laser/bite/net are
# the shared base effects the families were built from).
MOVE_EFFECTS = [slug(m) for m in MOVES]
# The moves with random parts, which have looks. The rest are drawn one way.
HAS_LOOKS = {slug(m) for m, (_, _, kw) in MOVES.items() if 'seed' in kw}


BLADE, EDGE, HILT, GRIP = '#C8D2E0', '#FFFFFF', '#FFC940', '#6A4A08'


@effect('sword_of_the_spirit')
def sword_of_the_spirit():
    """The Sword of the Spirit relic (your punch with it equipped): a long
    bright blade swings down across the target in an arc, leaves a white-gold
    trail and a flash where it cuts, then the light fades."""
    cx, cy, L = 8, 58, 54  # the hilt sits low on the attacker's side; the blade sweeps over the target
    angles = [-100, -80, -55, -30, -10, 5, 5, 5]  # degrees from +x, sweeping down (clockwise on screen)
    frames, durs = [], []
    for i, a in enumerate(angles):
        p = canvas()
        # trail: the arc the tip has swept so far
        if 2 <= i <= 6:
            for k, b in enumerate(range(angles[max(0, i - 3)], a, 4)):
                r = math.radians(b)
                x, y = cx + math.cos(r) * (L - 2), cy + math.sin(r) * (L - 2)
                p.ell(HILT if i < 5 else '#6A4A08', (x - 2, y - 2, x + 2, y + 2), shade=False, sep=False)
        r = math.radians(a)
        ux, uy = math.cos(r), math.sin(r)
        nx, ny = -uy, ux
        tip = (cx + ux * L, cy + uy * L)
        base = (cx + ux * 10, cy + uy * 10)
        if i < 7:
            # blade: a long tapering wedge with a bright edge line
            p.poly(BLADE, [(base[0] + nx * 3, base[1] + ny * 3), tip, (base[0] - nx * 3, base[1] - ny * 3)])
            p.line(EDGE, [base, tip], shade=False, sep=False)
            # crossguard, grip and pommel
            p.line(HILT, [(base[0] + nx * 7, base[1] + ny * 7), (base[0] - nx * 7, base[1] - ny * 7)], width=3, sep=False)
            p.line(GRIP, [(cx + ux * 2, cy + uy * 2), base], width=3, sep=False)
            p.ell(HILT, (cx - 2, cy - 2, cx + 2, cy + 2), shade=False, sep=False)
        img = p.done() if i < 7 else p.img
        if i in (4, 5):  # the cut: a white flash on the target
            q = pk.Painter(size=G); q.img = img
            spark(q, 44, 32, 9 if i == 4 else 5, EDGE)
            spark(q, 40, 26, 3, HILT)
            img = q.img
        if i == 7:
            img = pk.dither(img, 0.3)
        frames.append(img)
        durs.append(60 if i < 3 else (50 if i < 6 else 90))
    return fin(frames, durs)
