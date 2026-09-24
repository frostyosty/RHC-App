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


def spark(p, x, y, r, color):
    """A 4-point pixel star."""
    pts = [(x, y)] + [(x + i, y) for i in range(-r, r + 1)] + [(x, y + j) for j in range(-r, r + 1)]
    if r > 1:
        pts += [(x - 1, y - 1), (x + 1, y - 1), (x - 1, y + 1), (x + 1, y + 1)]
    p.px(color, pts)


@effect('laser')
def laser():
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
            img = pk.sprinkle(img, [(x + (x - 46) * k // 2, y + (y - cy) * k // 2) for x, y in embers], col)
        frames.append(img)
    return frames, [60, 50, 50, 60, 60, 60, 80, 40]


def maw(p, gap, dx, angry):
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
def bite():
    """Steel jaws lunge in from the attacker's side, snap shut on the target,
    grind once with a red spray, then pull apart and fade."""
    # (gap, lunge offset, flash)
    steps = [(10, -14, False), (10, -6, False), (4, 0, False), (0, 2, True), (1, 1, True), (0, 0, False), (6, -3, False), (9, -6, False)]
    rng = random.Random(3)
    spray = [(rng.randint(22, 58), rng.randint(18, 46)) for _ in range(14)]
    frames = []
    for i, (gap, dx, flash) in enumerate(steps):
        p = canvas()
        maw(p, gap, dx, angry=i >= 2)
        img = p.done()
        if flash:
            q = pk.Painter(size=G)
            q.img = img
            spark(q, 44, 32, 6, '#FFFFFF')
            spark(q, 30, 31, 2, HOT)
            img = pk.sprinkle(q.img, spray, RED)
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
