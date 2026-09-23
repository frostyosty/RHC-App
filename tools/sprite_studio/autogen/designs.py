"""Hand-authored pixel designs for every sprite row in the Studio matrix.

Each design is `fn(p, s)` drawing onto a mirror-aware Painter for one Pose.
Side views face right. Front views are drawn as the left half only (the
Painter mirrors it), except where asymmetry is wanted (walking legs, mics).
Ground line is y=29; keep ~3px free at the right so attack lunges fit.
"""
import math
from dataclasses import dataclass, replace

from pixelkit import lo

G = 29


@dataclass(frozen=True)
class Pose:
    front: bool = False
    step: int = 0        # walk phase 0..3 (1 and 3 are mid-stride)
    flap: int = 0        # wings: -1 up, 0 level, 1 down
    eyes: str = 'open'   # open | closed | hurt | happy | angry
    mouth: bool = False
    t: int = 0           # frame counter for glows, spinners, blinks

    def but(self, **kw):
        return replace(self, **kw)


@dataclass
class Design:
    name: str
    fn: object
    category: str
    fx: str
    color: str
    float: bool = False
    outline: bool = True


DESIGNS = {}


def design(name, category, fx, color, float=False, outline=True):
    def wrap(fn):
        DESIGNS[name] = Design(name, fn, category, fx, color, float, outline)
        return fn
    return wrap


# ---------------------------------------------------------------- helpers
def _gait(s, grp):
    """(dx, lift) for a leg in group 0/1 at the current walk step."""
    if s.step == 1:
        return (1, 1) if grp == 0 else (-1, 0)
    if s.step == 3:
        return (-1, 0) if grp == 0 else (1, 1)
    return (0, 0)


def leg(p, s, col, x, top, grp, w=2, foot=None):
    dx, lift = _gait(s, grp)
    p.rect(col, (x + dx, top, x + dx + w - 1, G - lift), shade=False)
    if foot:
        p.rect(foot, (x + dx, G - lift, x + dx + w, G - lift), shade=False, sep=False)


def legs_front(p, s, col, x, top, w=2, foot=None):
    m, p.mirror = p.mirror, False
    for side, xx in ((0, x), (1, 31 - x - w + 1)):
        lift = 1 if (s.step == 1 and side == 0) or (s.step == 3 and side == 1) else 0
        p.rect(col, (xx, top - lift, xx + w - 1, G - lift), shade=False)
        if foot:
            fx = xx - 1 if side == 0 else xx
            p.rect(foot, (fx, G - lift, fx + w, G - lift), shade=False, sep=False)
    p.mirror = m


def mouth(p, s, x, y, w=3, color='#2A0E14'):
    if s.mouth:
        p.px(color, [(x + i, y + j) for i in range(w) for j in range(2)])
        p.px('#E8627A', [(x + 1, y + 1)])
    elif s.eyes == 'happy':
        p.px(color, [(x, y), (x + 1, y + 1), (x + w - 1, y)])


def wing_y(s):
    return {-1: -3, 0: 0, 1: 2}[s.flap]


def spinner(p, cx, cy, t, on, off):
    ring = [(0, -2), (1, -1), (2, 0), (1, 1), (0, 2), (-1, 1), (-2, 0), (-1, -1)]
    for i, (dx, dy) in enumerate(ring):
        p.px(on if (i - t) % 8 in (0, 1) else off, [(cx + dx, cy + dy)])


# ================================================================ TECH
@design('bytelet', 'Tech', 'bits', '#35E0F0')
def bytelet(p, s):
    steel, dark, cyan = '#8FA3B8', '#16303C', '#35E0F0'
    glow = cyan if s.t % 4 < 2 else '#B8FBFF'
    if s.front:
        p.line('#5E6F82', [(12, 14), (10, 8)])
        p.ell(glow, (8, 6, 11, 9))
        legs_front(p, s, '#5E6F82', 10, 24)
        p.part(steel, lambda d: d.rounded_rectangle((8, 13, 23, 25), 3, fill=1))
        p.part(dark, lambda d: d.rounded_rectangle((10, 15, 21, 22), 1, fill=1), shade=False)
        screen_eyes(p, s, 12, 17, cyan, front=True)
        return
    leg(p, s, lo('#5E6F82'), 11, 23, 1)
    leg(p, s, lo('#5E6F82'), 19, 23, 0)
    p.line('#5E6F82', [(12, 13), (10, 7)])
    p.ell(glow, (8, 5, 11, 8))
    p.part(steel, lambda d: d.rounded_rectangle((7, 13, 23, 25), 3, fill=1))
    p.px('#C9D6E3', [(10, 16), (11, 16), (10, 17)])
    p.part(dark, lambda d: d.rounded_rectangle((16, 15, 23, 22), 1, fill=1), shade=False)
    screen_eyes(p, s, 18, 17, cyan)
    leg(p, s, '#5E6F82', 13, 24, 0)
    leg(p, s, '#5E6F82', 21, 24, 1)


def screen_eyes(p, s, x, y, col, front=False):
    """Pixel eyes on a dark screen. Front: left eye at x (mirrored)."""
    xs = [x] if front else [x, x + 3]
    for ex in xs:
        if s.eyes == 'closed':
            p.px(col, [(ex, y + 1), (ex + 1, y + 1)])
        elif s.eyes == 'hurt':
            p.px('#FF5A5A', [(ex, y), (ex + 1, y + 1), (ex, y + 2)] if not front else [(ex, y), (ex + 1, y + 1), (ex, y + 2), (ex + 2, y), (ex + 2, y + 2)])
        elif s.eyes == 'happy':
            p.px(col, [(ex, y + 1), (ex + 1, y)] if not front else [(ex, y + 1), (ex + 1, y), (ex + 2, y + 1)])
        elif s.eyes == 'angry':
            p.px('#FF5A5A', [(ex, y), (ex + 1, y + 1), (ex, y + 1), (ex + 1, y + 2)])
        else:
            p.px(col, [(ex, y), (ex + 1, y), (ex, y + 1), (ex + 1, y + 1)])
    if s.mouth:
        p.px(col, [(x + (0 if front else 0) + i, y + 4) for i in range(4 if front else 5)])


@design('cacheon', 'Tech', 'bits', '#35E0F0')
def cacheon(p, s):
    steel, dark, cyan = '#7C8DA6', '#4D5A70', '#35E0F0'
    if s.front:
        leg(p, s, lo(dark), 8, 21, 0)
        p.ell(steel, (8, 14, 23, 25))
        p.px(cyan, [(10, 19), (11, 19), (12, 20), (13, 20)])
        legs_front(p, s, dark, 11, 22, 3, foot=cyan)
        p.poly(steel, [(10, 9), (8, 1), (14, 6)])
        p.px(cyan, [(9, 4), (10, 6)])
        p.ell(steel, (9, 5, 22, 17))
        p.rect('#0F2632', (11, 9, 20, 12), shade=False)
        visor(p, s, 12, 10, front=True)
        p.ell('#A9B7CA', (12, 12, 19, 17))
        p.px('#1A1F2A', [(15, 13)])
        if s.mouth:
            p.px('#1A1F2A', [(13, 15), (14, 16), (15, 16)])
            p.px('#FFFFFF', [(13, 16)])
        return
    p.line(dark, [(7, 17), (3, 11)], width=2)
    p.px(cyan, [(2, 10), (3, 10), (2, 9)])
    leg(p, s, lo(dark), 8, 21, 1, 3)
    leg(p, s, lo(dark), 17, 21, 0, 3)
    p.ell(steel, (5, 13, 22, 24))
    p.px(cyan, [(8, 18), (9, 18), (10, 18), (11, 19), (12, 19), (13, 19), (14, 18), (15, 18)])
    p.px('#B8FBFF', [(11, 19)] if s.t % 4 < 2 else [(14, 18)])
    leg(p, s, dark, 10, 22, 0, 3, foot=cyan)
    leg(p, s, dark, 19, 22, 1, 3, foot=cyan)
    p.poly(steel, [(17, 9), (17, 2), (22, 7)])
    p.px(cyan, [(18, 5)])
    p.ell(steel, (16, 6, 26, 16))
    p.rect('#A9B7CA', (23, 11, 28, 15))
    p.px('#1A1F2A', [(28, 11)])
    p.rect('#0F2632', (20, 8, 25, 10), shade=False)
    visor(p, s, 21, 9)
    if s.mouth:
        p.px('#1A1F2A', [(24, 15), (25, 15), (26, 15), (27, 15), (28, 15), (24, 16), (25, 16), (26, 16)])
        p.px('#FFFFFF', [(25, 15), (27, 15)])


def visor(p, s, x, y, front=False):
    cyan = '#35E0F0'
    if s.eyes == 'closed':
        return
    col = '#FF5A5A' if s.eyes in ('hurt', 'angry') else cyan
    pts = [(x, y), (x + 1, y)] if front else [(x + 2, y), (x + 3, y)]
    if s.eyes == 'happy':
        pts = [(x, y + 1), (x + 1, y)] if front else [(x + 2, y + 1), (x + 3, y)]
    p.px(col, pts)


@design('technophasia', 'Tech', 'bits', '#35E0F0')
def technophasia(p, s):
    body, frame, scr, cyan = '#4A5670', '#6B7A94', '#0D2030', '#35E0F0'
    core = '#B8FBFF' if s.t % 4 < 2 else cyan
    if s.front:
        legs_front(p, s, '#39435A', 9, 22, 4)
        p.rect(body, (3, 12, 6, 22))
        p.ell(frame, (1, 20, 7, 26))
        p.part(body, lambda d: d.rounded_rectangle((7, 11, 24, 24), 2, fill=1))
        p.ell(core, (13, 14, 18, 19))
        p.poly(frame, [(7, 11), (3, 8), (4, 13)])
        p.part(frame, lambda d: d.rounded_rectangle((8, 1, 23, 12), 2, fill=1))
        p.rect(scr, (10, 3, 21, 10), shade=False)
        screen_eyes(p, s, 11, 5, cyan, front=True)
        p.px('#FFFFFF', [(10, 3)])
        return
    p.line('#27303F', [(10, 8), (6, 12), (6, 18)], width=2)
    p.rect(lo(body), (18, 12, 21, 22))
    p.ell(lo(frame), (17, 20, 22, 25))
    leg(p, s, lo('#39435A'), 9, 21, 1, 4)
    p.part(body, lambda d: d.rounded_rectangle((6, 10, 21, 23), 2, fill=1))
    p.ell(core, (10, 14, 15, 19))
    p.poly(frame, [(7, 10), (3, 6), (5, 12)])
    leg(p, s, '#39435A', 13, 22, 0, 4)
    p.part(frame, lambda d: d.rounded_rectangle((11, 1, 26, 12), 2, fill=1))
    p.rect(scr, (14, 3, 25, 10), shade=False)
    screen_eyes(p, s, 17, 5, cyan)
    p.px('#FFFFFF', [(14, 3)])
    p.rect(body, (19, 13, 23, 21))
    p.ell(frame, (20, 20, 26, 26))
    p.px('#B8FBFF', [(22, 22)])


# ================================================================ SOCIAL
@design('chirplet', 'Social', 'heart', '#FF6FAE')
def chirplet(p, s):
    pink, belly, beak, leg_c = '#FF6FAE', '#FFD1E4', '#FFB23F', '#E08A2E'
    wy = wing_y(s)
    if s.front:
        legs_front(p, s, leg_c, 12, 25, 1, foot=leg_c)
        p.line(lo(pink), [(15, 12), (13, 7)])
        p.line(pink, [(15, 12), (15, 6)])
        p.ell(pink, (7, 11, 24, 27))
        p.ell(belly, (10, 17, 21, 26))
        p.ell(lo(pink), (4, 16 + wy, 8, 22 + wy))
        p.eye(10, 14, s.eyes, big=True, side=False)
        p.poly(beak, [(14, 19), (17, 19), (16, 22), (15, 22)])
        if s.mouth:
            p.px('#8A2C00', [(15, 20), (16, 20)])
        p.px('#FF9CC6', [(9, 19), (10, 19)])
        return
    p.poly(lo(pink), [(10, 18), (4, 14), (5, 21)])
    leg(p, s, leg_c, 14, 25, 0, 1, foot=leg_c)
    leg(p, s, leg_c, 17, 25, 1, 1, foot=leg_c)
    p.line(lo(pink), [(14, 13), (12, 8)])
    p.line(pink, [(16, 13), (16, 7)])
    p.ell(pink, (8, 12, 23, 27))
    p.ell(belly, (14, 18, 22, 26))
    p.poly(lo(pink), [(9, 18 + wy), (15, 17 + wy), (16, 21 + wy), (11, 23 + wy)])
    p.eye(17, 15, s.eyes, big=True)
    if s.mouth:
        p.poly(beak, [(22, 17), (27, 17), (22, 19)])
        p.poly(beak, [(22, 21), (26, 22), (22, 22)])
    else:
        p.poly(beak, [(22, 18), (27, 20), (22, 21)])
    p.px('#FF9CC6', [(18, 20), (19, 20)])
    # speech bubble "tweet"
    p.ell('#FFFFFF', (19, 3, 28, 9), shade=False)
    p.px('#FFFFFF', [(20, 10), (19, 11)])
    p.px(pink, [(21 + 2 * i, 6) for i in range(3) if i <= s.t % 4])


@design('viralia', 'Social', 'heart', '#D9469B')
def viralia(p, s):
    fur, fluff, orb = '#D9469B', '#FFC4E1', '#9BFF6A'

    def orbs(pts):
        for x, y in pts:
            p.ell(orb, (x - 1, y - 1, x + 1, y + 1), shade=False)
            p.px('#5ED13A', [(x - 2, y), (x + 2, y), (x, y - 2), (x, y + 2)])

    sway = 1 if s.t % 8 >= 4 else 0
    if s.front:
        p.line(fur, [(12, 20), (6, 13), (4, 8 + sway)], width=2)
        orbs([(4, 7 + sway)])
        legs_front(p, s, lo(fur), 11, 23, 2, foot=fluff)
        p.ell(fur, (9, 15, 22, 25))
        p.ell(fluff, (12, 16, 19, 23))
        p.poly(fur, [(10, 8), (8, 0), (14, 5)])
        p.px(fluff, [(9, 3), (10, 5)])
        p.ell(fur, (8, 4, 23, 16))
        p.eye(10, 8, s.eyes, side=False)
        p.ell(fluff, (12, 11, 19, 16))
        p.px('#40102A', [(15, 11)])
        if s.mouth:
            p.px('#40102A', [(14, 14), (15, 14), (14, 15), (15, 15)])
        return
    for tip in ((3, 9 + sway), (2, 15), (6, 5 + sway)):
        p.line(fur, [(9, 18), ((9 + tip[0]) // 2 - 1, (18 + tip[1]) // 2), tip], width=2)
    orbs([(3, 9 + sway), (2, 15), (6, 5 + sway)])
    leg(p, s, lo(fur), 9, 21, 1)
    leg(p, s, lo(fur), 17, 21, 0)
    p.ell(fur, (7, 14, 22, 24))
    p.ell(fluff, (16, 15, 23, 23))
    leg(p, s, fur, 11, 22, 0, foot=fluff)
    leg(p, s, fur, 20, 22, 1, foot=fluff)
    p.poly(fur, [(18, 9), (18, 1), (23, 7)])
    p.px(fluff, [(19, 5), (19, 6)])
    p.ell(fur, (16, 6, 26, 16))
    if s.mouth:
        p.poly(fluff, [(24, 10), (29, 10), (24, 12)])
        p.poly(fluff, [(24, 14), (28, 15), (24, 15)])
        p.px('#40102A', [(25, 12), (26, 12), (25, 13)])
    else:
        p.poly(fluff, [(24, 10), (29, 12), (24, 15)])
        p.px('#40102A', [(29, 12)])
    p.eye(20, 9, s.eyes)


@design('trendrake', 'Social', 'heart', '#B8337A')
def trendrake(p, s):
    hide, belly, horn = '#B8337A', '#FFB0D0', '#F2E6C8'
    wy = wing_y(s)
    if s.front:
        p.poly(lo(hide), [(9, 15), (1, 4 + wy), (3, 12 + wy), (0, 17 + wy), (8, 20)])
        legs_front(p, s, lo(hide), 9, 23, 3, foot=horn)
        p.ell(hide, (7, 12, 24, 26))
        p.ell(belly, (11, 15, 20, 25))
        for y in (18, 21):
            p.px(lo(belly), [(13, y), (14, y)])
        p.line(horn, [(11, 5), (8, 0)])
        p.ell(hide, (9, 2, 22, 12))
        p.ell(belly, (12, 8, 19, 13))
        p.px('#40102A', [(14, 9)])
        p.eye(10, 5, s.eyes, side=False)
        if s.mouth:
            p.px('#40102A', [(13, 11), (14, 11), (14, 12)])
            p.px('#FFFFFF', [(13, 12)])
        return
    p.poly(lo(hide), [(14, 13), (13, 3 + wy), (19, 7 + wy), (21, 4 + wy), (19, 13)])
    p.poly(hide, [(8, 17), (2, 12), (1, 16), (8, 22)])
    p.poly(belly, [(3, 11), (0, 13), (2, 15)])
    leg(p, s, lo(hide), 8, 21, 1, 3)
    leg(p, s, lo(hide), 18, 21, 0, 3)
    p.ell(hide, (5, 13, 23, 25))
    p.ell(belly, (13, 17, 23, 24))
    p.px(belly, [(8, 16), (10, 16), (7, 17), (8, 17), (9, 17), (10, 17), (11, 17), (8, 18), (10, 18), (7, 19), (8, 19), (9, 19), (10, 19), (11, 19), (8, 20), (10, 20)])
    leg(p, s, hide, 10, 22, 0, 3, foot=horn)
    leg(p, s, hide, 20, 22, 1, 3, foot=horn)
    p.poly(hide, [(17, 16), (20, 8), (24, 9), (23, 17)])
    p.line(horn, [(21, 4), (17, 0)])
    p.ell(hide, (19, 2, 28, 11))
    if s.mouth:
        p.rect(hide, (25, 5, 30, 7))
        p.rect(belly, (25, 9, 29, 10))
        p.px('#FFFFFF', [(26, 8), (28, 8)])
    else:
        p.rect(hide, (25, 6, 30, 9))
        p.px('#40102A', [(30, 6)])
    p.eye(22, 4, s.eyes)
    p.poly(hide, [(10, 14), (8, 1 + wy), (13, 5 + wy), (17, 1 + wy), (17, 14)])
    p.px(belly, [(10, 5 + wy), (14, 5 + wy)])


# ================================================================ GAMING
@design('noobit', 'Gaming', 'slash', '#8E5BD6')
def noobit(p, s):
    fur, set_c, mic = '#8E5BD6', '#2E2E3A', '#6BFF7A'
    if s.front:
        legs_front(p, s, lo(fur), 10, 26, 3)
        p.ell(fur, (7, 11, 24, 28))
        p.ell('#B795EC', (11, 18, 20, 27))
        p.line(set_c, [(7, 15), (9, 9), (15, 7)], width=2)
        p.ell(set_c, (4, 14, 8, 21))
        p.eye(10, 15, s.eyes, big=True, side=False)
        p.px('#F28FD0', [(9, 20)])
        m, p.mirror = p.mirror, False
        p.line(set_c, [(6, 20), (10, 23)])
        p.px(mic, [(11, 23), (11, 24)])
        p.mirror = m
        if s.mouth:
            p.px('#2A0E24', [(15, 21), (16, 21), (15, 22), (16, 22)])
        if s.eyes == 'hurt':
            p.px('#7FD4FF', [(10, 19), (10, 20)])
        return
    leg(p, s, lo(fur), 10, 26, 1, 4)
    p.ell(fur, (7, 11, 24, 28))
    p.ell('#B795EC', (15, 18, 23, 27))
    p.px('#B795EC', [(15, 10), (16, 9)])
    p.line(set_c, [(13, 14), (14, 10), (18, 9), (22, 11)], width=2)
    p.ell(set_c, (10, 14, 15, 20))
    p.px(mic, [(12, 16)])
    p.line(set_c, [(15, 19), (20, 22)])
    p.px(mic, [(21, 22), (21, 23)])
    p.eye(18, 15, s.eyes, big=True)
    p.px('#F28FD0', [(19, 20), (20, 20)])
    mouth(p, s, 21, 21, 2)
    if s.eyes == 'hurt':
        p.px('#7FD4FF', [(19, 19), (19, 20)])
    leg(p, s, lo(fur), 16, 26, 0, 4)


@design('skirmalot', 'Gaming', 'slash', '#5D3FA8')
def skirmalot(p, s):
    shell, head, steel, glow = '#5D3FA8', '#2E2350', '#C9C9D6', '#7CFF6B'
    if s.front:
        legs_front(p, s, head, 11, 23, 3)
        p.rect(head, (4, 15, 7, 21))
        p.ell(shell, (6, 10, 25, 25))
        p.px(lo(shell), [(15, y) for y in range(11, 25)])
        p.px('#9A7BE8', [(10, 13), (11, 14)])
        p.ell(head, (10, 6, 21, 15))
        p.poly(steel, [(15, 7), (14, 3), (15, 0), (16, 0)])
        eye_glow(p, s, 12, 10, glow, front=True)
        if s.mouth:
            p.px(steel, [(13, 14), (14, 15)])
        return
    leg(p, s, lo(head), 11, 23, 1, 3)
    p.rect(lo(shell), (9, 14, 12, 20))
    p.ell(shell, (6, 10, 21, 25))
    p.line(lo(shell), [(8, 12), (14, 24)])
    p.px('#9A7BE8', [(9, 13), (10, 13), (11, 14)])
    leg(p, s, head, 15, 23, 0, 3)
    p.ell(head, (16, 8, 25, 17))
    p.poly(steel, [(23, 10), (29, 2), (30, 3), (26, 12)])
    p.px('#FFFFFF', [(28, 4)])
    eye_glow(p, s, 21, 11, glow)
    if s.mouth:
        p.px(steel, [(24, 15), (25, 16), (23, 16)])
    p.ell(steel, (18, 15, 24, 23))
    p.px(shell, [(21, 17), (20, 18), (21, 18), (22, 18), (21, 19), (21, 20)])


def eye_glow(p, s, x, y, col, front=False):
    if s.eyes == 'closed':
        p.px(lo(col), [(x, y + 1), (x + 1, y + 1)])
    elif s.eyes == 'hurt':
        p.px('#FF5A5A', [(x, y), (x + 1, y + 1)])
    elif s.eyes == 'happy':
        p.px(col, [(x, y + 1), (x + 1, y)])
    else:
        p.px(col, [(x, y), (x + 1, y), (x + 1 if front else x, y + 1)])


@design('grindlord', 'Gaming', 'slash', '#6A2FB0')
def grindlord(p, s):
    hood, belly, skin, face, can = '#6A2FB0', '#9A6BE0', '#555566', '#9A8A80', '#6BFF7A'
    rgb_c = ['#FF4FD8', '#4FD8FF', '#6BFF7A', '#FFD24F'][s.t % 4]
    if s.front:
        legs_front(p, s, lo(skin), 9, 24, 4)
        p.rect(skin, (1, 11, 6, 23))
        p.ell(skin, (0, 21, 7, 28))
        p.ell(hood, (4, 7, 27, 26))
        p.ell(belly, (10, 14, 21, 25))
        p.ell(skin, (9, 1, 22, 12))
        p.ell(face, (11, 5, 20, 12))
        p.px('#3A2A30', [(14, 9), (15, 9)])
        p.eye(11, 5, 'angry' if s.eyes == 'open' else s.eyes, big=True, side=False)
        p.line('#22222A', [(8, 6), (10, 1), (15, 0)], width=2)
        p.ell(rgb_c, (7, 4, 10, 9), shade=False)
        if s.mouth:
            p.rect('#2A0E14', (13, 10, 18, 11), shade=False)
            p.px('#FFFFFF', [(13, 10), (15, 10)])
        return
    p.rect(lo(skin), (18, 12, 22, 23))
    p.ell(lo(skin), (17, 21, 23, 27))
    leg(p, s, lo(skin), 8, 23, 1, 5)
    p.ell(hood, (4, 7, 22, 26))
    p.ell(belly, (12, 13, 22, 25))
    leg(p, s, skin, 12, 24, 0, 5)
    p.ell(skin, (14, 2, 26, 13))
    p.ell(face, (19, 5, 27, 12))
    p.px('#3A2A30', [(26, 8)])
    p.eye(21, 5, 'angry' if s.eyes == 'open' else s.eyes, big=True)
    p.line('#22222A', [(15, 6), (17, 1), (23, 1)], width=2)
    p.ell(rgb_c, (14, 4, 18, 9), shade=False)
    if s.mouth:
        p.rect('#2A0E14', (22, 10, 26, 11), shade=False)
        p.px('#FFFFFF', [(23, 10), (25, 10)])
    p.rect(can, (24, 16, 27, 22))
    p.px('#FFFFFF', [(25, 18), (25, 19)])
    p.rect(skin, (18, 13, 23, 23))
    p.ell(skin, (18, 20, 25, 27))


# ================================================================ STREAMING
@design('bufferoo', 'Streaming', 'play', '#E53935')
def bufferoo(p, s):
    fur, pouch, snout = '#E53935', '#FFCDD2', '#FF8A80'
    if s.front:
        legs_front(p, s, lo(fur), 9, 25, 4)
        p.poly(fur, [(11, 5), (9, -1), (14, 3)])
        p.px(pouch, [(11, 2), (11, 3)])
        p.ell(fur, (9, 11, 22, 27))
        p.ell(pouch, (12, 17, 19, 25))
        spinner(p, 15, 21, s.t, '#FFFFFF', '#E57373')
        p.ell(fur, (10, 3, 21, 13))
        p.ell(snout, (13, 8, 18, 13))
        p.px('#40101A', [(15, 9)])
        p.eye(11, 6, s.eyes, side=False)
        if s.mouth:
            p.px('#40101A', [(14, 11), (15, 11), (14, 12)])
        return
    p.poly(fur, [(10, 22), (2, 26), (2, 28), (12, 26)])
    p.ell(lo(fur), (13, 24, 20, 28))
    p.ell(fur, (8, 11, 20, 26))
    p.ell(pouch, (14, 17, 20, 25))
    spinner(p, 17, 21, s.t, '#FFFFFF', '#E57373')
    dx, lift = _gait(s, 0)
    p.ell(fur, (10 + dx, 24 - lift, 20 + dx, G - lift))
    p.line(fur, [(19, 15), (22, 17)], width=2)
    p.poly(fur, [(15, 6), (13, 0), (17, 4)])
    p.poly(fur, [(17, 5), (19, -1), (20, 5)])
    p.px(pouch, [(18, 2), (18, 3)])
    p.ell(fur, (14, 4, 23, 13))
    p.ell(snout, (20, 8, 25, 12))
    p.px('#40101A', [(25, 9)])
    mouth(p, s, 22, 11, 2)
    p.eye(19, 6, s.eyes)


@design('streamlet', 'Streaming', 'play', '#E0312B', float=True)
def streamlet(p, s):
    red, white = '#E0312B', '#FFFFFF'
    wave = [0, 1, 0, -1][s.step % 4] if s.step else [0, 0, 1, 1, 0, 0, -1, -1][s.t % 8]
    if s.front:
        p.ell(red, (13, 20, 18, 27 + wave))
        p.poly(white, [(13, 9), (13, 2), (18, 5)])
        p.ell(white, (3, 14 + wave, 8, 19 + wave))
        p.ell(red, (8, 7, 23, 22))
        p.ell('#FF8A80', (11, 15, 20, 21))
        p.eye(10, 11, s.eyes, big=True, side=False)
        if s.mouth:
            p.px('#40101A', [(14, 17), (15, 17), (14, 18), (15, 18)])
        return
    p.poly(white, [(6, 19 + wave), (1, 14 + wave), (1, 24 + wave)])
    p.ell(red, (3, 16 + wave, 10, 22 + wave))
    p.ell(red, (8, 14, 18, 23))
    p.poly(white, [(12, 15), (12, 7), (18, 11)])
    p.ell(red, (15, 10, 26, 22))
    p.ell('#FF8A80', (16, 17, 25, 21))
    p.px('#FF8A80', [(9, 20), (11, 20), (13, 20)])
    p.poly(white, [(16, 18), (13, 23), (19, 21)])
    p.eye(20, 13, s.eyes, big=True)
    if s.mouth:
        p.px('#40101A', [(24, 18), (25, 18), (24, 19), (25, 19)])
    else:
        p.px('#40101A', [(24, 18), (25, 18)])


@design('bingewyrm', 'Streaming', 'play', '#9C1C28')
def bingewyrm(p, s):
    hide, belly, tv, eye = '#9C1C28', '#E9A06A', '#22223A', '#FFE14F'

    def hypno(x, y):
        if s.eyes in ('open', 'angry'):
            p.px(eye, [(x + i, y + j) for i in range(3) for j in range(3)])
            spin = [(x, y), (x + 1, y), (x + 2, y), (x + 2, y + 1), (x + 2, y + 2), (x + 1, y + 2), (x, y + 2), (x, y + 1)]
            p.px('#6A1A8A', [(x + 1, y + 1), spin[s.t % 8], spin[(s.t + 4) % 8]])
        else:
            p.eye(x, y, s.eyes, big=True, side=not s.front)

    def screens(pts):
        for i, (x, y) in enumerate(pts):
            p.rect(tv, (x, y, x + 2, y + 1), shade=False, sep=False)
            p.px('#8FE3FF' if (s.t + i) % 3 else '#FFFFFF', [(x + 1, y)])

    if s.front:
        p.ell(lo(hide), (1, 19, 30, 29))
        screens([(5, 23), (10, 25)])
        p.rect(hide, (11, 10, 20, 22))
        p.rect(belly, (13, 12, 18, 22))
        p.poly(hide, [(8, 3), (3, 0), (6, 6)])
        p.ell(hide, (6, 1, 25, 12))
        p.ell(belly, (10, 7, 21, 13))
        hypno(8, 4)
        if s.mouth:
            p.rect('#2A0E14', (12, 10, 19, 12), shade=False)
            p.px('#FFFFFF', [(12, 10), (14, 10)])
        return
    p.ell(lo(hide), (2, 18, 17, 29))
    p.poly(hide, [(3, 22), (0, 17), (1, 23)])
    p.ell(hide, (9, 17, 26, 29))
    screens([(5, 21), (12, 21), (18, 22), (8, 25)])
    p.poly(hide, [(13, 21), (16, 8), (23, 8), (22, 21)])
    p.poly(belly, [(18, 21), (20, 10), (23, 10), (22, 21)])
    p.poly(lo(hide), [(15, 5), (9, 2), (14, 9)])
    p.ell(hide, (14, 2, 27, 13))
    if s.mouth:
        p.rect(hide, (23, 5, 30, 8))
        p.rect(belly, (22, 10, 29, 12))
        p.px('#FFFFFF', [(24, 9), (26, 9), (28, 9)])
    else:
        p.rect(hide, (22, 7, 30, 11))
        p.px('#40101A', [(30, 8)])
    hypno(18, 4)


# ================================================================ FLYING
@design('zephyrlet', 'Flying', 'wind', '#9FD8FF', float=True)
def zephyrlet(p, s):
    cloud, wing = '#DDF3FF', '#9FD8FF'
    wy = wing_y(s)
    if s.front:
        p.poly(wing, [(8, 14), (2, 8 + wy), (4, 16 + wy)])
        p.ell(cloud, (5, 13, 15, 23))
        p.ell(cloud, (9, 8, 22, 22))
        p.eye(11, 14, s.eyes, side=False, iris='#1E3A5A')
        p.px('#FFB0C8', [(10, 18)])
        if s.mouth:
            p.px('#1E3A5A', [(15, 19), (15, 20)])
        p.px(wing, [(12, 25), (15, 26)] if s.t % 4 < 2 else [(13, 26), (16, 25)])
        return
    p.ell(cloud, (5, 15, 13, 23))
    p.ell(cloud, (7, 11, 17, 21))
    p.ell(cloud, (13, 8, 24, 21))
    p.ell(cloud, (15, 14, 25, 23))
    p.poly(wing, [(11, 12), (8, 5 + wy), (16, 11)])
    p.eye(19, 13, s.eyes, iris='#1E3A5A')
    p.px('#FFB0C8', [(20, 17), (21, 17)])
    mouth(p, s, 22, 18, 2, '#1E3A5A')
    p.px(wing, [(8, 25), (12, 26), (16, 25)] if s.t % 4 < 2 else [(10, 26), (14, 25), (18, 26)])


@design('airstream', 'Flying', 'wind', '#3F8FE0', float=True)
def airstream(p, s):
    blue, white, beak = '#3F8FE0', '#F2F6FA', '#FFB23F'
    wy = wing_y(s)
    if s.front:
        p.poly(lo(blue), [(13, 22), (11, 28), (15, 24)])
        p.poly(blue, [(9, 13), (1, 5 + wy), (3, 14 + wy), (8, 19)])
        p.ell(blue, (8, 7, 23, 24))
        p.ell(white, (11, 13, 20, 24))
        p.eye(10, 10, s.eyes, side=False)
        p.poly(beak, [(14, 13), (17, 13), (16, 16), (15, 16)])
        p.px('#E08A2E', [(13, 25), (14, 25)])
        return
    p.poly(lo(blue), [(12, 12), (9, 3 + wy), (17, 11)])
    p.poly(blue, [(9, 16), (1, 12), (4, 17), (1, 22), (10, 19)])
    p.ell(blue, (7, 12, 22, 21))
    p.ell(white, (13, 16, 22, 21))
    p.ell(blue, (16, 6, 25, 14))
    p.px(white, [(22, 12), (23, 12), (24, 11)])
    if s.mouth:
        p.poly(beak, [(24, 8), (29, 8), (24, 9)])
        p.poly(beak, [(24, 11), (28, 11), (24, 12)])
    else:
        p.poly(beak, [(24, 9), (30, 10), (24, 11)])
    p.eye(20, 8, s.eyes)
    p.poly(blue, [(10, 14), (6, 5 + wy), (13, 7 + wy), (19, 14)])
    p.px(white, [(8, 8 + wy), (11, 9 + wy)])
    p.px('#E08A2E', [(15, 22), (18, 22)])


@design('stratolord', 'Flying', 'wind', '#264E9C')
def stratolord(p, s):
    blue, chest, head, gold = '#264E9C', '#E8E2D0', '#F2F2F2', '#F2B81C'
    wy = wing_y(s)
    if s.front:
        p.poly(blue, [(9, 13), (0, 2 + wy), (1, 13 + wy), (0, 19 + wy), (8, 22)])
        p.px(gold, [(1, 3 + wy), (1, 13 + wy)])
        legs_front(p, s, gold, 11, 25, 2, foot=gold)
        p.ell(blue, (7, 10, 24, 27))
        p.ell(chest, (11, 13, 20, 26))
        p.ell(head, (9, 1, 22, 12))
        p.eye(10, 5, 'angry' if s.eyes == 'open' else s.eyes, side=False)
        p.poly(gold, [(14, 8), (17, 8), (16, 12), (15, 12)])
        if s.mouth:
            p.px('#40101A', [(15, 11)])
        return
    p.poly(lo(blue), [(13, 12), (11, 1 + wy), (18, 6 + wy), (21, 12)])
    p.poly(blue, [(8, 18), (0, 22), (1, 26), (9, 23)])
    leg(p, s, lo(gold), 11, 23, 1, 2, foot=lo(gold))
    p.ell(blue, (5, 11, 23, 25))
    p.ell(chest, (15, 13, 23, 24))
    leg(p, s, gold, 16, 24, 0, 2, foot=gold)
    p.poly(gold, [(18, 5), (13, 3), (16, 8), (12, 8), (18, 10)])
    p.ell(head, (17, 2, 27, 12))
    if s.mouth:
        p.poly(gold, [(25, 5), (30, 6), (30, 8), (25, 7)])
        p.poly(gold, [(25, 10), (29, 10), (25, 11)])
    else:
        p.poly(gold, [(25, 6), (30, 7), (29, 10), (25, 10)])
    p.eye(21, 5, 'angry' if s.eyes == 'open' else s.eyes)
    p.poly(blue, [(8, 14), (3, 1 + wy), (11, 5 + wy), (17, 1 + wy), (20, 14)])
    p.px(gold, [(4, 2 + wy), (17, 2 + wy)])


# ================================================================ SHOPPING
@design('cartini', 'Shopping', 'coin', '#43C463')
def cartini(p, s):
    green, wire, wheel, tag = '#43C463', '#2B8A45', '#2E2E3A', '#F2C84B'
    spoke = [(0, -1), (1, 0), (0, 1), (-1, 0)][s.t % 4]
    if s.front:
        p.line('#8A8A96', [(6, 8), (25, 8)])
        p.rect('#8A8A96', (5, 7, 7, 9))
        p.poly(green, [(6, 11), (15, 11), (15, 23), (9, 23)])
        for x in (9, 12):
            p.px(wire, [(x, y) for y in range(13, 23, 2)])
        for x, y in ((8, 25), (12, 25)):
            p.ell(wheel, (x - 1, y - 2, x + 2, y + 2))
        p.eye(10, 14, s.eyes, big=True, side=False)
        if s.mouth:
            p.px('#10301A', [(14, 20), (15, 20), (14, 21)])
        return
    p.line('#8A8A96', [(7, 12), (4, 7)], width=1)
    p.rect('#1E1E26', (1, 6, 5, 7), shade=False)
    p.poly(green, [(6, 11), (26, 11), (23, 23), (9, 23)])
    for x in range(9, 25, 3):
        p.px(wire, [(x, y) for y in range(13, 23) if x < 23 or y < 20])
    p.poly(tag, [(24, 11), (28, 7), (31, 10), (27, 14)])
    p.px('#8A6A1A', [(28, 9), (27, 11)])
    for cx in (12, 21):
        p.ell(wheel, (cx - 2, 24, cx + 2, 28))
        p.px('#8A8A96', [(cx + spoke[0], 26 + spoke[1])])
    p.eye(15, 14, s.eyes, big=True)
    p.eye(19, 14, s.eyes, big=True)
    mouth(p, s, 17, 19, 3, '#10301A')


# ================================================================ LEGENDARY
@design('aegis', 'Legendary', 'nova', '#F2C84B', float=True)
def aegis(p, s):
    gold, white, cyan = '#F2C84B', '#FFF6D8', '#5CE1FF'
    wy = wing_y(s)
    orbit = [(4, 16), (8, 24), (16, 27), (24, 24), (27, 16), (24, 8), (16, 5), (8, 8)]

    def orbs():
        for k in (0, 4):
            x, y = orbit[(s.t + k) % 8]
            p.px(cyan, [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)])

    if s.front:
        p.poly('#FFF0B0', [(8, 12), (1, 6 + wy), (3, 14 + wy), (7, 18)], shade=False)
        p.line(gold, [(11, 1), (15, 0)])
        p.px(gold, [(10, 2), (10, 3)])
        p.poly(gold, [(8, 7), (16, 7), (16, 27), (10, 20), (7, 13)])
        p.px(white, [(15, y) for y in range(10, 23)] + [(12, 13), (13, 13), (14, 13)])
        aegis_eye(p, s, 14, 15, cyan)
        orbs()
        return
    p.poly('#FFF0B0', [(12, 12), (3, 5 + wy), (5, 13 + wy), (10, 18)], shade=False)
    p.line(gold, [(12, 2), (16, 1), (21, 2)])
    p.px(gold, [(11, 3), (22, 3)])
    p.poly(gold, [(11, 7), (23, 7), (25, 14), (18, 27), (11, 17)])
    p.poly(white, [(21, 8), (23, 8), (24, 14), (19, 24)])
    p.px(white, [(16, y) for y in range(10, 23)] + [(13, 13), (14, 13), (15, 13), (17, 13), (18, 13), (19, 13)])
    aegis_eye(p, s, 20, 10, cyan)
    orbs()


def aegis_eye(p, s, x, y, cyan):
    if s.eyes == 'closed':
        p.px('#8A6A1A', [(x, y + 1), (x + 1, y + 1)])
    elif s.eyes == 'hurt':
        p.px('#FF5A5A', [(x, y), (x + 1, y + 1), (x, y + 1), (x + 1, y)])
    elif s.eyes == 'happy':
        p.px(cyan, [(x - 1, y + 1), (x, y), (x + 1, y), (x + 2, y + 1)])
    else:
        p.px(cyan, [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)])
        p.px('#FFFFFF', [(x, y)])


@design('titan', 'Legendary', 'claw', '#B07A3A')
def titan(p, s):
    fur, mane, snout, claw = '#B07A3A', '#5A2E1A', '#E0B070', '#F4F0E8'

    def spikes(pts):
        p.poly(mane, pts)

    if s.front:
        legs_front(p, s, lo(fur), 8, 21, 3, foot=claw)
        p.ell(fur, (7, 13, 24, 26))
        spikes([(5, 12), (1, 8), (6, 7), (3, 2), (9, 4), (10, 0), (15, 3)])
        p.ell(mane, (4, 2, 27, 21))
        p.ell(fur, (9, 5, 22, 18))
        p.ell(snout, (12, 11, 19, 18))
        p.px('#2A1208', [(15, 12)])
        p.eye(10, 8, 'angry' if s.eyes == 'open' else s.eyes, side=False, iris='#2A1208')
        if s.mouth:
            p.rect('#2A0E14', (13, 15, 18, 17), shade=False)
            p.px(claw, [(13, 15), (13, 16)])
        else:
            p.px(claw, [(13, 17)])
        return
    p.line(fur, [(5, 16), (2, 10)], width=2)
    p.ell(mane, (0, 7, 4, 11))
    leg(p, s, lo(fur), 7, 21, 1, 3, foot=claw)
    leg(p, s, lo(fur), 18, 21, 0, 3, foot=claw)
    p.ell(fur, (3, 12, 23, 24))
    leg(p, s, fur, 9, 22, 0, 3, foot=claw)
    leg(p, s, fur, 20, 22, 1, 3, foot=claw)
    spikes([(14, 14), (11, 7), (15, 7), (13, 2), (18, 5), (20, 0), (22, 4), (26, 19)])
    p.ell(mane, (13, 3, 26, 20))
    p.ell(fur, (18, 5, 27, 15))
    if s.mouth:
        p.rect(snout, (24, 8, 30, 10))
        p.rect(snout, (24, 13, 29, 15))
        p.px('#2A0E14', [(25, 11), (26, 11), (27, 11), (25, 12), (26, 12)])
        p.px(claw, [(28, 11), (27, 12)])
    else:
        p.rect(snout, (24, 9, 30, 14))
        p.px('#2A1208', [(30, 9)])
        p.px(claw, [(28, 15)])
    p.eye(22, 7, 'angry' if s.eyes == 'open' else s.eyes, iris='#2A1208')


# ================================================================ PEOPLE
@design('player', 'Hero', 'nova', '#2E6FD8')
def player(p, s):
    skin, hair, tunic, pants, cape, boot, wood = '#F1C7A0', '#5A3A22', '#2E6FD8', '#3A3A4A', '#C62828', '#4A2E1A', '#9A6A3A'
    if s.front:
        p.poly(cape, [(9, 11), (6, 26), (15, 25)])
        legs_front(p, s, pants, 12, 22, 3, foot=boot)
        p.rect(skin, (7, 12, 9, 20))
        p.rect(tunic, (10, 11, 21, 22))
        p.px('#F2C84B', [(15, 13), (15, 14), (15, 15), (14, 14), (16, 14)])
        p.rect('#4A2E1A', (10, 19, 21, 19), shade=False, sep=False)
        p.ell(skin, (10, 2, 21, 12))
        p.poly(hair, [(10, 7), (10, 2), (15, 0), (16, 0), (16, 4), (12, 4)])
        p.rect(cape, (10, 4, 15, 4), shade=False, sep=False)
        p.eye(12, 7, s.eyes, side=False, iris='#2A1A10')
        if s.mouth:
            p.px('#8A2E2E', [(15, 10), (15, 11)])
        return
    p.poly(cape, [(12, 11), (7, 26), (15, 25)])
    leg(p, s, lo(pants), 13, 22, 1, 3, foot=lo(boot))
    p.rect(lo(skin), (14, 13, 16, 19))
    p.rect(tunic, (11, 11, 19, 22))
    p.rect('#4A2E1A', (11, 19, 19, 19), shade=False, sep=False)
    p.px('#F2C84B', [(17, 13), (17, 14), (17, 15), (16, 14), (18, 14)])
    leg(p, s, pants, 16, 22, 0, 3, foot=boot)
    p.ell(skin, (12, 2, 21, 11))
    p.poly(hair, [(12, 7), (12, 2), (15, 0), (21, 1), (21, 3), (16, 4), (15, 8)])
    p.rect(cape, (15, 4, 20, 4), shade=False, sep=False)
    p.px(cape, [(12, 4), (11, 5), (11, 6)])
    p.eye(18, 6, s.eyes, iris='#2A1A10')
    if s.mouth:
        p.px('#8A2E2E', [(20, 9), (20, 10)])
    p.line(wood, [(22, 3), (22, 28)])
    p.ell('#5CE1FF' if s.t % 4 < 2 else '#B8FBFF', (21, 1, 23, 3), shade=False)
    p.rect(skin, (19, 13, 21, 18))
    p.px(skin, [(22, 16), (22, 17)])


@design('poacher', 'Villain', 'net', '#4A4E3A')
def poacher(p, s):
    coat, hat, goggle, skin, scarf, gun = '#4A4E3A', '#2B2B2B', '#FF5252', '#C99A7A', '#7A2E2E', '#3A3A44'
    if s.front:
        legs_front(p, s, '#2E2E2E', 12, 23, 3, foot='#1A1A1A')
        p.rect(coat, (7, 12, 9, 21))
        p.poly(coat, [(10, 11), (16, 11), (16, 26), (8, 26)])
        p.px(lo(coat), [(15, y) for y in range(12, 26)])
        p.ell(skin, (10, 3, 21, 12))
        p.rect(scarf, (10, 9, 21, 12))
        p.rect(hat, (6, 4, 25, 5), shade=False)
        p.rect(hat, (10, 0, 21, 4))
        p.rect('#1A1A1A', (11, 6, 20, 7), shade=False, sep=False)
        goggles(p, s, 12, 6, goggle)
        return
    leg(p, s, '#1E1E1E', 12, 23, 1, 3)
    p.poly(coat, [(10, 11), (20, 11), (22, 26), (9, 26)])
    p.px(lo(coat), [(18, y) for y in range(12, 26)])
    leg(p, s, '#2E2E2E', 15, 24, 0, 3, foot='#1A1A1A')
    p.ell(skin, (12, 3, 21, 12))
    p.rect(scarf, (12, 9, 22, 12))
    p.rect(hat, (9, 4, 25, 5), shade=False)
    p.rect(hat, (12, 0, 21, 4))
    goggles(p, s, 18, 6, goggle)
    p.rect(gun, (16, 15, 27, 18))
    p.rect('#1A1A1A', (27, 16, 29, 17), shade=False)
    p.px('#9A9AA6', [(19, 15), (20, 15), (21, 15)])
    p.rect(coat, (15, 13, 18, 19))
    p.rect(skin, (18, 17, 19, 19))


def goggles(p, s, x, y, col):
    lens = {'closed': '#5A1A1A', 'hurt': '#FFFFFF'}.get(s.eyes, col)
    p.px(lens, [(x, y), (x + 1, y), (x + 2, y), (x, y + 1), (x + 1, y + 1), (x + 2, y + 1)])
    p.px('#FFFFFF', [(x, y)])


# ================================================================ OBJECTS
@design('laser', 'Object', 'laser', '#FF3B3B', float=True)
def laser(p, s):
    grey, red, fin = '#9AA4B2', '#FF3B3B', '#5E6F82'
    blade = s.t % 2
    if s.front:
        p.line('#3A3A44', [(15, 8), (15, 5)])
        p.line('#C9D6E3', [(8 + blade * 2, 4), (15, 4)])
        p.poly(fin, [(8, 18), (4, 24), (10, 21)])
        p.ell(grey, (7, 8, 24, 23))
        p.ell('#2A2A34', (11, 11, 20, 20))
        lens(p, s, 13, 13, red, 6)
        return
    p.line('#3A3A44', [(15, 9), (15, 6)])
    p.line('#C9D6E3', [(9 + blade * 3, 5), (21 - blade * 3, 5)])
    p.poly(fin, [(9, 17), (4, 22), (12, 20)])
    p.ell(grey, (7, 9, 23, 23))
    p.rect(fin, (7, 15, 12, 16), shade=False)
    p.ell('#2A2A34', (17, 11, 25, 20))
    lens(p, s, 19, 13, red, 5)


def lens(p, s, x, y, col, n):
    if s.eyes == 'closed':
        p.px('#5A1A1A', [(x + i, y + n // 2) for i in range(n)])
        return
    c = '#FFFFFF' if s.mouth else ('#FFE14F' if s.eyes == 'hurt' else col)
    p.ell(c, (x, y, x + n - 1, y + n - 1), shade=False, sep=False)
    p.px('#FFFFFF', [(x + 1, y + 1)])


@design('bite', 'Object', 'claw', '#7A2E2E')
def bite(p, s):
    gum, tooth, tongue = '#B8404A', '#F4F0E8', '#E8627A'
    gap = 4 if s.mouth else (1 if s.eyes == 'closed' else 2)
    if s.front:
        p.ell(gum, (6, 5, 25, 15 - gap // 2))
        p.ell(gum, (6, 15 + gap // 2, 25, 26))
        p.rect('#2A0E14', (9, 14 - gap // 2, 22, 16 + gap // 2), shade=False, sep=False)
        p.px(tongue, [(14, 16 + gap // 2), (15, 16 + gap // 2)])
        for x in (9, 12, 15):
            p.poly(tooth, [(x, 13 - gap // 2), (x + 2, 13 - gap // 2), (x + 1, 16 - gap // 2)])
            p.poly(tooth, [(x, 17 + gap // 2), (x + 2, 17 + gap // 2), (x + 1, 14 + gap // 2)])
        p.eye(9, 7, s.eyes, side=False)
        return
    top = 14 - gap
    p.poly(gum, [(5, 6), (20, 4), (29, top), (6, top + 2)])
    p.poly(gum, [(6, 16 + gap), (28, 16 + gap), (22, 27), (7, 26)])
    p.rect('#2A0E14', (8, top + 1, 26, 16 + gap - 1), shade=False, sep=False)
    p.ell(tongue, (9, 14 + gap, 18, 17 + gap), shade=False, sep=False)
    for x in (10, 14, 18, 22):
        p.poly(tooth, [(x, top + 1), (x + 2, top + 1), (x + 1, top + 4)])
        p.poly(tooth, [(x - 1, 15 + gap), (x + 1, 15 + gap), (x, 12 + gap)])
    p.eye(17, 6, s.eyes)


@design('net', 'Object', 'net', '#C9A86A', float=True, outline=False)
def net(p, s):
    rope, weight = '#C9A86A', '#5E6F82'
    sway = [0, 1, 0, -1][s.t % 4] if not s.mouth else 0
    spread = 2 if s.mouth else 0
    if True:  # a net looks the same from the side and the front
        L, R, T, B = 5 - spread, 26 + spread, 4 - spread, 25 + spread
        m, p.mirror = p.mirror, False
        for i in range(5):
            x = L + (R - L) * i // 4
            p.line(lo(rope), [(x + 1, T + 1), (x + sway + 1, B + 1)], sep=False)
            p.line(rope, [(x, T), (x + sway, B)], sep=False)
            y = T + (B - T) * i // 4
            p.line(lo(rope), [(L + 1, y + 1), (R + 1, y + sway + 1)], sep=False)
            p.line(rope, [(L, y), (R, y + sway)], sep=False)
        knots = [(L + (R - L) * i // 4, T + (B - T) * j // 4) for i in range(5) for j in range(5)]
        p.px(lo(rope), knots)
        for x, y in ((L, T), (R, T), (L + sway, B), (R + sway, B)):
            p.ell(weight, (x - 1, y - 1, x + 1, y + 1), shade=False)
        p.mirror = m
        if s.eyes in ('hurt', 'happy', 'angry'):
            p.eye(13, 13, s.eyes, side=False)
