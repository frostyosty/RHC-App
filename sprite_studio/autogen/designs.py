"""Hand-authored pixel designs for every sprite row in the Studio matrix.

Each design is `fn(p, s)` drawing onto a mirror-aware Painter for one Pose.
Side views face right. Front views are drawn as the left half only (the
Painter mirrors it), except where asymmetry is wanted (walking legs, mics).

The 3D Wilds show creatures from 8 directions using 5 drawn views (the
other 3 are mirrors): front, fq (3/4 front, facing right toward the
camera), side, bq (3/4 back, facing right away from it) and back. Back is
symmetric like front (left half only). A design that draws the extra views
lists them in `views=` so autogen emits their GIFs.
Rows are drawn on the 64 grid (`size=64`) in the cacheon style: a dark
per-line `Kit` palette with one glowing accent, feet on `p.ground`.
Frames are padded (see autogen.draw), so attack lunges never clip.
"""
import math
from dataclasses import dataclass, replace

from pixelkit import lo


VIEWS = ('front', 'fq', 'side', 'bq', 'back')
ALL_VIEWS = VIEWS


@dataclass(frozen=True)
class Pose:
    view: str = 'side'   # one of VIEWS
    step: int = 0        # walk phase 0..3 (1 and 3 are mid-stride)
    flap: int = 0        # wings: -1 up, 0 level, 1 down
    eyes: str = 'open'   # open | closed | hurt | happy | angry
    mouth: bool = False
    t: int = 0           # frame counter for glows, spinners, blinks

    def but(self, **kw):
        return replace(self, **kw)

    @property
    def front(self):
        return self.view == 'front'

    @property
    def symmetric(self):
        """Front and back are drawn as a left half and mirrored."""
        return self.view in ('front', 'back')


@dataclass
class Design:
    name: str
    fn: object
    category: str
    fx: str
    color: str
    float: bool = False
    outline: bool = True
    views: tuple = ('front', 'side')
    size: int = 32       # logical grid; GIFs are written at 2x


DESIGNS = {}


def design(name, category, fx, color, float=False, outline=True, views=('front', 'side'), size=32):
    def wrap(fn):
        DESIGNS[name] = Design(name, fn, category, fx, color, float, outline, views, size)
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


def legs_front(p, s, col, x, top, w=2, foot=None):
    m, p.mirror = p.mirror, False
    g = p.ground
    for side, xx in ((0, x), (1, p.size - 1 - x - w + 1)):
        lift = 1 if (s.step == 1 and side == 0) or (s.step == 3 and side == 1) else 0
        p.rect(col, (xx, top - lift, xx + w - 1, g - lift), shade=False)
        if foot:
            fx = xx - 1 if side == 0 else xx
            p.rect(foot, (fx, g - lift, fx + w, g - lift), shade=False, sep=False)
    p.mirror = m


def wing_y(s):
    return {-1: -3, 0: 0, 1: 2}[s.flap]


# ================================================================ TECH
class Kit:
    """Shared dark palette for the 64-grid restyle (see cacheon). Each beast
    subclasses it and sets its glowing `accent` (and `hot`, the flicker)."""
    steel, plate, joint, dark = '#4A5263', '#646E82', '#2A303C', '#15181F'
    jaw, teeth, red = '#0B0D12', '#D8DEE6', '#FF4D5E'
    accent, flare = '#2ED3EA', '#A8F6FF'

    def __init__(self, s):
        self.hot = self.flare if s.t % 4 < 2 else self.accent
        self.eye = None if s.eyes == 'closed' else (self.red if s.eyes in ('hurt', 'angry') else self.accent)


def sq(x, y, w=2, h=2):
    """Pixel block with top-left (x, y), for eyes, vents and teeth."""
    return [(x + i, y + j) for i in range(w) for j in range(h)]


def fangs(p, k, xs, y, down=True, h=2):
    """A row of 2px-wide teeth at xs, hanging from y (or rising to it)."""
    p.px(k.teeth, [pt for x in xs for pt in sq(x, y if down else y - h + 1, 2, h)])


def by_view(fns):
    """Dispatch a design to one function per view, each fn(p, s, kit)."""
    def draw(p, s):
        fns[s.view](p, s, fns['kit'](s))
    return draw


# ================================================================ TECH
class BytletKit(Kit):
    accent, flare = '#2ED3EA', '#A8F6FF'


def bytelet_head_side(p, s, k, x=0, y=0):
    """Rat skull: long wedge snout, blade ear, antenna, slit visor."""
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.line(k.joint, t([(47, 34), (50, 22)]), width=2)
    p.px(k.hot, t(sq(49, 20, 3, 3)))
    if s.mouth:
        p.poly(k.jaw, t([(49, 45), (60, 44), (58, 50), (49, 49)]), shade=False)
        fangs(p, k, [a + x for a in (52, 55)], 48 + y, down=False)
    p.poly(k.steel, t([(40, 40), (44, 33), (52, 33), (62, 40), (61, 44), (44, 46)]))
    p.poly(k.plate, t([(50, 35), (62, 40), (58, 42), (49, 39)]))
    p.poly(k.plate, t([(43, 36), (39, 24), (49, 33)]))
    p.px(k.accent, t(sq(40, 27)))
    p.rect(k.dark, (44 + x, 37 + y, 52 + x, 39 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(47, 37, 3, 2) + sq(50, 38, 3, 2)))
    p.line(k.jaw, t([(51, 45), (61, 44)]))
    fangs(p, k, [a + x for a in (53, 57)], 46 + y)
    p.px(k.jaw, t(sq(61, 40)))


def _bytelet_side(p, s, k):
    p.line(k.dark, [(15, 46), (8, 48), (4, 54), (6, 58)], width=2)
    p.poly(k.plate, [(3, 57), (9, 57), (8, 61), (4, 61)])
    p.px(k.accent, sq(5, 58))
    dleg(p, s, lo(k.joint), 20, 48, 1, hind=True)
    dleg(p, s, lo(k.joint), 38, 50, 0)
    p.poly(k.steel, [(12, 49), (16, 39), (26, 33), (37, 33), (45, 39), (45, 48), (37, 53), (18, 53)])
    for x0, y0 in ((17, 40), (25, 35), (33, 35)):
        p.poly(k.plate, [(x0, y0 + 2), (x0 + 3, y0 - 1), (x0 + 9, y0), (x0 + 9, y0 + 5), (x0 + 1, y0 + 6)])
    for x0, y0 in ((20, 37), (28, 32), (36, 32)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 1, y0 - 4), (x0 + 3, y0)], shade=False)
    p.px(k.accent, sq(24, 46, 2, 2) + sq(28, 46, 2, 2) + sq(32, 46, 2, 2))
    p.px(k.hot, sq(24 + 4 * (s.t % 3), 46, 2, 1))
    dleg(p, s, k.joint, 23, 49, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 41, 51, 1, paw=k.dark)
    bytelet_head_side(p, s, k)


def _bytelet_front(p, s, k):
    legs_front(p, s, lo(k.joint), 19, 54, 3)
    p.poly(k.steel, [(16, 46), (21, 37), (32, 36), (32, 56), (20, 55), (16, 51)])
    p.poly(k.plate, [(17, 45), (21, 38), (25, 38), (22, 47)])
    legs_front(p, s, k.joint, 23, 52, 3, foot=k.dark)
    m, p.mirror = p.mirror, False
    p.line(k.joint, [(28, 34), (25, 21)], width=2)
    p.px(k.hot, sq(24, 19, 3, 3))
    p.mirror = m
    p.poly(k.plate, [(24, 33), (17, 22), (27, 30)])
    p.px(k.accent, sq(19, 25))
    p.poly(k.steel, [(21, 38), (24, 31), (32, 30), (32, 50), (27, 49), (23, 44)])
    p.rect(k.dark, (23, 35, 33, 38), shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 35, 2, 2) + sq(27, 36, 2, 2) + sq(29, 37, 2, 2))
    p.poly(k.plate, [(28, 40), (32, 39), (32, 51), (29, 49)])
    p.px(k.jaw, sq(31, 41, 1, 2))
    if s.mouth:
        p.poly(k.jaw, [(27, 48), (32, 48), (32, 55), (28, 54)], shade=False)
        fangs(p, k, [29], 48)
        fangs(p, k, [30], 54, down=False)
    else:
        p.line(k.jaw, [(27, 49), (32, 49)])
        fangs(p, k, [29], 50)


def _bytelet_fq(p, s, k):
    p.line(k.dark, [(15, 46), (9, 49), (6, 55), (8, 58)], width=2)
    p.poly(k.plate, [(5, 57), (11, 57), (10, 61), (6, 61)])
    p.px(k.accent, sq(7, 58))
    dleg(p, s, lo(k.joint), 21, 49, 1, hind=True)
    dleg(p, s, lo(k.joint), 40, 51, 0)
    p.poly(k.steel, [(14, 49), (18, 39), (28, 34), (38, 35), (44, 41), (43, 50), (35, 55), (20, 54)])
    for x0, y0 in ((19, 41), (27, 36)):
        p.poly(k.plate, [(x0, y0 + 2), (x0 + 3, y0 - 1), (x0 + 9, y0), (x0 + 9, y0 + 5), (x0 + 1, y0 + 6)])
    for x0, y0 in ((22, 38), (30, 33)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 1, y0 - 4), (x0 + 3, y0)], shade=False)
    p.px(k.accent, sq(24, 47) + sq(28, 47) + sq(32, 47))
    p.px(k.hot, sq(24 + 4 * (s.t % 3), 47, 2, 1))
    dleg(p, s, k.joint, 25, 50, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 36, 53, 1, paw=k.dark)
    # head turned toward us: both ears, both eye slits meeting in a V
    p.line(k.joint, [(49, 35), (52, 23)], width=2)
    p.px(k.hot, sq(51, 21, 3, 3))
    p.poly(lo(k.plate), [(53, 36), (57, 25), (58, 36)])
    p.poly(k.steel, [(40, 42), (43, 35), (53, 34), (58, 39), (57, 45), (45, 48)])
    p.poly(k.plate, [(43, 37), (37, 26), (48, 34)])
    p.px(k.accent, sq(39, 29))
    p.rect(k.dark, (43, 38, 57, 41), shade=False)
    if k.eye:
        p.px(k.eye, sq(44, 38) + sq(46, 39) + sq(48, 40, 1, 2) + sq(51, 40, 1, 2) + sq(52, 39) + sq(54, 38))
    p.poly(k.plate, [(47, 42), (56, 42), (61, 45), (57, 49), (49, 49)])
    p.px(k.jaw, sq(59, 45))
    if s.mouth:
        p.poly(k.jaw, [(48, 49), (58, 49), (56, 55), (49, 54)], shade=False)
        fangs(p, k, [50, 55], 49)
        fangs(p, k, [52], 54, down=False)
    else:
        p.line(k.jaw, [(49, 49), (58, 49)])
        fangs(p, k, [51, 55], 50)


def _bytelet_bq(p, s, k):
    dleg(p, s, lo(k.joint), 40, 50, 0)
    dleg(p, s, lo(k.joint), 34, 51, 1)
    p.line(k.joint, [(48, 34), (51, 22)], width=2)
    p.px(k.hot, sq(50, 20, 3, 3))
    p.poly(lo(k.plate), [(51, 36), (54, 25), (56, 34)])
    p.poly(k.steel, [(39, 41), (42, 34), (52, 34), (59, 40), (56, 45), (44, 46)])
    p.poly(k.plate, [(42, 36), (37, 24), (47, 33)])
    p.px(k.accent, sq(38, 27))
    p.px(lo(k.steel), [(44, y) for y in range(36, 44)] + [(45, y) for y in range(36, 44)])
    p.rect(k.dark, (53, 38, 58, 40), shade=False)
    if k.eye:
        p.px(k.eye, sq(55, 38, 3, 2))
    p.poly(k.steel, [(9, 49), (13, 39), (24, 34), (36, 35), (42, 41), (40, 49), (31, 55), (14, 56)])
    for x0, y0 in ((13, 40), (21, 36), (29, 36)):
        p.poly(k.plate, [(x0, y0 + 2), (x0 + 3, y0 - 1), (x0 + 9, y0), (x0 + 9, y0 + 5), (x0 + 1, y0 + 6)])
    for x0, y0 in ((16, 37), (24, 33), (32, 33)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 1, y0 - 4), (x0 + 3, y0)], shade=False)
    p.px(k.accent, sq(15, 47) + sq(19, 48) + sq(23, 49))
    p.px(k.hot, sq(15 + 4 * (s.t % 3), 47 + s.t % 3, 2, 1))
    dleg(p, s, k.joint, 16, 52, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 28, 53, 1, hind=True, paw=k.dark)
    p.line(k.dark, [(11, 47), (6, 50), (3, 55), (5, 58)], width=2)
    p.poly(k.plate, [(2, 57), (8, 57), (7, 61), (3, 61)])
    p.px(k.accent, sq(4, 58))


def _bytelet_back(p, s, k):
    p.poly(k.plate, [(24, 33), (18, 22), (27, 30)])
    p.px(k.accent, sq(20, 25))
    p.poly(k.steel, [(22, 36), (25, 30), (32, 30), (32, 42), (24, 40)])
    p.px(lo(k.steel), sq(27, 32, 2, 8))
    p.poly(k.steel, [(15, 48), (20, 38), (32, 36), (32, 57), (19, 56), (15, 52)])
    p.poly(k.plate, [(20, 40), (32, 38), (32, 45), (22, 45)])
    for y0 in (38, 44):
        p.poly(k.dark, [(28, y0), (32, y0 - 6), (32, y0)], shade=False)
    p.px(k.accent, sq(19, 49, 3, 2))
    p.px(k.hot, [(19 + s.t % 3, 49)])
    legs_front(p, s, k.joint, 18, 54, 4, foot=k.dark)
    m, p.mirror = p.mirror, False
    p.line(k.joint, [(36, 32), (39, 20)], width=2)
    p.px(k.hot, sq(38, 18, 3, 3))
    p.line(k.dark, [(32, 50), (36, 54), (35, 58)], width=2)
    p.poly(k.plate, [(32, 57), (38, 57), (37, 61), (33, 61)])
    p.mirror = m


bytelet = design('bytelet', 'Tech', 'bits', '#35E0F0', views=ALL_VIEWS, size=64)(by_view({
    'kit': BytletKit, 'side': _bytelet_side, 'front': _bytelet_front, 'fq': _bytelet_fq,
    'bq': _bytelet_bq, 'back': _bytelet_back}))


@design('cacheon', 'Tech', 'bits', '#35E0F0', views=ALL_VIEWS, size=64)
def cacheon(p, s):
    """Armoured robo-hound: wedge snout, slit visor, blade ears, cable tail."""
    {'side': _cacheon_side, 'front': _cacheon_front, 'fq': _cacheon_fq,
     'bq': _cacheon_bq, 'back': _cacheon_back}[s.view](p, s, CacheonKit(s))


class CacheonKit:
    steel, plate, joint, dark = '#4A5263', '#646E82', '#2A303C', '#15181F'
    cyan, jaw, teeth = '#2ED3EA', '#0B0D12', '#D8DEE6'

    def __init__(self, s):
        self.hot = '#A8F6FF' if s.t % 4 < 2 else self.cyan
        self.eye = None if s.eyes == 'closed' else ('#FF4D5E' if s.eyes in ('hurt', 'angry') else self.cyan)


def dleg(p, s, col, x, y, grp, hind=False, paw=None):
    """Jointed leg from hip/shoulder (x, y) to the ground. Hind legs have a
    muscled thigh and bend backwards at the hock; front legs are near straight.
    Sized for the 64 grid."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    fx = x + dx
    if hind:
        jx, jy = x - 5 + dx // 2, y + (g - y) * 3 // 5
        p.poly(col, [(x - 4, y - 4), (x + 4, y - 3), (jx + 2, jy + 1), (jx - 1, jy)])
    else:
        jx, jy = x + dx // 2, y + (g - y) // 2
        p.line(col, [(x, y - 3), (jx, jy)], width=4)
        p.px(lo(col), [(jx - 1, jy), (jx, jy), (jx + 1, jy)])
    p.line(col, [(jx, jy), (fx, g - 1)], width=3)
    p.rect(paw or col, (fx - 2, g - 1, fx + 2, g), shade=False)
    p.px('#AEB8C6', [(fx + 3, g), (fx + 4, g)])


def cacheon_head_side(p, s, k, x=0, y=0):
    """Skull, muzzle, slit visor and jaw; (x, y) shifts it for other views."""
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(lo(k.plate), t([(48, 16), (47, 5), (53, 15)]))
    if s.mouth:
        p.poly(k.jaw, t([(51, 27), (61, 27), (59, 32), (51, 31)]), shade=False)
        p.px(k.teeth, t([(53, 30), (53, 31), (54, 30), (54, 31), (56, 30), (56, 31), (57, 30), (57, 31), (58, 29), (58, 30), (59, 29), (59, 30)]))
    p.poly(k.steel, t([(43, 20), (47, 13), (55, 15), (57, 20), (52, 27), (44, 27)]))
    p.poly(k.plate, t([(52, 19), (61, 21), (61, 25), (53, 27)]))
    p.poly(k.plate, t([(45, 16), (39, 4), (51, 15)]))
    p.px(k.cyan, t([(42, 8), (42, 9), (43, 8), (43, 9)]))
    p.rect(k.dark, (45 + x, 17 + y, 54 + x, 19 + y), shade=False)
    if k.eye:
        p.px(k.eye, t([(49, 17), (49, 18), (50, 17), (50, 18), (50, 19), (51, 18), (51, 19), (52, 18), (52, 19), (53, 18), (53, 19), (54, 18), (54, 19)]))
    p.line(k.jaw, t([(53, 27), (60, 27)]))
    p.px(k.teeth, t([(54, 28), (54, 29), (55, 28), (55, 29), (57, 28), (57, 29), (58, 28), (58, 29)]))
    p.px(k.jaw, t([(61, 21), (61, 22), (62, 21), (62, 22)]))


def _cacheon_side(p, s, k):
    p.line(k.dark, [(16, 33), (11, 28), (8, 20)], width=3)
    p.poly(k.plate, [(8, 21), (5, 11), (12, 17)])
    p.px(k.cyan, [(6, 14), (6, 15), (7, 14), (7, 15), (8, 17), (8, 18), (9, 17), (9, 18)])
    dleg(p, s, lo(k.joint), 20, 39, 1, hind=True)
    dleg(p, s, lo(k.joint), 40, 39, 0)
    p.poly(k.steel, [(13, 35), (19, 28), (40, 25), (47, 31), (45, 41), (37, 45), (21, 45), (15, 41)])
    p.poly(k.plate, [(20, 28), (39, 25), (41, 31), (21, 33)])
    for x0 in (23, 29, 36):
        p.poly(k.dark, [(x0, 28), (x0 + 2, 22), (x0 + 4, 27)], shade=False)
    p.px(k.cyan, [(28, 34), (28, 35), (29, 34), (29, 35), (30, 34), (30, 35), (32, 34), (32, 35), (33, 34), (33, 35), (34, 34), (34, 35), (36, 34), (36, 35), (37, 34), (37, 35), (38, 34), (38, 35)])
    p.px(k.hot, [(32 + 4 * (s.t % 2), 35), (33 + 4 * (s.t % 2), 35)])
    p.poly(k.joint, [(39, 29), (44, 20), (51, 23), (47, 35)])
    dleg(p, s, k.joint, 24, 40, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 43, 40, 1, paw=k.dark)
    cacheon_head_side(p, s, k)


def _cacheon_front(p, s, k):
    # left half only (mirrored); the eye slits meet in a V
    legs_front(p, s, lo(k.joint), 17, 48, 3)
    p.poly(k.joint, [(24, 21), (32, 21), (32, 35), (23, 32)])
    p.poly(k.steel, [(17, 35), (23, 27), (32, 27), (32, 51), (24, 49), (17, 44)])
    p.poly(k.plate, [(17, 35), (23, 28), (25, 31), (20, 39)])
    p.px(k.hot, [(30, 40), (30, 41), (31, 40), (31, 41), (30, 42), (31, 42)])
    p.px(k.cyan, [(29, 41), (29, 42), (30, 41), (30, 42)])
    legs_front(p, s, k.joint, 23, 44, 3, foot=k.dark)
    p.poly(k.plate, [(23, 15), (16, 1), (27, 11)])
    p.px(k.cyan, [(18, 5), (18, 6), (19, 5), (19, 6)])
    p.poly(k.steel, [(20, 16), (25, 9), (32, 9), (32, 28), (27, 28), (21, 23)])
    p.rect(k.dark, (22, 14, 33, 18), shade=False)
    if k.eye:
        p.px(k.eye, [(24, 14), (24, 15), (25, 14), (25, 15), (26, 14), (26, 15), (26, 16), (26, 17), (27, 16), (27, 17), (28, 16), (28, 17), (29, 16), (29, 17), (29, 18), (30, 17), (30, 18)])
    p.poly(k.plate, [(27, 20), (32, 19), (32, 35), (28, 33)])
    p.px(k.jaw, [(30, 20), (30, 21), (31, 20), (31, 21), (30, 22), (31, 22)])
    if s.mouth:
        p.poly(k.jaw, [(28, 29), (32, 29), (32, 39), (29, 37)], shade=False)
        p.px(k.teeth, [(29, 29), (29, 30), (30, 29), (30, 30), (30, 31), (31, 30), (31, 31), (30, 37), (30, 38), (31, 37), (31, 38)])
    else:
        p.px(k.jaw, [(28, 32), (28, 33), (29, 32), (29, 33), (30, 32), (30, 33), (31, 32), (31, 33)])
        p.px(k.teeth, [(29, 33), (29, 34), (30, 33), (30, 34)])


def _cacheon_fq(p, s, k):
    # 3/4 front: body angled away to the left, head turned toward us
    p.line(k.dark, [(16, 33), (11, 27), (9, 19)], width=3)
    p.poly(k.plate, [(9, 20), (7, 9), (13, 16)])
    p.px(k.cyan, [(8, 13), (8, 14), (9, 13), (9, 14)])
    dleg(p, s, lo(k.joint), 21, 39, 1, hind=True)
    dleg(p, s, lo(k.joint), 43, 41, 0)
    p.poly(k.steel, [(16, 35), (21, 28), (37, 27), (45, 32), (44, 44), (35, 48), (23, 47), (17, 41)])
    p.poly(k.plate, [(21, 28), (36, 27), (39, 32), (23, 33)])
    for x0 in (31, 39):
        p.poly(k.dark, [(x0, 28), (x0 + 2, 22), (x0 + 4, 27)], shade=False)
    p.px(k.cyan, [(26, 36), (26, 37), (27, 36), (27, 37), (28, 36), (28, 37), (29, 36), (29, 37), (30, 36), (30, 37), (31, 36), (31, 37), (32, 36), (32, 37), (33, 36), (33, 37)])
    p.px(k.hot, [(30 + 3 * (s.t % 2), 36), (31 + 3 * (s.t % 2), 36)])
    dleg(p, s, k.joint, 25, 41, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 37, 44, 1, paw=k.dark)
    p.poly(k.joint, [(35, 32), (39, 21), (48, 24), (45, 37)])
    p.poly(lo(k.plate), [(48, 15), (51, 3), (55, 13)])
    p.poly(k.steel, [(37, 19), (43, 12), (52, 12), (56, 19), (53, 28), (41, 28)])
    p.poly(k.plate, [(40, 16), (33, 4), (45, 13)])
    p.px(k.cyan, [(36, 8), (36, 9), (37, 8), (37, 9)])
    p.rect(k.dark, (40, 16, 55, 19), shade=False)
    if k.eye:
        p.px(k.eye, [(41, 16), (41, 17), (42, 16), (42, 17), (42, 18), (43, 17), (43, 18), (44, 17), (44, 18), (45, 17), (45, 18), (45, 19), (46, 18), (46, 19)])
        p.px(k.eye, [(49, 18), (49, 19), (50, 18), (50, 19), (50, 17), (51, 17), (51, 18), (52, 17), (52, 18), (53, 17), (53, 18), (53, 16), (54, 16), (54, 17)])
    p.poly(k.plate, [(44, 21), (55, 21), (59, 25), (55, 31), (47, 31)])
    p.px(k.jaw, [(57, 24), (57, 25), (58, 24), (58, 25), (59, 24), (59, 25)])
    if s.mouth:
        p.poly(k.jaw, [(47, 29), (56, 29), (55, 36), (48, 35)], shade=False)
        p.px(k.teeth, [(48, 29), (48, 30), (49, 29), (49, 30), (50, 30), (50, 31), (51, 30), (51, 31), (53, 29), (53, 30), (54, 29), (54, 30), (50, 34), (50, 35), (51, 34), (51, 35)])
    else:
        p.line(k.jaw, [(47, 29), (55, 29)])
        p.px(k.teeth, [(48, 30), (48, 31), (49, 30), (49, 31), (50, 30), (50, 31), (51, 30), (51, 31), (53, 30), (53, 31), (54, 30), (54, 31)])


def _cacheon_bq(p, s, k):
    # 3/4 back: head furthest away at the right, rump and tail nearest
    dleg(p, s, lo(k.joint), 39, 40, 0)
    dleg(p, s, lo(k.joint), 33, 41, 1)
    p.poly(lo(k.plate), [(45, 15), (49, 3), (52, 13)])
    p.poly(k.joint, [(33, 31), (39, 20), (47, 23), (43, 35)])
    p.poly(k.plate, [(51, 19), (59, 20), (59, 24), (52, 25)])
    p.poly(k.steel, [(37, 19), (41, 12), (51, 13), (53, 20), (48, 27), (39, 25)])
    p.poly(k.plate, [(40, 16), (35, 3), (45, 13)])
    p.px(k.cyan, [(37, 6), (37, 7), (38, 6), (38, 7)])
    p.px(lo(k.steel), [(42, 14), (42, 15), (43, 14), (43, 15), (42, 16), (42, 17), (43, 16), (43, 17), (42, 18), (43, 18), (42, 19), (43, 19)])
    p.rect(k.dark, (49, 16, 54, 19), shade=False)
    if k.eye:
        p.px(k.eye, [(52, 17), (52, 18), (53, 17), (53, 18), (54, 17), (54, 18)])
    p.line(k.jaw, [(52, 24), (59, 24)])
    p.poly(k.steel, [(11, 36), (16, 28), (36, 27), (43, 31), (41, 40), (32, 45), (16, 49), (11, 44)])
    p.poly(k.plate, [(16, 28), (35, 27), (36, 32), (17, 35)])
    for x0 in (19, 25, 32):
        p.poly(k.dark, [(x0, 28), (x0 + 2, 22), (x0 + 4, 27)], shade=False)
    p.px(k.cyan, [(16, 38), (16, 39), (17, 38), (17, 39), (18, 38), (18, 39), (20, 40), (20, 41), (21, 40), (21, 41), (22, 40), (22, 41)])
    p.px(k.hot, [(16 + 4 * (s.t % 2), 38 + s.t % 2), (17 + 4 * (s.t % 2), 38 + s.t % 2)])
    dleg(p, s, k.joint, 16, 44, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 28, 45, 1, hind=True, paw=k.dark)
    p.line(k.dark, [(13, 33), (8, 25), (7, 16)], width=3)
    p.poly(k.plate, [(7, 17), (4, 5), (11, 13)])
    p.px(k.cyan, [(5, 10), (5, 11), (6, 10), (6, 11)])


def _cacheon_back(p, s, k):
    # left half only (mirrored): rump, hind legs, tail up the middle
    p.poly(k.plate, [(23, 16), (16, 3), (27, 12)])
    p.px(k.cyan, [(18, 6), (18, 7), (19, 6), (19, 7)])
    p.poly(k.steel, [(21, 17), (25, 11), (32, 11), (32, 27), (24, 25)])
    p.px(lo(k.steel), [(26, 14), (26, 15), (27, 14), (27, 15), (26, 16), (26, 17), (27, 16), (27, 17), (26, 18), (27, 18)])
    p.poly(k.joint, [(24, 28), (27, 21), (32, 21), (32, 31)])
    p.poly(k.steel, [(16, 39), (21, 29), (32, 28), (32, 52), (20, 51), (16, 45)])
    p.poly(k.plate, [(21, 31), (32, 29), (32, 37), (23, 37)])
    p.px(k.cyan, [(20, 41), (20, 42), (21, 41), (21, 42), (22, 41), (22, 42)])
    p.px(k.hot, [(20 + s.t % 2, 41)])
    legs_front(p, s, k.joint, 19, 47, 4, foot=k.dark)
    # tail curls up to one side, so draw it unmirrored
    m, p.mirror = p.mirror, False
    p.line(k.dark, [(32, 36), (28, 28), (23, 21)], width=3)
    p.poly(k.plate, [(23, 23), (16, 12), (25, 17)])
    p.px(k.cyan, [(20, 17), (20, 18), (21, 17), (21, 18)])
    p.mirror = m


class TechnophasiaKit(Kit):
    accent, flare = '#2ED3EA', '#A8F6FF'


def gauntlet(p, s, col, x, y, grp, fist=None, reach=0):
    """Knuckle-walking arm from shoulder (x, y): upper arm, a heavy armoured
    forearm and a fist planted on the ground. Sized for the 64 grid."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    fx = x + dx + reach
    ex, ey = x + 1 + dx // 2 + reach // 2, y + (g - y) * 2 // 5
    p.line(col, [(x, y), (ex, ey)], width=5)
    p.poly(col, [(ex - 4, ey - 1), (ex + 4, ey - 2), (fx + 4, g - 6), (fx - 4, g - 6)])
    p.rect(fist or col, (fx - 4, g - 6, fx + 4, g))
    p.px('#AEB8C6', [(fx + 2, g - 5), (fx + 3, g - 5), (fx + 2, g - 3), (fx + 3, g - 3)])


def techno_core(p, s, k, cx, cy, r=3):
    p.ell(k.dark, (cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1), shade=False)
    p.ell(k.hot, (cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1), shade=False, sep=False)
    p.px('#FFFFFF', [(cx, cy)])


def techno_stacks(p, k, xs, y):
    for x in xs:
        p.rect(k.joint, (x, y - 8, x + 3, y))
        p.px(k.hot, sq(x + 1, y - 9, 2, 2))


def techno_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    if s.mouth:
        p.poly(k.jaw, t([(48, 34), (58, 33), (57, 39), (49, 38)]), shade=False)
        fangs(p, k, [a + x for a in (51, 55)], 37 + y, down=False)
    p.poly(k.steel, t([(44, 30), (46, 22), (55, 21), (59, 27), (58, 34), (47, 35)]))
    p.poly(k.plate, t([(46, 23), (50, 17), (56, 21)]))
    p.rect(k.dark, (49 + x, 25 + y, 59 + x, 27 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(53, 25, 3, 2) + sq(56, 26, 3, 2)))
    p.line(k.jaw, t([(50, 33), (58, 33)]))
    fangs(p, k, [a + x for a in (52, 56)], 34 + y)


def _techno_side(p, s, k):
    techno_stacks(p, k, (22, 28), 22)
    dleg(p, s, lo(k.joint), 17, 44, 1, hind=True)
    gauntlet(p, s, lo(k.joint), 36, 30, 0, reach=4)
    p.poly(k.steel, [(9, 42), (13, 31), (26, 21), (40, 17), (48, 22), (48, 36), (38, 45), (18, 50), (10, 48)])
    p.poly(k.plate, [(13, 32), (26, 22), (32, 26), (18, 38)])
    p.poly(k.plate, [(30, 21), (40, 17), (47, 22), (40, 30), (31, 27)])
    for x0, y0 in ((15, 31), (21, 26), (27, 22)):
        p.poly(k.dark, [(x0, y0), (x0 - 2, y0 - 6), (x0 + 3, y0 - 2)], shade=False)
    p.px(k.accent, sq(18, 42) + sq(22, 42) + sq(26, 41))
    p.px(k.hot, sq(18 + 4 * (s.t % 3), 42, 2, 1))
    techno_core(p, s, k, 44, 40, 2)
    dleg(p, s, k.joint, 21, 46, 0, hind=True, paw=k.dark)
    gauntlet(p, s, k.joint, 36, 28, 1, fist=k.dark, reach=3)
    p.poly(k.plate, [(29, 22), (40, 19), (43, 28), (33, 32)])
    p.px(k.accent, sq(36, 23))
    techno_head_side(p, s, k, 0, 6)


def _techno_front(p, s, k):
    # left half, mirrored: huge shoulders, arms planted wide, head sunk low
    legs_front(p, s, lo(k.joint), 20, 52, 4)
    techno_stacks(p, k, (22,), 20)
    p.poly(k.steel, [(12, 26), (20, 18), (32, 17), (32, 52), (22, 52), (15, 40)])
    p.poly(k.plate, [(18, 22), (26, 18), (32, 19), (32, 26), (22, 30)])
    techno_core(p, s, k, 32, 46, 3)
    # arms: planted on both sides, the lift alternating with the step
    m, p.mirror = p.mirror, False
    for x, grp in ((11, 0), (52, 1)):
        dx, lift = _gait(s, grp)
        lift *= 3
        g = p.ground - lift
        p.poly(k.joint, [(x - 3, 30), (x + 3, 30), (x + 5, g - 6), (x - 5, g - 6)])
        p.rect(k.dark, (x - 5, g - 6, x + 5, g))
        p.px('#AEB8C6', [(x - 2, g - 4), (x, g - 4), (x + 2, g - 4)])
        p.poly(k.plate, [(x - 7, 26), (x - 2, 19), (x + 6, 20), (x + 6, 31), (x - 5, 33)])
        p.px(k.accent, sq(x - 1, 25))
    p.mirror = m
    p.poly(k.steel, [(24, 30), (26, 24), (32, 23), (32, 38), (26, 36)])
    p.poly(k.plate, [(26, 24), (29, 19), (32, 20), (32, 24)])
    p.rect(k.dark, (25, 28, 33, 30), shade=False)
    if k.eye:
        p.px(k.eye, sq(26, 28, 2, 1) + sq(28, 29, 2, 1) + sq(30, 30, 2, 1))
    if s.mouth:
        p.poly(k.jaw, [(27, 34), (32, 34), (32, 40), (28, 39)], shade=False)
        fangs(p, k, [28], 34)
        fangs(p, k, [30], 39, down=False)
    else:
        p.line(k.jaw, [(27, 35), (32, 35)])
        fangs(p, k, [28, 30], 36)


def _techno_fq(p, s, k):
    techno_stacks(p, k, (20, 26), 22)
    dleg(p, s, lo(k.joint), 17, 45, 1, hind=True)
    gauntlet(p, s, lo(k.joint), 33, 32, 0, reach=2)
    p.poly(k.steel, [(10, 43), (14, 31), (26, 21), (40, 18), (50, 24), (50, 38), (40, 48), (20, 52), (11, 49)])
    p.poly(k.plate, [(14, 32), (26, 22), (32, 26), (19, 38)])
    for x0, y0 in ((16, 31), (22, 26), (28, 22)):
        p.poly(k.dark, [(x0, y0), (x0 - 2, y0 - 6), (x0 + 3, y0 - 2)], shade=False)
    p.px(k.accent, sq(19, 43) + sq(23, 43))
    techno_core(p, s, k, 42, 42, 3)
    dleg(p, s, k.joint, 21, 47, 0, hind=True, paw=k.dark)
    gauntlet(p, s, k.joint, 50, 32, 1, fist=k.dark, reach=4)
    p.poly(k.plate, [(28, 24), (38, 20), (42, 30), (32, 33)])
    p.poly(k.plate, [(50, 25), (58, 23), (60, 32), (53, 34)])
    p.px(k.accent, sq(34, 25) + sq(55, 27))
    # head: turned toward us, both slits meet in a V
    p.poly(k.steel, [(40, 33), (42, 25), (51, 23), (57, 28), (56, 36), (45, 38)])
    p.poly(k.plate, [(43, 25), (48, 19), (53, 24)])
    p.rect(k.dark, (42, 28, 56, 30), shade=False)
    if k.eye:
        p.px(k.eye, sq(43, 28) + sq(45, 29) + sq(47, 30, 1, 1) + sq(51, 30, 1, 1) + sq(52, 29) + sq(54, 28))
    if s.mouth:
        p.poly(k.jaw, [(45, 35), (55, 35), (54, 41), (46, 40)], shade=False)
        fangs(p, k, [47, 52], 35)
        fangs(p, k, [49], 40, down=False)
    else:
        p.line(k.jaw, [(45, 35), (55, 35)])
        fangs(p, k, [47, 50, 53], 36)


def _techno_bq(p, s, k):
    gauntlet(p, s, lo(k.joint), 44, 30, 1, reach=4)
    techno_head_side(p, s, k, -2, 4)
    gauntlet(p, s, lo(k.joint), 38, 30, 0, reach=2)
    p.poly(k.steel, [(6, 44), (10, 32), (24, 22), (40, 18), (50, 24), (48, 36), (38, 46), (16, 53), (7, 50)])
    p.poly(k.plate, [(10, 33), (24, 23), (30, 27), (15, 40)])
    p.poly(k.plate, [(30, 21), (42, 18), (48, 24), (40, 30), (31, 27)])
    for x0, y0 in ((12, 32), (18, 27), (24, 23), (30, 20)):
        p.poly(k.dark, [(x0, y0), (x0 - 2, y0 - 6), (x0 + 3, y0 - 2)], shade=False)
    techno_stacks(p, k, (34, 40), 24)
    p.px(k.accent, sq(12, 44) + sq(16, 45) + sq(20, 46))
    p.px(k.hot, sq(12 + 4 * (s.t % 3), 44 + s.t % 3, 2, 1))
    dleg(p, s, k.joint, 14, 48, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 26, 49, 1, hind=True, paw=k.dark)


def _techno_back(p, s, k):
    m, p.mirror = p.mirror, False
    for x, grp in ((11, 0), (52, 1)):
        dx, lift = _gait(s, grp)
        lift *= 3
        g = p.ground - lift
        p.poly(lo(k.joint), [(x - 3, 30), (x + 3, 30), (x + 5, g - 6), (x - 5, g - 6)])
        p.rect(k.dark, (x - 5, g - 6, x + 5, g))
    p.mirror = m
    p.poly(k.steel, [(10, 28), (20, 18), (32, 17), (32, 50), (20, 52), (13, 42)])
    p.poly(k.plate, [(12, 27), (20, 19), (26, 22), (16, 33)])
    p.poly(k.plate, [(22, 34), (32, 32), (32, 42), (24, 42)])
    for y0 in (24, 31, 38):
        p.poly(k.dark, [(28, y0), (32, y0 - 7), (32, y0)], shade=False)
    techno_stacks(p, k, (20,), 24)
    p.px(k.accent, sq(17, 44, 3, 2))
    p.px(k.hot, [(17 + s.t % 3, 44)])
    legs_front(p, s, k.joint, 18, 50, 5, foot=k.dark)
    p.poly(k.plate, [(4, 26), (9, 19), (16, 20), (16, 31), (6, 33)])


technophasia = design('technophasia', 'Tech', 'bits', '#35E0F0', views=ALL_VIEWS, size=64)(by_view({
    'kit': TechnophasiaKit, 'side': _techno_side, 'front': _techno_front, 'fq': _techno_fq,
    'bq': _techno_bq, 'back': _techno_back}))


# ================================================================ SOCIAL
class SocialKit(Kit):
    """Plum-tinted dark kit for the Social line; accent is a hot magenta."""
    steel, plate, joint, dark = '#4A4052', '#625670', '#2C2533', '#17131C'
    accent, flare = '#FF3FA4', '#FFB3DC'


def bird_leg(p, s, col, x, y, grp, talon='#AEB8C6'):
    """Thin back-bent bird leg from hip (x, y) with forward talons."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    fx = x + dx
    jx, jy = x - 3 + dx // 2, y + (g - y) // 2
    p.line(col, [(x, y), (jx, jy)], width=3)
    p.line(col, [(jx, jy), (fx, g - 1)], width=2)
    p.line(col, [(fx - 3, g), (fx + 3, g)], width=1)
    p.px(talon, [(fx + 4, g), (fx - 4, g)])


def bird_leg_front(p, s, col, x, y, talon='#AEB8C6'):
    """Front view pair of bird legs (unmirrored so they can step)."""
    m, p.mirror = p.mirror, False
    for side, xx in ((0, x), (1, p.size - 1 - x)):
        lift = 3 if (s.step == 1 and side == 0) or (s.step == 3 and side == 1) else 0
        g = p.ground - lift
        p.line(col, [(xx, y - lift), (xx, g)], width=2)
        p.line(col, [(xx - 3, g), (xx + 3, g)])
        p.px(talon, [(xx - 4, g), (xx + 4, g)])
    p.mirror = m


class ChirpletKit(SocialKit):
    pass


def chirplet_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    for tip in ((33, 12), (37, 9), (42, 10)):
        p.poly(k.plate, t([(41, 22), tip, (46, 20)]))
    p.px(k.accent, t(sq(37, 10) + sq(33, 13, 1, 1)))
    if s.mouth:
        p.poly(k.dark, t([(50, 22), (61, 20), (51, 25)]))
        p.poly(k.dark, t([(50, 28), (59, 29), (51, 30)]))
        p.poly(k.jaw, t([(51, 25), (58, 24), (58, 28), (51, 28)]), shade=False)
    else:
        p.poly(k.dark, t([(49, 22), (62, 26), (58, 28), (50, 29)]))
        p.px(k.teeth, t([(53, 27), (55, 27), (57, 27)]))
    p.poly(k.steel, t([(38, 26), (41, 19), (49, 18), (53, 23), (51, 30), (41, 31)]))
    p.poly(k.plate, t([(47, 19), (53, 23), (49, 24)]))
    p.rect(k.jaw, (43 + x, 22 + y, 51 + x, 24 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(46, 22, 3, 2) + sq(49, 23, 2, 2)))


def _chirplet_body_side(p, s, k, x=0):
    wy = wing_y(s)
    p.poly(k.dark, [(20 + x, 36), (3 + x, 28), (6 + x, 33), (2 + x, 36), (8 + x, 38), (4 + x, 42), (19 + x, 41)])
    p.px(k.accent, [(3 + x, 28), (4 + x, 29), (2 + x, 36), (3 + x, 36), (4 + x, 42)])
    p.poly(k.steel, [(16 + x, 38), (22 + x, 30), (36 + x, 27), (44 + x, 31), (44 + x, 41), (36 + x, 48), (24 + x, 48)])
    p.poly(lo(k.plate), [(34 + x, 39), (43 + x, 37), (40 + x, 46), (33 + x, 47)])
    p.poly(k.plate, [(19 + x, 36 + wy), (26 + x, 30 + wy), (38 + x, 31 + wy), (35 + x, 40 + wy), (10 + x, 43 + wy)])
    p.poly(k.dark, [(25 + x, 38 + wy), (8 + x, 45 + wy), (21 + x, 41 + wy)], shade=False)
    p.poly(k.dark, [(36 + x, 31 + wy), (38 + x, 25 + wy), (40 + x, 32 + wy)], shade=False)
    p.px(k.hot, sq(28 + x, 34 + wy, 2, 1) + sq(24 + x, 35 + wy, 2, 1))


def _chirplet_side(p, s, k):
    bird_leg(p, s, lo(k.joint), 28, 46, 1)
    _chirplet_body_side(p, s, k)
    bird_leg(p, s, k.joint, 32, 47, 0)
    chirplet_head_side(p, s, k, 0, 6)


def _chirplet_front(p, s, k):
    wy = wing_y(s)
    bird_leg_front(p, s, k.joint, 27, 47)
    p.poly(k.dark, [(20, 37 + wy), (13, 50 + wy), (18, 49 + wy), (22, 43)], shade=False)
    p.poly(k.steel, [(20, 38), (24, 30), (32, 29), (32, 51), (25, 49), (20, 44)])
    p.poly(lo(k.plate), [(26, 40), (32, 39), (32, 50), (27, 48)])
    p.poly(k.plate, [(18, 34 + wy), (24, 30 + wy), (24, 44 + wy), (16, 47 + wy)])
    p.px(k.hot, sq(20, 38 + wy, 1, 2))
    for tip in ((20, 13), (26, 11)):
        p.poly(k.plate, [(26, 25), tip, (30, 22)])
    p.px(k.accent, sq(20, 14, 1, 1) + sq(26, 12, 1, 1))
    p.poly(k.steel, [(22, 26), (25, 20), (32, 19), (32, 33), (25, 32)])
    p.rect(k.jaw, (23, 24, 33, 26), shade=False)
    if k.eye:
        p.px(k.eye, sq(24, 24, 2, 1) + sq(26, 25, 2, 1) + sq(28, 26, 3, 1))
    if s.mouth:
        p.poly(k.dark, [(28, 27), (32, 27), (32, 30), (29, 29)])
        p.poly(k.jaw, [(29, 30), (32, 30), (32, 33), (30, 33)], shade=False)
        p.poly(k.dark, [(29, 33), (32, 33), (32, 37), (31, 37)])
    else:
        p.poly(k.dark, [(28, 27), (32, 27), (32, 36), (31, 36)])
        p.px(k.teeth, [(30, 31), (30, 33)])


def _chirplet_fq(p, s, k):
    bird_leg(p, s, lo(k.joint), 29, 46, 1)
    _chirplet_body_side(p, s, k, 1)
    bird_leg(p, s, k.joint, 34, 48, 0)
    y = 6
    for tip in ((35, 12), (41, 9), (47, 11)):
        p.poly(k.plate, [(43, 22 + y), (tip[0], tip[1] + y), (48, 20 + y)])
    p.px(k.accent, sq(41, 10 + y, 1, 1) + sq(35, 13 + y, 1, 1))
    p.poly(k.steel, [(39, 26 + y), (42, 19 + y), (51, 18 + y), (56, 23 + y), (53, 31 + y), (43, 32 + y)])
    p.rect(k.jaw, (42, 22 + y, 56, 24 + y), shade=False)
    if k.eye:
        p.px(k.eye, sq(43, 22 + y, 2, 1) + sq(45, 23 + y, 2, 1) + sq(47, 24 + y, 1, 1) + sq(51, 24 + y, 1, 1) + sq(52, 23 + y, 2, 1) + sq(54, 22 + y, 2, 1))
    if s.mouth:
        p.poly(k.dark, [(47, 25 + y), (60, 26 + y), (49, 29 + y)])
        p.poly(k.jaw, [(49, 29 + y), (58, 28 + y), (57, 31 + y), (50, 32 + y)], shade=False)
        p.poly(k.dark, [(49, 32 + y), (57, 32 + y), (50, 35 + y)])
    else:
        p.poly(k.dark, [(47, 25 + y), (61, 28 + y), (57, 31 + y), (49, 31 + y)])
        p.px(k.teeth, [(52, 30 + y), (54, 30 + y), (56, 30 + y)])


def _chirplet_bq(p, s, k):
    bird_leg(p, s, lo(k.joint), 33, 47, 0)
    y = 6
    p.poly(k.dark, [(49, 23 + y), (60, 25 + y), (50, 28 + y)])
    p.poly(k.steel, [(37, 26 + y), (40, 19 + y), (48, 18 + y), (52, 23 + y), (50, 30 + y), (40, 31 + y)])
    p.rect(k.jaw, (48, 22 + y, 52, 24 + y), shade=False)
    if k.eye:
        p.px(k.eye, sq(50, 22 + y, 2, 2))
    for tip in ((30, 13), (35, 9), (41, 10)):
        p.poly(k.plate, [(39, 22 + y), (tip[0], tip[1] + y), (45, 20 + y)])
    p.px(k.accent, sq(35, 10 + y, 1, 1) + sq(30, 14 + y, 1, 1))
    wy = wing_y(s)
    p.poly(k.dark, [(20, 36), (2, 32), (5, 37), (2, 42), (8, 42), (6, 47), (20, 42)])
    p.px(k.accent, [(2, 32), (3, 32), (2, 42), (6, 47)])
    p.poly(k.steel, [(14, 38), (20, 30), (34, 28), (42, 32), (41, 42), (32, 49), (20, 48)])
    p.poly(k.plate, [(16, 36 + wy), (24, 30 + wy), (36, 31 + wy), (34, 41 + wy), (8, 45 + wy)])
    p.poly(k.plate, [(28, 33 + wy), (34, 29 + wy), (42, 33 + wy), (38, 40 + wy)])
    p.poly(k.dark, [(23, 39 + wy), (6, 47 + wy), (19, 42 + wy)], shade=False)
    p.px(k.hot, sq(26, 34 + wy, 2, 1) + sq(22, 35 + wy, 2, 1))
    bird_leg(p, s, k.joint, 26, 47, 1)


def _chirplet_back(p, s, k):
    wy = wing_y(s)
    bird_leg_front(p, s, k.joint, 27, 47)
    for tip in ((20, 13), (26, 11)):
        p.poly(k.plate, [(26, 25), tip, (30, 22)])
    p.px(k.accent, sq(20, 14, 1, 1) + sq(26, 12, 1, 1))
    p.poly(k.steel, [(22, 26), (25, 20), (32, 19), (32, 33), (25, 32)])
    p.poly(k.plate, [(27, 21), (32, 20), (32, 30), (28, 28)])
    p.poly(k.steel, [(20, 38), (24, 30), (32, 29), (32, 51), (25, 49), (20, 44)])
    p.poly(k.plate, [(17, 33 + wy), (26, 30 + wy), (32, 34 + wy), (32, 46 + wy), (22, 48 + wy), (15, 46 + wy)])
    p.line(k.dark, [(32, 35 + wy), (32, 46 + wy)], width=2)
    p.px(k.hot, sq(22, 37 + wy, 2, 1) + sq(21, 40 + wy, 2, 1))
    p.poly(k.dark, [(27, 45), (22, 56), (28, 53), (32, 58), (32, 46)])
    p.px(k.accent, [(22, 56), (32, 58)])


chirplet = design('chirplet', 'Social', 'heart', '#FF6FAE', views=ALL_VIEWS, size=64)(by_view({
    'kit': ChirpletKit, 'side': _chirplet_side, 'front': _chirplet_front, 'fq': _chirplet_fq,
    'bq': _chirplet_bq, 'back': _chirplet_back}))


class ViraliaKit(SocialKit):
    accent, flare = '#8CFF4A', '#DFFFC2'


def virus(p, k, x, y):
    """Spiked glowing orb centred on (x, y): a viralia tail tip."""
    p.px(k.joint, [(x - 3, y), (x + 3, y), (x, y - 3), (x, y + 3), (x - 2, y - 2), (x + 2, y - 2), (x - 2, y + 2), (x + 2, y + 2)])
    p.ell(k.accent, (x - 2, y - 2, x + 2, y + 2), shade=False, sep=False)
    p.px(k.hot, [(x - 1, y - 1), (x, y - 1)])


def viralia_tails(p, s, k, base, tips):
    sway = 1 if s.t % 8 >= 4 else 0
    for i, (tx, ty) in enumerate(tips):
        ty += sway if i % 2 == 0 else -sway
        mx, my = (base[0] + tx) // 2 + (2 if tx > base[0] else -2), (base[1] + ty) // 2 + 2
        p.line(k.joint, [base, (mx, my), (tx, ty)], width=2)
    for i, (tx, ty) in enumerate(tips):
        virus(p, k, tx, ty + (sway if i % 2 == 0 else -sway))


def viralia_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(lo(k.plate), t([(47, 16), (47, 2), (52, 14)]))
    if s.mouth:
        p.poly(k.jaw, t([(50, 25), (61, 25), (58, 30), (50, 29)]), shade=False)
        fangs(p, k, [a + x for a in (53, 57)], 28 + y, down=False)
    p.poly(k.steel, t([(42, 20), (45, 13), (53, 14), (56, 19), (51, 25), (43, 25)]))
    p.poly(k.plate, t([(51, 18), (62, 21), (61, 24), (52, 25)]))
    p.poly(k.plate, t([(44, 15), (40, 0), (50, 13)]))
    p.px(k.accent, t(sq(41, 3, 2, 2)))
    p.poly(k.joint, t([(41, 24), (38, 28), (44, 26)]))
    p.rect(k.jaw, (45 + x, 17 + y, 54 + x, 18 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(48, 17, 3, 1) + sq(50, 18, 4, 1)))
    p.line(k.jaw, t([(52, 25), (60, 24)]))
    fangs(p, k, [a + x for a in (54, 57)], 25 + y)
    p.px(k.jaw, t(sq(61, 21)))


def viralia_mane(p, k, x, y):
    for a, b, c in (((0, 3), (3, -6), (6, 2)), ((5, 1), (9, -8), (11, 1)), ((10, 0), (15, -7), (16, 2))):
        p.poly(k.joint, [(a[0] + x, a[1] + y), (b[0] + x, b[1] + y), (c[0] + x, c[1] + y)])


def _viralia_side(p, s, k):
    viralia_tails(p, s, k, (15, 35), [(5, 18), (3, 30), (11, 12)])
    dleg(p, s, lo(k.joint), 20, 40, 1, hind=True)
    dleg(p, s, lo(k.joint), 40, 40, 0)
    p.poly(k.steel, [(13, 36), (18, 29), (38, 27), (46, 31), (44, 39), (36, 43), (22, 43), (15, 41)])
    p.poly(k.plate, [(19, 29), (33, 27), (34, 32), (20, 33)])
    p.poly(lo(k.steel), [(24, 39), (36, 38), (34, 43), (24, 43)])
    viralia_mane(p, k, 28, 28)
    p.px(k.accent, sq(26, 34, 2, 1) + sq(30, 34, 2, 1))
    p.px(k.hot, sq(26 + 4 * (s.t % 2), 34, 2, 1))
    dleg(p, s, k.joint, 24, 41, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 43, 41, 1, paw=k.dark)
    p.poly(k.joint, [(38, 30), (43, 20), (49, 23), (46, 34)])
    viralia_head_side(p, s, k)


def _viralia_front(p, s, k):
    m, p.mirror = p.mirror, False
    viralia_tails(p, s, k, (32, 38), [(14, 22), (50, 24), (40, 14)])
    p.mirror = m
    legs_front(p, s, lo(k.joint), 19, 48, 3)
    p.poly(k.joint, [(24, 21), (32, 21), (32, 35), (23, 32)])
    p.poly(k.steel, [(19, 35), (24, 28), (32, 28), (32, 50), (25, 48), (19, 43)])
    p.poly(lo(k.steel), [(26, 38), (32, 37), (32, 49), (27, 47)])
    p.poly(k.joint, [(19, 33), (21, 24), (25, 30)])
    legs_front(p, s, k.joint, 24, 44, 3, foot=k.dark)
    p.poly(k.plate, [(24, 15), (18, 0), (28, 11)])
    p.px(k.accent, sq(19, 3))
    p.poly(k.steel, [(21, 16), (26, 9), (32, 9), (32, 28), (28, 28), (22, 22)])
    p.rect(k.jaw, (23, 15, 33, 17), shade=False)
    if k.eye:
        p.px(k.eye, sq(24, 15, 3, 1) + sq(26, 16, 3, 1) + sq(29, 17, 2, 1))
    p.poly(k.plate, [(28, 19), (32, 18), (32, 33), (29, 31)])
    p.px(k.jaw, sq(31, 19, 1, 2))
    if s.mouth:
        p.poly(k.jaw, [(28, 29), (32, 29), (32, 38), (29, 36)], shade=False)
        fangs(p, k, [29], 29)
        fangs(p, k, [30], 37, down=False)
    else:
        p.px(k.jaw, sq(28, 31, 4, 1))
        fangs(p, k, [29], 32)


def _viralia_fq(p, s, k):
    viralia_tails(p, s, k, (17, 35), [(6, 18), (4, 30), (13, 11)])
    dleg(p, s, lo(k.joint), 21, 40, 1, hind=True)
    dleg(p, s, lo(k.joint), 43, 42, 0)
    p.poly(k.steel, [(15, 36), (20, 29), (37, 28), (45, 33), (44, 43), (35, 47), (23, 46), (17, 41)])
    p.poly(k.plate, [(20, 29), (34, 28), (36, 33), (22, 34)])
    viralia_mane(p, k, 27, 29)
    p.px(k.accent, sq(26, 37, 2, 1) + sq(30, 37, 2, 1))
    dleg(p, s, k.joint, 25, 42, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 37, 44, 1, paw=k.dark)
    p.poly(k.joint, [(35, 32), (39, 21), (48, 24), (45, 37)])
    p.poly(lo(k.plate), [(49, 14), (53, 0), (56, 13)])
    p.poly(k.steel, [(37, 19), (43, 12), (52, 12), (56, 19), (53, 27), (41, 27)])
    p.poly(k.plate, [(40, 15), (34, 0), (45, 12)])
    p.px(k.accent, sq(35, 3) + sq(52, 3))
    p.rect(k.jaw, (40, 16, 55, 18), shade=False)
    if k.eye:
        p.px(k.eye, sq(41, 16, 3, 1) + sq(43, 17, 3, 1) + sq(46, 18, 1, 1) + sq(50, 18, 1, 1) + sq(51, 17, 3, 1) + sq(53, 16, 2, 1))
    p.poly(k.plate, [(45, 20), (55, 20), (60, 24), (55, 29), (48, 29)])
    p.px(k.jaw, sq(58, 23))
    if s.mouth:
        p.poly(k.jaw, [(47, 28), (56, 28), (55, 34), (48, 33)], shade=False)
        fangs(p, k, [48, 53], 28)
        fangs(p, k, [50], 33, down=False)
    else:
        p.line(k.jaw, [(48, 28), (56, 28)])
        fangs(p, k, [49, 53], 29)


def _viralia_bq(p, s, k):
    dleg(p, s, lo(k.joint), 39, 40, 0)
    dleg(p, s, lo(k.joint), 33, 41, 1)
    p.poly(lo(k.plate), [(46, 15), (49, 1), (53, 13)])
    p.poly(k.joint, [(33, 31), (39, 20), (47, 23), (43, 35)])
    p.poly(k.plate, [(51, 18), (60, 20), (59, 24), (52, 24)])
    p.poly(k.steel, [(37, 19), (41, 12), (51, 13), (53, 20), (48, 26), (39, 25)])
    p.poly(k.plate, [(40, 15), (34, 0), (45, 12)])
    p.px(k.accent, sq(35, 3))
    p.px(lo(k.steel), sq(42, 14, 2, 7))
    p.rect(k.jaw, (49, 16, 54, 17), shade=False)
    if k.eye:
        p.px(k.eye, sq(51, 16, 3, 2))
    p.poly(k.steel, [(11, 36), (16, 28), (36, 27), (43, 31), (41, 40), (32, 45), (16, 47), (11, 43)])
    p.poly(k.plate, [(16, 28), (35, 27), (36, 32), (17, 34)])
    viralia_mane(p, k, 22, 28)
    dleg(p, s, k.joint, 16, 43, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 28, 44, 1, hind=True, paw=k.dark)
    viralia_tails(p, s, k, (12, 34), [(4, 16), (2, 28), (13, 9)])


def _viralia_back(p, s, k):
    p.poly(k.plate, [(24, 15), (18, 0), (28, 11)])
    p.px(k.accent, sq(19, 3))
    p.poly(k.steel, [(21, 17), (25, 11), (32, 11), (32, 27), (24, 25)])
    p.px(lo(k.steel), sq(26, 14, 2, 5))
    p.poly(k.joint, [(24, 28), (27, 21), (32, 21), (32, 31)])
    p.poly(k.steel, [(17, 39), (21, 29), (32, 28), (32, 50), (21, 49), (17, 45)])
    p.poly(k.plate, [(21, 31), (32, 29), (32, 37), (23, 37)])
    for y0 in (31, 37):
        p.poly(k.joint, [(28, y0 + 1), (32, y0 - 7), (32, y0 + 1)])
    legs_front(p, s, k.joint, 19, 46, 4, foot=k.dark)
    m, p.mirror = p.mirror, False
    viralia_tails(p, s, k, (32, 40), [(14, 28), (50, 30), (44, 12)])
    p.mirror = m


viralia = design('viralia', 'Social', 'heart', '#D9469B', views=ALL_VIEWS, size=64)(by_view({
    'kit': ViraliaKit, 'side': _viralia_side, 'front': _viralia_front, 'fq': _viralia_fq,
    'bq': _viralia_bq, 'back': _viralia_back}))


class TrendrakeKit(SocialKit):
    pass


def bat_wing(p, k, root, tips, tail, wy=0, far=False):
    """Membrane wing: skin from root through each finger tip down to `tail`,
    with plated bones and a glowing claw on the first finger. The far wing
    is darker so the two read apart."""
    tips = [(x, y + wy) for x, y in tips]
    p.poly(k.dark if far else k.joint, [root] + tips + [tail])
    for tip in tips:
        p.line(k.plate, [root, tip], width=2)
    p.px(k.accent, sq(tips[0][0] - 1, tips[0][1] - 1))


def trendrake_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(lo(k.plate), t([(46, 12), (41, 2), (50, 9)]))
    if s.mouth:
        p.poly(k.jaw, t([(51, 19), (62, 19), (60, 25), (51, 23)]), shade=False)
        fangs(p, k, [a + x for a in (54, 58)], 22 + y, down=False)
    p.poly(k.steel, t([(43, 15), (46, 9), (54, 9), (58, 14), (53, 20), (44, 20)]))
    p.poly(k.plate, t([(52, 12), (62, 15), (62, 18), (53, 20)]))
    p.poly(k.plate, t([(47, 10), (38, 0), (51, 9)]))
    p.px(k.accent, t(sq(39, 1)))
    p.rect(k.jaw, (47 + x, 12 + y, 55 + x, 13 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(50, 12, 3, 1) + sq(52, 13, 4, 1)))
    p.line(k.jaw, t([(53, 20), (61, 19)]))
    fangs(p, k, [a + x for a in (55, 59)], 20 + y)
    p.px(k.jaw, t(sq(61, 15)))


def trendrake_tail(p, k, pts):
    p.line(k.steel, pts, width=4)
    x, y = pts[-1]
    p.poly(k.plate, [(x + 2, y - 3), (x - 6, y - 1), (x + 1, y + 4)])
    p.px(k.accent, [(x - 5, y - 1), (x - 4, y - 1)])


def _trendrake_body_side(p, s, k, wy):
    bat_wing(p, k, (30, 30), [(22, 3), (10, 8), (4, 20)], (18, 32), wy, far=True)
    trendrake_tail(p, k, [(16, 40), (8, 44), (4, 52), (8, 56)])
    dleg(p, s, lo(k.joint), 20, 42, 1, hind=True)
    dleg(p, s, lo(k.joint), 40, 43, 0)
    p.poly(k.steel, [(13, 40), (18, 32), (37, 29), (46, 33), (44, 43), (36, 48), (20, 48), (14, 45)])
    p.poly(k.plate, [(19, 32), (36, 29), (38, 35), (21, 36)])
    for x0 in (22, 29):
        p.poly(k.dark, [(x0, 31), (x0 + 2, 25), (x0 + 4, 31)], shade=False)
    p.poly(lo(k.steel), [(26, 42), (38, 41), (36, 47), (26, 47)])
    for yy in (43, 45):
        p.px(k.joint, [(xx, yy) for xx in range(27, 37, 2)])
    p.px(k.hot, sq(30, 38, 2, 1) + sq(34, 38, 2, 1))
    dleg(p, s, k.joint, 24, 43, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 43, 44, 1, paw=k.dark)


def _trendrake_side(p, s, k):
    wy = wing_y(s)
    _trendrake_body_side(p, s, k, wy)
    p.poly(k.joint, [(37, 33), (43, 16), (50, 18), (47, 36)])
    for yy in (22, 27, 32):
        p.px(lo(k.joint), [(43, yy), (44, yy), (45, yy)])
    trendrake_head_side(p, s, k)
    bat_wing(p, k, (34, 31), [(30, 6), (20, 10), (14, 22)], (24, 36), wy)


def _trendrake_front(p, s, k):
    wy = wing_y(s)
    bat_wing(p, k, (22, 30), [(8, 6), (2, 16), (1, 30)], (14, 40), wy)
    legs_front(p, s, lo(k.joint), 20, 50, 3)
    p.poly(k.steel, [(17, 38), (22, 30), (32, 29), (32, 53), (23, 51), (17, 46)])
    p.poly(lo(k.steel), [(26, 36), (32, 35), (32, 52), (27, 50)])
    for yy in (39, 43, 47):
        p.px(k.joint, [(xx, yy) for xx in range(27, 33)])
    legs_front(p, s, k.joint, 23, 47, 3, foot=k.dark)
    p.poly(k.joint, [(26, 32), (27, 18), (32, 17), (32, 34)])
    p.poly(lo(k.plate), [(25, 12), (17, 1), (27, 8)])
    p.px(k.accent, sq(17, 1))
    p.poly(k.steel, [(22, 13), (26, 7), (32, 7), (32, 24), (27, 23), (23, 18)])
    p.rect(k.jaw, (23, 12, 33, 14), shade=False)
    if k.eye:
        p.px(k.eye, sq(24, 12, 2, 1) + sq(26, 13, 3, 1) + sq(29, 14, 2, 1))
    p.poly(k.plate, [(28, 16), (32, 15), (32, 28), (29, 26)])
    p.px(k.jaw, sq(31, 17, 1, 2))
    if s.mouth:
        p.poly(k.jaw, [(28, 24), (32, 24), (32, 33), (29, 31)], shade=False)
        fangs(p, k, [29], 24)
        fangs(p, k, [30], 32, down=False)
    else:
        p.px(k.jaw, sq(28, 26, 4, 1))
        fangs(p, k, [29], 27)


def _trendrake_fq(p, s, k):
    wy = wing_y(s)
    _trendrake_body_side(p, s, k, wy)
    p.poly(k.joint, [(37, 33), (42, 17), (50, 19), (47, 36)])
    p.poly(lo(k.plate), [(51, 12), (56, 1), (57, 11)])
    p.poly(k.steel, [(39, 16), (44, 9), (52, 9), (56, 15), (53, 22), (42, 22)])
    p.poly(k.plate, [(43, 11), (35, 0), (47, 9)])
    p.px(k.accent, sq(35, 0) + sq(55, 1))
    p.rect(k.jaw, (41, 13, 55, 15), shade=False)
    if k.eye:
        p.px(k.eye, sq(42, 13, 3, 1) + sq(44, 14, 3, 1) + sq(47, 15, 1, 1) + sq(50, 15, 1, 1) + sq(51, 14, 3, 1) + sq(53, 13, 2, 1))
    p.poly(k.plate, [(45, 17), (55, 17), (60, 21), (55, 26), (48, 26)])
    p.px(k.jaw, sq(58, 20))
    if s.mouth:
        p.poly(k.jaw, [(47, 25), (56, 25), (55, 31), (48, 30)], shade=False)
        fangs(p, k, [48, 53], 25)
        fangs(p, k, [50], 30, down=False)
    else:
        p.line(k.jaw, [(48, 25), (56, 25)])
        fangs(p, k, [49, 53], 26)
    bat_wing(p, k, (32, 31), [(28, 5), (16, 9), (10, 22)], (22, 36), wy)


def _trendrake_bq(p, s, k):
    wy = wing_y(s)
    dleg(p, s, lo(k.joint), 39, 42, 0)
    dleg(p, s, lo(k.joint), 33, 43, 1)
    p.poly(k.joint, [(35, 33), (41, 16), (48, 18), (45, 36)])
    trendrake_head_side(p, s, k, -2, 2)
    bat_wing(p, k, (34, 30), [(40, 2), (50, 6), (58, 14)], (44, 30), wy, far=True)
    p.poly(k.steel, [(10, 40), (15, 31), (36, 29), (43, 33), (41, 42), (32, 47), (16, 50), (10, 46)])
    p.poly(k.plate, [(15, 31), (35, 29), (36, 34), (16, 36)])
    for x0 in (18, 25, 32):
        p.poly(k.dark, [(x0, 31), (x0 + 2, 25), (x0 + 4, 31)], shade=False)
    dleg(p, s, k.joint, 16, 46, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 28, 47, 1, hind=True, paw=k.dark)
    trendrake_tail(p, k, [(12, 40), (6, 46), (4, 54), (9, 58)])
    bat_wing(p, k, (26, 31), [(20, 3), (8, 8), (2, 20)], (14, 34), wy)


def _trendrake_back(p, s, k):
    wy = wing_y(s)
    p.poly(lo(k.plate), [(25, 14), (17, 3), (27, 10)])
    p.px(k.accent, sq(17, 3))
    p.poly(k.steel, [(22, 15), (26, 9), (32, 9), (32, 24), (25, 22)])
    p.poly(k.joint, [(26, 32), (27, 20), (32, 19), (32, 34)])
    for yy in (22, 27):
        p.poly(k.dark, [(29, yy + 3), (32, yy - 3), (32, yy + 3)], shade=False)
    bat_wing(p, k, (24, 30), [(10, 4), (3, 14), (1, 28)], (16, 40), wy)
    p.poly(k.steel, [(16, 40), (21, 31), (32, 30), (32, 53), (21, 52), (16, 47)])
    p.poly(k.plate, [(21, 32), (32, 31), (32, 39), (23, 39)])
    p.poly(k.dark, [(28, 38), (32, 30), (32, 38)], shade=False)
    legs_front(p, s, k.joint, 19, 48, 4, foot=k.dark)
    m, p.mirror = p.mirror, False
    trendrake_tail(p, k, [(32, 44), (35, 52), (40, 56), (46, 57)])
    p.mirror = m


trendrake = design('trendrake', 'Social', 'heart', '#B8337A', views=ALL_VIEWS, size=64)(by_view({
    'kit': TrendrakeKit, 'side': _trendrake_side, 'front': _trendrake_front, 'fq': _trendrake_fq,
    'bq': _trendrake_bq, 'back': _trendrake_back}))


# ================================================================ GAMING
class GamingKit(Kit):
    """Violet-slate dark kit for the Gaming line; accent is an RGB violet."""
    steel, plate, joint, dark = '#4B4660', '#645D7E', '#2B2838', '#16141D'
    accent, flare = '#B45CFF', '#E6C8FF'


class NoobitKit(GamingKit):
    pass


def claw_arm(p, s, col, x, y, hx, hy, grp=0):
    """Dangling arm from shoulder (x, y) to a clawed hand at (hx, hy) that
    swings against the step."""
    dx, _ = _gait(s, grp)
    hx += dx * 2
    ex, ey = (x + hx) // 2 - 2, (y + hy) // 2
    p.line(col, [(x, y), (ex, ey)], width=3)
    p.line(col, [(ex, ey), (hx, hy)], width=2)
    p.px('#AEB8C6', [(hx - 1, hy + 1), (hx + 1, hy + 1), (hx + 2, hy)])


def headset_side(p, k, x, y, mic=True):
    """Band over the skull, glowing ear cup centred at (x, y), mic boom."""
    p.line(k.dark, [(x - 6, y - 4), (x - 1, y - 11), (x + 7, y - 10)], width=2)
    p.ell(k.dark, (x - 3, y - 3, x + 3, y + 3))
    p.px(k.hot, sq(x - 1, y - 1, 3, 3))
    if mic:
        p.line(k.dark, [(x + 2, y + 2), (x + 8, y + 7)])
        p.px(k.accent, sq(x + 8, y + 6))


def noobit_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(lo(k.plate), t([(44, 20), (38, 10), (47, 17)]))
    if s.mouth:
        p.poly(k.jaw, t([(48, 29), (58, 28), (56, 34), (48, 33)]), shade=False)
        fangs(p, k, [a + x for a in (50, 54)], 32 + y, down=False)
    p.poly(k.steel, t([(38, 24), (42, 17), (51, 17), (58, 23), (57, 28), (43, 30)]))
    p.poly(k.plate, t([(49, 19), (58, 23), (54, 25), (48, 22)]))
    p.poly(k.plate, t([(40, 20), (30, 12), (44, 18)]))
    p.px(k.accent, t(sq(31, 12)))
    p.rect(k.jaw, (45 + x, 21 + y, 52 + x, 23 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(47, 21, 2, 2) + sq(49, 22, 3, 2)))
    p.line(k.jaw, t([(49, 29), (57, 28)]))
    fangs(p, k, [a + x for a in (50, 54)], 29 + y)
    headset_side(p, k, 42 + x, 24 + y)


def _noobit_side(p, s, k):
    dleg(p, s, lo(k.joint), 22, 46, 1, hind=True)
    claw_arm(p, s, lo(k.joint), 32, 32, 38, 48, 0)
    p.poly(k.steel, [(15, 46), (18, 36), (27, 28), (38, 28), (41, 36), (36, 46), (24, 50)])
    p.poly(k.plate, [(19, 36), (27, 29), (33, 31), (24, 40)])
    for x0, y0 in ((18, 36), (23, 31)):
        p.poly(k.dark, [(x0, y0), (x0 - 3, y0 - 5), (x0 + 3, y0 - 2)], shade=False)
    p.poly(lo(k.steel), [(30, 40), (39, 38), (36, 46), (30, 47)])
    dleg(p, s, k.joint, 26, 47, 0, hind=True, paw=k.dark)
    noobit_head_side(p, s, k, 0, 6)
    claw_arm(p, s, k.joint, 34, 34, 42, 50, 1)


def _noobit_front(p, s, k):
    legs_front(p, s, k.joint, 21, 50, 4, foot=k.dark)
    p.poly(k.steel, [(18, 42), (21, 33), (32, 31), (32, 53), (22, 52)])
    p.poly(lo(k.steel), [(26, 42), (32, 41), (32, 52), (27, 51)])
    m, p.mirror = p.mirror, False
    for x, grp in ((17, 0), (46, 1)):
        dx, _ = _gait(s, grp)
        hx = x + (-3 if x < 32 else 3)
        p.line(k.joint, [(x, 36), (hx, 44 + dx)], width=3)
        p.line(k.joint, [(hx, 44 + dx), (hx, 52 + dx)], width=2)
        p.px('#AEB8C6', [(hx - 1, 53 + dx), (hx + 1, 53 + dx)])
    p.mirror = m
    p.poly(k.plate, [(24, 22), (11, 13), (26, 18)])
    p.px(k.accent, sq(11, 13))
    p.poly(k.steel, [(23, 24), (26, 17), (32, 16), (32, 36), (27, 35), (24, 30)])
    p.line(k.dark, [(22, 22), (26, 13), (32, 12)], width=2)
    p.ell(k.dark, (18, 20, 24, 28))
    p.px(k.hot, sq(20, 23, 3, 3))
    p.rect(k.jaw, (25, 22, 33, 24), shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 22, 2, 1) + sq(27, 23, 2, 1) + sq(29, 24, 2, 1))
    p.poly(k.plate, [(28, 26), (32, 25), (32, 33), (29, 32)])
    m, p.mirror = p.mirror, False
    p.line(k.dark, [(21, 28), (25, 34), (28, 35)])
    p.px(k.accent, sq(28, 34))
    p.mirror = m
    if s.mouth:
        p.poly(k.jaw, [(27, 31), (32, 31), (32, 38), (28, 37)], shade=False)
        fangs(p, k, [28], 31)
        fangs(p, k, [30], 37, down=False)
    else:
        p.px(k.jaw, sq(27, 32, 5, 1))
        fangs(p, k, [28, 30], 33)


def _noobit_fq(p, s, k):
    dleg(p, s, lo(k.joint), 23, 46, 1, hind=True)
    claw_arm(p, s, lo(k.joint), 30, 32, 34, 48, 0)
    p.poly(k.steel, [(16, 46), (19, 36), (28, 28), (38, 29), (42, 37), (37, 47), (25, 51)])
    p.poly(k.plate, [(20, 36), (28, 29), (33, 31), (25, 40)])
    for x0, y0 in ((19, 36), (24, 31)):
        p.poly(k.dark, [(x0, y0), (x0 - 3, y0 - 5), (x0 + 3, y0 - 2)], shade=False)
    dleg(p, s, k.joint, 28, 48, 0, hind=True, paw=k.dark)
    y = 6
    p.poly(lo(k.plate), [(51, 21 + y), (60, 12 + y), (55, 24 + y)])
    p.poly(k.steel, [(37, 24 + y), (41, 17 + y), (51, 16 + y), (56, 22 + y), (54, 30 + y), (42, 31 + y)])
    p.poly(k.plate, [(40, 20 + y), (28, 13 + y), (43, 18 + y)])
    p.px(k.accent, sq(29, 13 + y) + sq(59, 12 + y))
    p.rect(k.jaw, (41, 21 + y, 55, 23 + y), shade=False)
    if k.eye:
        p.px(k.eye, sq(42, 21 + y, 2, 1) + sq(44, 22 + y, 3, 1) + sq(47, 23 + y, 1, 1) + sq(50, 23 + y, 1, 1) + sq(51, 22 + y, 3, 1) + sq(53, 21 + y, 2, 1))
    p.poly(k.plate, [(45, 25 + y), (54, 25 + y), (58, 28 + y), (54, 32 + y), (47, 32 + y)])
    if s.mouth:
        p.poly(k.jaw, [(46, 31 + y), (55, 31 + y), (54, 37 + y), (47, 36 + y)], shade=False)
        fangs(p, k, [47, 52], 31 + y)
        fangs(p, k, [49], 36 + y, down=False)
    else:
        p.line(k.jaw, [(47, 31 + y), (55, 31 + y)])
        fangs(p, k, [48, 52], 32 + y)
    headset_side(p, k, 40, 25 + y)
    claw_arm(p, s, k.joint, 36, 36, 44, 51, 1)


def _noobit_bq(p, s, k):
    claw_arm(p, s, lo(k.joint), 36, 34, 44, 50, 1)
    dleg(p, s, lo(k.joint), 28, 47, 0, hind=True)
    noobit_head_side(p, s, k, -2, 4)
    p.poly(k.steel, [(12, 46), (15, 36), (25, 28), (37, 28), (40, 36), (35, 47), (22, 51)])
    p.poly(k.plate, [(15, 36), (25, 29), (32, 30), (20, 41)])
    for x0, y0 in ((14, 38), (19, 32), (25, 29)):
        p.poly(k.dark, [(x0, y0), (x0 - 3, y0 - 5), (x0 + 3, y0 - 2)], shade=False)
    headset_side(p, k, 38, 28, mic=False)
    dleg(p, s, k.joint, 22, 48, 1, hind=True, paw=k.dark)
    claw_arm(p, s, k.joint, 18, 36, 14, 50, 0)


def _noobit_back(p, s, k):
    legs_front(p, s, k.joint, 21, 50, 4, foot=k.dark)
    m, p.mirror = p.mirror, False
    for x, grp in ((17, 0), (46, 1)):
        dx, _ = _gait(s, grp)
        hx = x + (-3 if x < 32 else 3)
        p.line(k.joint, [(x, 36), (hx, 44 + dx)], width=3)
        p.line(k.joint, [(hx, 44 + dx), (hx, 52 + dx)], width=2)
    p.mirror = m
    p.poly(k.steel, [(18, 42), (21, 33), (32, 31), (32, 53), (22, 52)])
    p.poly(k.plate, [(21, 34), (32, 32), (32, 40), (23, 41)])
    for y0 in (36, 42):
        p.poly(k.dark, [(28, y0), (32, y0 - 6), (32, y0)], shade=False)
    p.poly(k.plate, [(24, 22), (11, 13), (26, 18)])
    p.px(k.accent, sq(11, 13))
    p.poly(k.steel, [(23, 24), (26, 17), (32, 16), (32, 34), (27, 33), (24, 30)])
    p.px(lo(k.steel), sq(28, 20, 2, 10))
    p.line(k.dark, [(22, 22), (26, 13), (32, 12)], width=2)
    p.ell(k.dark, (18, 20, 24, 28))
    p.px(k.hot, sq(20, 23, 3, 3))


noobit = design('noobit', 'Gaming', 'slash', '#8E5BD6', views=ALL_VIEWS, size=64)(by_view({
    'kit': NoobitKit, 'side': _noobit_side, 'front': _noobit_front, 'fq': _noobit_fq,
    'bq': _noobit_bq, 'back': _noobit_back}))


class SkirmalotKit(GamingKit):
    blade = '#AEB8C6'


def skirm_horn(p, k, base, tip, w=3):
    """Sword horn: a blade from base to tip with a steel edge line."""
    (bx, by), (tx, ty) = base, tip
    p.poly(k.blade, [(bx - w, by), (bx + w, by + 1), (tx, ty)], shade=False)
    p.line('#E4EAF2', [(bx - 1, by), (tx, ty)], sep=False)
    p.line(k.plate, [(bx - w - 1, by + 1), (bx + w + 1, by + 2)], width=2)


def skirm_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    skirm_horn(p, k, (52 + x, 32 + y), (62 + x, 8 + y))
    if s.mouth:
        p.poly(k.jaw, t([(52, 42), (61, 41), (58, 47), (52, 46)]), shade=False)
        fangs(p, k, [a + x for a in (54, 57)], 45 + y, down=False)
    p.poly(k.steel, t([(43, 36), (47, 30), (55, 30), (59, 36), (56, 43), (46, 43)]))
    p.poly(k.plate, t([(46, 31), (52, 27), (56, 31)]))
    p.rect(k.jaw, (49 + x, 34 + y, 58 + x, 36 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(52, 34, 3, 2) + sq(55, 35, 3, 2)))
    p.poly(k.dark, t([(55, 42), (62, 44), (56, 45)]))
    fangs(p, k, [a + x for a in (50, 53)], 43 + y)


def skirm_shell_side(p, s, k, x=0):
    def t(pts):
        return [(a + x, b) for a, b in pts]
    p.poly(k.steel, t([(9, 42), (13, 30), (25, 21), (39, 21), (48, 29), (48, 41), (40, 48), (15, 48)]))
    p.poly(k.plate, t([(13, 31), (25, 22), (39, 22), (46, 29), (41, 35), (16, 38)]))
    p.line(k.dark, t([(14, 36), (26, 30), (44, 31)]), width=2)
    for x0 in (18, 26, 34):
        p.poly(k.dark, t([(x0, 25), (x0 + 3, 16), (x0 + 5, 24)]), shade=False)
    p.px(k.accent, t(sq(16, 42) + sq(22, 43) + sq(28, 43) + sq(34, 43)))
    p.px(k.hot, t(sq(16 + 6 * (s.t % 4), 42 + (s.t % 4 > 0), 2, 1)))


def _skirm_side(p, s, k):
    dleg(p, s, lo(k.joint), 20, 44, 1, hind=True)
    dleg(p, s, lo(k.joint), 38, 44, 0)
    skirm_shell_side(p, s, k)
    dleg(p, s, k.joint, 24, 45, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 42, 45, 1, paw=k.dark)
    skirm_head_side(p, s, k)


def _skirm_front(p, s, k):
    legs_front(p, s, lo(k.joint), 12, 50, 3)
    p.poly(k.steel, [(8, 38), (14, 26), (26, 20), (32, 20), (32, 48), (12, 48)])
    p.poly(k.plate, [(12, 32), (17, 24), (27, 21), (30, 30), (18, 38)])
    p.poly(k.dark, [(18, 24), (19, 14), (23, 22)], shade=False)
    p.px(k.accent, sq(12, 42) + sq(17, 44))
    legs_front(p, s, k.joint, 20, 48, 3, foot=k.dark)
    p.poly(k.blade, [(28, 32), (32, 2), (32, 32)], shade=False)
    p.line('#E4EAF2', [(31, 5), (31, 30)], sep=False)
    p.poly(k.steel, [(22, 36), (25, 30), (32, 29), (32, 45), (26, 44), (23, 40)])
    p.rect(k.jaw, (24, 34, 33, 36), shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 34, 2, 1) + sq(27, 35, 2, 1) + sq(29, 36, 2, 1))
    p.poly(k.dark, [(24, 42), (27, 40), (29, 47), (27, 48)])
    if s.mouth:
        p.poly(k.jaw, [(28, 42), (32, 42), (32, 49), (29, 48)], shade=False)
        fangs(p, k, [29], 42)
        fangs(p, k, [30], 48, down=False)
    else:
        fangs(p, k, [29], 42, h=3)


def _skirm_fq(p, s, k):
    dleg(p, s, lo(k.joint), 21, 45, 1, hind=True)
    dleg(p, s, lo(k.joint), 41, 46, 0)
    skirm_shell_side(p, s, k, -2)
    dleg(p, s, k.joint, 24, 47, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 38, 48, 1, paw=k.dark)
    skirm_horn(p, k, (50, 32), (58, 4))
    p.poly(k.steel, [(40, 38), (44, 31), (53, 30), (58, 36), (56, 44), (44, 45)])
    p.rect(k.jaw, (43, 35, 57, 37), shade=False)
    if k.eye:
        p.px(k.eye, sq(44, 35, 2, 1) + sq(46, 36, 2, 1) + sq(48, 37, 1, 1) + sq(51, 37, 1, 1) + sq(52, 36, 2, 1) + sq(54, 35, 2, 1))
    p.poly(k.dark, [(44, 43), (47, 41), (49, 49), (46, 49)])
    p.poly(k.dark, [(56, 42), (60, 44), (56, 50), (54, 49)])
    if s.mouth:
        p.poly(k.jaw, [(48, 43), (55, 43), (54, 49), (49, 49)], shade=False)
        fangs(p, k, [49, 52], 43)
    else:
        fangs(p, k, [49, 52], 44, h=3)


def _skirm_bq(p, s, k):
    dleg(p, s, lo(k.joint), 42, 45, 0)
    dleg(p, s, lo(k.joint), 36, 46, 1)
    skirm_head_side(p, s, k, -2, -2)
    p.poly(k.steel, [(7, 42), (11, 29), (24, 20), (39, 21), (48, 30), (46, 42), (37, 49), (13, 50)])
    p.poly(k.plate, [(11, 30), (24, 21), (39, 22), (46, 30), (38, 38), (14, 40)])
    p.line(k.dark, [(26, 21), (22, 32), (14, 40)], width=2)
    for x0 in (14, 22, 30, 38):
        p.poly(k.dark, [(x0, 26), (x0 + 3, 17), (x0 + 5, 25)], shade=False)
    p.px(k.accent, sq(12, 43) + sq(18, 45) + sq(24, 46))
    p.px(k.hot, sq(12 + 6 * (s.t % 3), 43 + s.t % 3, 2, 1))
    dleg(p, s, k.joint, 16, 48, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 28, 49, 1, hind=True, paw=k.dark)


def _skirm_back(p, s, k):
    p.poly(k.blade, [(29, 22), (32, 2), (32, 22)], shade=False)
    legs_front(p, s, lo(k.joint), 12, 50, 3)
    p.poly(k.steel, [(8, 38), (14, 25), (26, 19), (32, 19), (32, 50), (12, 49)])
    p.poly(k.plate, [(10, 36), (15, 26), (26, 20), (31, 21), (31, 46), (14, 46)])
    p.line(k.dark, [(32, 20), (32, 49)], width=2)
    for y0 in (24, 32, 40):
        p.poly(k.dark, [(22 - (y0 - 24) // 3, y0), (24 - (y0 - 24) // 3, y0 - 8), (26 - (y0 - 24) // 3, y0)], shade=False)
    p.px(k.accent, sq(12, 42) + sq(17, 44) + sq(22, 45))
    p.px(k.hot, [(12 + 5 * (s.t % 3), 42 + s.t % 3)])
    legs_front(p, s, k.joint, 20, 50, 3, foot=k.dark)


skirmalot = design('skirmalot', 'Gaming', 'slash', '#5D3FA8', views=ALL_VIEWS, size=64)(by_view({
    'kit': SkirmalotKit, 'side': _skirm_side, 'front': _skirm_front, 'fq': _skirm_fq,
    'bq': _skirm_bq, 'back': _skirm_back}))


class GrindlordKit(GamingKit):
    can = '#3A2E5C'


def stomp_leg(p, s, col, x, y, grp, boot=None):
    """Thick biped leg from hip (x, y): wide thigh, armoured shin, boot."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    kx, ky = x + 2 + dx // 2, y + (g - y) // 2
    p.poly(col, [(x - 4, y - 2), (x + 4, y - 2), (kx + 4, ky), (kx - 4, ky)])
    p.poly(col, [(kx - 4, ky - 1), (kx + 3, ky - 1), (x + dx + 3, g - 3), (x + dx - 4, g - 3)])
    p.poly(boot or col, [(x + dx - 5, g - 4), (x + dx + 4, g - 4), (x + dx + 7, g), (x + dx - 5, g)])


def energy_can(p, k, x, y):
    """Glowing can, top-left (x, y), 6x9."""
    p.rect(k.can, (x, y, x + 5, y + 8))
    p.rect(k.hot, (x + 1, y + 3, x + 4, y + 5), shade=False, sep=False)
    p.px('#AEB8C6', [(x + 1, y), (x + 2, y), (x + 3, y), (x + 4, y)])


def brute_arm(p, s, col, x, y, hx, hy, grp, fist=None):
    """Heavy biped arm from shoulder (x, y) to a fist at (hx, hy)."""
    dx, _ = _gait(s, grp)
    hx += dx * 2
    ex, ey = (x + hx) // 2 - 1, (y + hy) // 2
    p.line(col, [(x, y), (ex, ey)], width=5)
    p.poly(col, [(ex - 3, ey - 1), (ex + 4, ey - 1), (hx + 4, hy - 3), (hx - 3, hy - 3)])
    p.rect(fist or col, (hx - 4, hy - 4, hx + 4, hy + 2))


def grind_hood_side(p, s, k, x=0, y=0):
    """Hood seen from the right: dark face opening with slit eyes and grin,
    headset cup glowing on the side."""
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(k.steel, t([(36, 16), (42, 6), (52, 5), (58, 11), (58, 24), (52, 28), (40, 26)]))
    p.poly(k.plate, t([(42, 7), (52, 6), (56, 10), (46, 13)]))
    p.poly(k.jaw, t([(50, 12), (58, 12), (58, 25), (51, 26)]), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(52, 15, 2, 1) + sq(54, 16, 3, 1)))
    fangs(p, k, [a + x for a in (52, 55)], (21 if s.mouth else 20) + y)
    if s.mouth:
        fangs(p, k, [53 + x], 25 + y, down=False)
    p.line(k.dark, t([(40, 20), (43, 8), (50, 5)]), width=2)
    p.ell(k.dark, (40 + x, 14 + y, 47 + x, 21 + y))
    p.px(k.hot, t(sq(42, 16 + (s.t % 2), 3, 3)))


def grind_torso_side(p, k, x=0):
    def t(pts):
        return [(a + x, b) for a, b in pts]
    p.poly(k.steel, t([(12, 44), (14, 30), (24, 20), (40, 18), (48, 26), (44, 42), (32, 48), (18, 48)]))
    p.poly(lo(k.steel), t([(24, 40), (42, 36), (40, 44), (28, 47)]))
    for x0, y0 in ((15, 32), (19, 25), (25, 21)):
        p.poly(k.dark, t([(x0, y0), (x0 - 6, y0 - 3), (x0 + 1, y0 - 6)]), shade=False)


def _grind_side(p, s, k):
    stomp_leg(p, s, lo(k.joint), 24, 44, 1)
    brute_arm(p, s, lo(k.joint), 24, 26, 20, 48, 1)
    grind_torso_side(p, k)
    stomp_leg(p, s, k.joint, 32, 45, 0, boot=k.dark)
    p.poly(k.plate, [(28, 22), (40, 19), (44, 28), (34, 32)])
    for x0 in (31, 37):
        p.poly(k.dark, [(x0, 22), (x0 + 1, 15), (x0 + 4, 21)], shade=False)
    grind_hood_side(p, s, k, 0, 2)
    brute_arm(p, s, k.joint, 38, 26, 48, 44, 0, fist=k.dark)
    energy_can(p, k, 45, 33)


def _grind_front(p, s, k):
    legs_front(p, s, k.joint, 21, 46, 6, foot=k.dark)
    p.poly(k.steel, [(14, 34), (18, 22), (32, 19), (32, 48), (20, 48)])
    p.poly(lo(k.steel), [(22, 38), (32, 37), (32, 47), (23, 47)])
    m, p.mirror = p.mirror, False
    for x, grp, hand in ((11, 0, False), (52, 1, True)):
        dx, _ = _gait(s, grp)
        p.poly(k.joint, [(x - 3, 26), (x + 3, 26), (x + 5, 42 + dx), (x - 5, 42 + dx)])
        p.rect(k.dark, (x - 5, 42 + dx, x + 5, 49 + dx))
        if hand:
            energy_can(p, k, x - 2, 37 + dx)
    p.mirror = m
    p.poly(k.plate, [(4, 26), (10, 18), (22, 18), (24, 26), (12, 32)])
    for x0 in (9, 15):
        p.poly(k.dark, [(x0, 19), (x0 + 1, 11), (x0 + 4, 18)], shade=False)
    p.poly(k.steel, [(21, 16), (24, 6), (32, 4), (32, 28), (25, 26)])
    p.poly(k.jaw, [(24, 13), (32, 11), (32, 27), (27, 26)], shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 15, 2, 1) + sq(27, 16, 2, 1) + sq(29, 17, 2, 1))
    fangs(p, k, [27, 30], 21 if s.mouth else 20)
    if s.mouth:
        fangs(p, k, [28], 26, down=False)
    p.line(k.dark, [(21, 14), (24, 4), (32, 2)], width=2)
    p.ell(k.dark, (17, 13, 23, 20))
    p.px(k.hot, sq(19, 15, 3, 3))


def _grind_fq(p, s, k):
    stomp_leg(p, s, lo(k.joint), 22, 44, 1)
    brute_arm(p, s, lo(k.joint), 20, 26, 14, 48, 1)
    grind_torso_side(p, k, -2)
    stomp_leg(p, s, k.joint, 30, 46, 0, boot=k.dark)
    p.poly(k.steel, [(34, 16), (39, 6), (50, 4), (56, 10), (56, 24), (49, 29), (38, 27)])
    p.poly(k.jaw, [(38, 12), (54, 12), (53, 26), (47, 29), (39, 25)], shade=False)
    if k.eye:
        p.px(k.eye, sq(39, 15, 2, 1) + sq(41, 16, 2, 1) + sq(43, 17, 1, 1) + sq(47, 17, 1, 1) + sq(48, 16, 2, 1) + sq(50, 15, 2, 1))
    fangs(p, k, [41, 44, 47, 50], 21 if s.mouth else 20)
    if s.mouth:
        fangs(p, k, [43, 48], 26, down=False)
    p.line(k.dark, [(35, 18), (38, 7), (46, 3)], width=2)
    p.ell(k.dark, (32, 13, 38, 20))
    p.px(k.hot, sq(34, 15 + (s.t % 2), 3, 3))
    p.poly(k.plate, [(46, 22), (56, 20), (61, 28), (52, 32)])
    brute_arm(p, s, k.joint, 52, 28, 56, 46, 0, fist=k.dark)
    energy_can(p, k, 53, 35)
    p.poly(k.plate, [(22, 20), (34, 17), (38, 26), (27, 30)])
    for x0 in (25, 31):
        p.poly(k.dark, [(x0, 20), (x0 + 1, 13), (x0 + 4, 19)], shade=False)


def _grind_bq(p, s, k):
    brute_arm(p, s, lo(k.joint), 40, 26, 50, 44, 0)
    energy_can(p, k, 47, 33)
    stomp_leg(p, s, lo(k.joint), 32, 45, 0)
    grind_hood_side(p, s, k, -4, 2)
    p.poly(k.steel, [(8, 44), (10, 28), (22, 18), (38, 18), (46, 26), (42, 42), (30, 48), (14, 48)])
    p.poly(k.plate, [(10, 28), (22, 19), (36, 19), (30, 30), (14, 36)])
    for x0, y0 in ((11, 32), (14, 25), (19, 20), (26, 18)):
        p.poly(k.dark, [(x0, y0), (x0 - 6, y0 - 3), (x0 + 1, y0 - 6)], shade=False)
    p.poly(k.plate, [(14, 22), (26, 17), (30, 26), (18, 31)])
    stomp_leg(p, s, k.joint, 22, 46, 1, boot=k.dark)
    brute_arm(p, s, k.joint, 16, 28, 10, 48, 1, fist=k.dark)


def _grind_back(p, s, k):
    legs_front(p, s, k.joint, 21, 46, 6, foot=k.dark)
    m, p.mirror = p.mirror, False
    for x, grp in ((11, 0), (52, 1)):
        dx, _ = _gait(s, grp)
        p.poly(k.joint, [(x - 3, 26), (x + 3, 26), (x + 5, 42 + dx), (x - 5, 42 + dx)])
        p.rect(k.dark, (x - 5, 42 + dx, x + 5, 49 + dx))
    p.mirror = m
    p.poly(k.steel, [(14, 34), (18, 20), (32, 17), (32, 48), (20, 48)])
    p.poly(k.plate, [(18, 22), (32, 19), (32, 32), (20, 34)])
    for y0 in (28, 35, 42):
        p.poly(k.dark, [(28, y0), (32, y0 - 7), (32, y0)], shade=False)
    p.poly(k.plate, [(4, 26), (10, 18), (22, 18), (24, 26), (12, 32)])
    for x0 in (9, 15):
        p.poly(k.dark, [(x0, 19), (x0 + 1, 11), (x0 + 4, 18)], shade=False)
    p.poly(k.steel, [(21, 16), (24, 6), (32, 4), (32, 26), (25, 24)])
    p.poly(k.plate, [(25, 8), (32, 6), (32, 20), (27, 18)])
    p.line(k.dark, [(21, 14), (24, 4), (32, 2)], width=2)
    p.ell(k.dark, (17, 13, 23, 20))
    p.px(k.hot, sq(19, 15, 3, 3))


grindlord = design('grindlord', 'Gaming', 'slash', '#6A2FB0', views=ALL_VIEWS, size=64)(by_view({
    'kit': GrindlordKit, 'side': _grind_side, 'front': _grind_front, 'fq': _grind_fq,
    'bq': _grind_bq, 'back': _grind_back}))


# ================================================================ STREAMING
class StreamingKit(Kit):
    """Wine-slate dark kit for the Streaming line; accent is an ember orange
    (not red, so the red angry eyes still read)."""
    steel, plate, joint, dark = '#54474C', '#6E5C62', '#30272B', '#191315'
    accent, flare = '#FF7A1A', '#FFD2A8'


class BufferooKit(StreamingKit):
    pass


def roo_leg(p, s, col, x, y, grp, foot=None):
    """Kangaroo hind leg from hip (x, y): big thigh, shin raked back to the
    heel, long foot forward."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    hx = x - 7 + dx
    p.poly(col, [(x - 6, y - 5), (x + 5, y - 4), (x + 2, y + 7), (x - 7, y + 5)])
    p.line(col, [(x - 2, y + 5), (hx, g - 3)], width=4)
    p.poly(foot or col, [(hx - 2, g - 4), (hx + 11, g - 2), (hx + 12, g), (hx - 2, g)])


def load_ring(p, k, cx, cy, t):
    """Loading spinner: an 8-dot ring with a bright head chasing round."""
    ring = [(0, -3), (2, -2), (3, 0), (2, 2), (0, 3), (-2, 2), (-3, 0), (-2, -2)]
    for i, (dx, dy) in enumerate(ring):
        c = k.flare if (i - t) % 8 == 0 else (k.accent if (i - t) % 8 in (1, 7) else lo(lo(k.accent)))
        p.px(c, [(cx + dx, cy + dy)])


def roo_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(lo(k.plate), t([(44, 11), (43, 0), (48, 9)]))
    if s.mouth:
        p.poly(k.jaw, t([(50, 20), (60, 19), (58, 25), (50, 24)]), shade=False)
        fangs(p, k, [a + x for a in (52, 55)], 23 + y, down=False)
    p.poly(k.steel, t([(38, 16), (42, 9), (50, 9), (54, 14), (50, 21), (41, 21)]))
    p.poly(k.plate, t([(49, 13), (60, 16), (60, 19), (51, 21)]))
    p.poly(k.plate, t([(41, 11), (36, -2), (46, 9)]))
    p.px(k.accent, t(sq(37, 0)))
    p.rect(k.jaw, (43 + x, 12 + y, 51 + x, 14 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(46, 12, 2, 2) + sq(48, 13, 3, 2)))
    p.line(k.jaw, t([(51, 21), (59, 20)]))
    fangs(p, k, [a + x for a in (52, 56)], 21 + y)
    p.px(k.jaw, t(sq(59, 16)))


def _roo_body_side(p, s, k, x=0):
    def t(pts):
        return [(a + x, b) for a, b in pts]
    p.poly(k.steel, t([(16, 44), (18, 32), (26, 22), (38, 19), (44, 26), (42, 40), (34, 50), (22, 51)]))
    p.poly(k.plate, t([(18, 32), (26, 23), (32, 23), (24, 36)]))
    for x0, y0 in ((19, 31), (23, 26)):
        p.poly(k.dark, t([(x0, y0), (x0 - 5, y0 - 3), (x0 + 1, y0 - 5)]), shade=False)
    p.poly(lo(k.plate), t([(31, 34), (42, 32), (40, 44), (32, 48)]))
    load_ring(p, k, 36 + x, 40, s.t)


def _roo_side(p, s, k):
    p.poly(k.steel, [(22, 42), (27, 48), (8, 61), (1, 61), (1, 59)])
    p.px(k.accent, sq(3, 59))
    roo_leg(p, s, lo(k.joint), 22, 46, 1)
    claw_arm(p, s, lo(k.joint), 38, 28, 44, 40, 1)
    _roo_body_side(p, s, k)
    roo_leg(p, s, k.joint, 26, 47, 0, foot=k.dark)
    roo_head_side(p, s, k, 0, 6)
    claw_arm(p, s, k.joint, 40, 30, 48, 40, 0)


def _roo_front(p, s, k):
    m, p.mirror = p.mirror, False
    for x, grp in ((20, 0), (43, 1)):
        dx, lift = _gait(s, grp)
        g = p.ground - lift * 3
        p.poly(k.joint, [(x - 5, 42), (x + 5, 42), (x + 3, g - 3), (x - 3, g - 3)])
        p.poly(k.dark, [(x - 3, g - 4), (x + 3, g - 4), (x + 4, g), (x - 4, g)])
    p.mirror = m
    p.poly(k.steel, [(17, 44), (18, 32), (24, 24), (32, 23), (32, 52), (22, 51)])
    p.poly(lo(k.plate), [(24, 36), (32, 35), (32, 50), (25, 48)])
    m, p.mirror = p.mirror, False
    load_ring(p, k, 32, 42, s.t)
    p.mirror = m
    p.line(k.joint, [(20, 30), (22, 38), (26, 38)], width=3)
    p.px('#AEB8C6', [(27, 37), (27, 39)])
    p.poly(k.plate, [(25, 14), (21, -1), (29, 11)])
    p.px(k.accent, sq(21, 0))
    p.poly(k.steel, [(22, 16), (26, 9), (32, 9), (32, 28), (27, 27), (23, 22)])
    p.rect(k.jaw, (24, 14, 33, 16), shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 14, 2, 1) + sq(27, 15, 2, 1) + sq(29, 16, 2, 1))
    p.poly(k.plate, [(28, 18), (32, 17), (32, 29), (29, 27)])
    if s.mouth:
        p.poly(k.jaw, [(28, 25), (32, 25), (32, 32), (29, 31)], shade=False)
        fangs(p, k, [29], 25)
        fangs(p, k, [30], 31, down=False)
    else:
        p.px(k.jaw, sq(28, 27, 4, 1))
        fangs(p, k, [29], 28)


def _roo_fq(p, s, k):
    p.poly(k.steel, [(22, 42), (27, 48), (9, 61), (2, 61), (2, 59)])
    roo_leg(p, s, lo(k.joint), 22, 46, 1)
    claw_arm(p, s, lo(k.joint), 34, 30, 38, 42, 1)
    _roo_body_side(p, s, k, -1)
    roo_leg(p, s, k.joint, 28, 48, 0, foot=k.dark)
    y = 6
    p.poly(lo(k.plate), [(48, 10 + y), (54, -2 + y), (54, 11 + y)])
    p.poly(k.steel, [(37, 16 + y), (41, 9 + y), (50, 9 + y), (55, 14 + y), (52, 21 + y), (41, 22 + y)])
    p.poly(k.plate, [(40, 11 + y), (33, -2 + y), (44, 9 + y)])
    p.px(k.accent, sq(34, -1 + y) + sq(53, -1 + y))
    p.rect(k.jaw, (40, 12 + y, 54, 14 + y), shade=False)
    if k.eye:
        p.px(k.eye, sq(41, 12 + y, 2, 1) + sq(43, 13 + y, 2, 1) + sq(45, 14 + y, 1, 1) + sq(49, 14 + y, 1, 1) + sq(50, 13 + y, 2, 1) + sq(52, 12 + y, 2, 1))
    p.poly(k.plate, [(44, 16 + y), (54, 16 + y), (59, 19 + y), (54, 24 + y), (47, 24 + y)])
    if s.mouth:
        p.poly(k.jaw, [(46, 23 + y), (55, 23 + y), (54, 29 + y), (47, 28 + y)], shade=False)
        fangs(p, k, [47, 52], 23 + y)
        fangs(p, k, [49], 28 + y, down=False)
    else:
        p.line(k.jaw, [(47, 23 + y), (55, 23 + y)])
        fangs(p, k, [48, 52], 24 + y)
    claw_arm(p, s, k.joint, 40, 32, 46, 42, 0)


def _roo_bq(p, s, k):
    claw_arm(p, s, lo(k.joint), 40, 30, 48, 40, 0)
    roo_head_side(p, s, k, -2, 5)
    roo_leg(p, s, lo(k.joint), 30, 47, 0)
    p.poly(k.steel, [(12, 44), (14, 32), (22, 22), (36, 19), (42, 26), (40, 40), (32, 50), (18, 51)])
    p.poly(k.plate, [(14, 32), (22, 23), (34, 21), (26, 34), (16, 40)])
    for x0, y0 in ((15, 31), (19, 26), (25, 22)):
        p.poly(k.dark, [(x0, y0), (x0 - 5, y0 - 3), (x0 + 1, y0 - 5)], shade=False)
    roo_leg(p, s, k.joint, 20, 48, 1, foot=k.dark)
    p.poly(k.steel, [(16, 44), (20, 50), (6, 61), (0, 61), (0, 58)])
    p.px(k.accent, sq(2, 58))


def _roo_back(p, s, k):
    m, p.mirror = p.mirror, False
    for x, grp in ((20, 0), (43, 1)):
        dx, lift = _gait(s, grp)
        g = p.ground - lift * 3
        p.poly(k.joint, [(x - 5, 42), (x + 5, 42), (x + 3, g - 3), (x - 3, g - 3)])
        p.poly(k.dark, [(x - 3, g - 4), (x + 3, g - 4), (x + 4, g), (x - 4, g)])
    p.mirror = m
    p.poly(k.plate, [(25, 14), (21, -1), (29, 11)])
    p.px(k.accent, sq(21, 0))
    p.poly(k.steel, [(22, 16), (26, 9), (32, 9), (32, 26), (27, 25), (23, 22)])
    p.px(lo(k.steel), sq(28, 12, 2, 10))
    p.poly(k.steel, [(17, 44), (18, 32), (24, 24), (32, 23), (32, 52), (22, 51)])
    p.poly(k.plate, [(19, 32), (24, 25), (32, 24), (32, 34), (21, 36)])
    for y0 in (30, 37):
        p.poly(k.dark, [(28, y0), (32, y0 - 7), (32, y0)], shade=False)
    p.poly(k.steel, [(27, 44), (32, 42), (32, 61), (29, 61)])
    p.px(k.accent, sq(31, 58))


bufferoo = design('bufferoo', 'Streaming', 'play', '#E53935', views=ALL_VIEWS, size=64)(by_view({
    'kit': BufferooKit, 'side': _roo_side, 'front': _roo_front, 'fq': _roo_fq,
    'bq': _roo_bq, 'back': _roo_back}))


class StreamletKit(StreamingKit):
    pass


def _fish_wave(s):
    return [0, 1, 0, -1][s.step % 4] if s.step else [0, 0, 1, 1, 0, 0, -1, -1][s.t % 8]


def play_fin(p, k, x, y, h=12):
    """Dorsal fin shaped like a play button, glowing edge, base at (x, y)."""
    p.poly(k.plate, [(x, y), (x, y - h), (x + h * 3 // 4, y - h // 2)])
    p.line(k.accent, [(x + 2, y - 3), (x + 2, y - h + 3), (x + h * 3 // 4 - 3, y - h // 2), (x + 2, y - 3)], sep=False)


def fish_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    if s.mouth:
        p.poly(k.jaw, t([(48, 36), (62, 34), (60, 44), (48, 42)]), shade=False)
        p.poly(k.steel, t([(46, 42), (60, 44), (62, 47), (48, 47)]))
        fangs(p, k, [a + x for a in (50, 53, 56, 59)], 43 + y, down=False)
    else:
        p.poly(k.steel, t([(46, 38), (61, 39), (62, 43), (48, 44)]))
        fangs(p, k, [a + x for a in (52, 56)], 36 + y, down=False, h=3)
    p.poly(k.steel, t([(42, 30), (50, 26), (60, 30), (62, 35), (52, 37), (44, 38)]))
    p.poly(k.plate, t([(50, 27), (60, 30), (56, 32), (48, 30)]))
    p.rect(k.jaw, (48 + x, 30 + y, 56 + x, 32 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(50, 30, 3, 2) + sq(53, 31, 3, 2)))
    fangs(p, k, [a + x for a in (51, 54, 57)], 37 + y)


def _streamlet_side(p, s, k):
    w = _fish_wave(s)
    p.poly(k.joint, [(12, 34 + w), (1, 22 + w), (5, 34 + w), (1, 46 + w)])
    p.px(k.accent, [(1, 22 + w), (2, 23 + w), (1, 46 + w), (2, 45 + w)])
    p.poly(k.dark, [(30, 42), (24, 52), (34, 45)])
    play_fin(p, k, 22, 29, 14)
    p.poly(k.steel, [(10, 35 + w), (18, 29), (34, 26), (46, 29), (50, 36), (44, 43), (28, 46), (14, 41 + w)])
    p.poly(lo(k.steel), [(18, 40), (44, 40), (40, 45), (24, 46)])
    for x0 in (20, 27, 34):
        p.poly(k.plate, [(x0, 29), (x0 + 6, 28), (x0 + 5, 36), (x0 - 1, 36)])
    p.px(k.accent, sq(22, 41, 2, 1) + sq(28, 42, 2, 1) + sq(34, 42, 2, 1))
    p.px(k.hot, sq(22 + 6 * (s.t % 3), 41 + (s.t % 3 > 0), 2, 1))
    p.poly(k.joint, [(36, 40), (32, 52), (42, 44)])
    fish_head_side(p, s, k)


def _streamlet_front(p, s, k):
    w = _fish_wave(s)
    p.poly(k.plate, [(29, 26), (32, 12), (32, 26)])
    p.px(k.accent, [(31, 16), (31, 17), (31, 18)])
    p.poly(k.joint, [(22, 40), (8, 48 + w), (20, 45)])
    p.px(k.accent, sq(8, 47 + w))
    p.poly(k.steel, [(20, 34), (24, 26), (32, 24), (32, 50), (25, 48), (20, 42)])
    p.rect(k.jaw, (23, 30, 33, 32), shade=False)
    if k.eye:
        p.px(k.eye, sq(24, 30, 2, 1) + sq(26, 31, 2, 1) + sq(28, 32, 2, 1))
    if s.mouth:
        p.poly(k.jaw, [(23, 36), (32, 35), (32, 47), (25, 45)], shade=False)
        fangs(p, k, [25, 29], 36, h=3)
        fangs(p, k, [27], 46, down=False, h=3)
    else:
        p.poly(k.jaw, [(24, 37), (32, 36), (32, 40), (25, 40)], shade=False)
        fangs(p, k, [26, 30], 36, h=3)


def _streamlet_fq(p, s, k):
    w = _fish_wave(s)
    p.poly(k.joint, [(14, 34 + w), (4, 24 + w), (8, 34 + w), (5, 44 + w)])
    p.px(k.accent, [(4, 24 + w), (5, 44 + w)])
    play_fin(p, k, 24, 28, 13)
    p.poly(k.steel, [(12, 35 + w), (20, 29), (34, 26), (46, 29), (50, 37), (44, 45), (28, 47), (16, 41 + w)])
    for x0 in (22, 29):
        p.poly(k.plate, [(x0, 29), (x0 + 6, 28), (x0 + 5, 36), (x0 - 1, 36)])
    p.px(k.accent, sq(24, 42, 2, 1) + sq(30, 43, 2, 1))
    p.poly(k.joint, [(38, 41), (33, 53), (44, 45)])
    p.poly(k.steel, [(40, 30), (47, 25), (57, 27), (61, 34), (56, 42), (44, 42)])
    p.rect(k.jaw, (42, 30, 58, 32), shade=False)
    if k.eye:
        p.px(k.eye, sq(43, 30, 2, 1) + sq(45, 31, 2, 1) + sq(47, 32, 1, 1) + sq(52, 32, 1, 1) + sq(53, 31, 2, 1) + sq(55, 30, 2, 1))
    if s.mouth:
        p.poly(k.jaw, [(44, 35), (60, 35), (58, 47), (46, 46)], shade=False)
        fangs(p, k, [46, 50, 54], 35)
        fangs(p, k, [48, 52, 56], 45, down=False)
    else:
        p.poly(k.jaw, [(45, 36), (59, 36), (58, 40), (46, 40)], shade=False)
        fangs(p, k, [46, 50, 54], 36)
        fangs(p, k, [48, 52, 56], 40, down=False)
    p.poly(k.joint, [(56, 38), (62, 46), (58, 44)])


def _streamlet_bq(p, s, k):
    w = _fish_wave(s)
    p.poly(k.joint, [(44, 38), (50, 50), (46, 42)])
    fish_head_side(p, s, k, -4, 0)
    play_fin(p, k, 22, 29, 14)
    p.poly(k.steel, [(12, 35 + w), (20, 28), (34, 26), (46, 30), (48, 38), (42, 45), (26, 47), (14, 42 + w)])
    for x0 in (18, 25, 32, 39):
        p.poly(k.plate, [(x0, 29), (x0 + 6, 28), (x0 + 5, 37), (x0 - 1, 37)])
    p.poly(k.joint, [(28, 42), (20, 54), (32, 46)])
    p.poly(k.joint, [(14, 35 + w), (2, 20 + w), (8, 36 + w), (3, 50 + w)])
    p.px(k.accent, [(2, 20 + w), (3, 21 + w), (3, 50 + w), (4, 49 + w)])


def _streamlet_back(p, s, k):
    w = _fish_wave(s)
    p.poly(k.plate, [(29, 26), (32, 12), (32, 26)])
    p.px(k.accent, [(31, 16), (31, 17), (31, 18)])
    p.poly(k.joint, [(22, 40), (8, 48 + w), (20, 45)])
    p.poly(k.steel, [(20, 34), (24, 26), (32, 24), (32, 48), (25, 46), (20, 42)])
    p.poly(k.plate, [(24, 27), (32, 25), (32, 34), (25, 34)])
    m, p.mirror = p.mirror, False
    p.poly(k.joint, [(30, 36), (24 + w, 22), (29 + w, 36), (24 - w, 52), (34, 38), (40 - w, 52), (35 + w, 36), (40 + w, 22), (34, 36)])
    p.px(k.accent, [(24 + w, 22), (40 + w, 22), (24 - w, 52), (40 - w, 52)])
    p.mirror = m


streamlet = design('streamlet', 'Streaming', 'play', '#E0312B', float=True, views=ALL_VIEWS, size=64)(by_view({
    'kit': StreamletKit, 'side': _streamlet_side, 'front': _streamlet_front, 'fq': _streamlet_fq,
    'bq': _streamlet_bq, 'back': _streamlet_back}))


class BingewyrmKit(StreamingKit):
    screen = '#0E1A22'


def screen_panel(p, k, x, y, i, s, w=5, h=4):
    """A little glowing screen set into the hood; the picture flickers."""
    p.rect(k.screen, (x, y, x + w - 1, y + h - 1), shade=False)
    lit = (s.t + i) % 3
    p.px(k.hot if lit == 0 else k.accent, [(x + 1 + lit, y + 1), (x + 2 + lit % 2, y + 2)])


def wyrm_coil(p, s, k, x0=4, x1=58):
    """The body coiled on the ground in two stacked loops; the scale
    ridges roll with the step."""
    sh = s.step % 4
    for (a, b), top, bot in (((x0, x1), 52, 61), ((x0 + 7, x1 - 7), 44, 53)):
        p.poly(k.steel, [(a, bot), (a, top + 4), (a + 4, top), (b - 4, top), (b, top + 4), (b, bot)])
        p.line(lo(k.plate), [(a + 3, bot - 1), (b - 3, bot - 1)], width=2)
        for xx in range(a + 5 + sh, b - 3, 6):
            p.line(k.joint, [(xx, top + 1), (xx - 2, bot - 3)])
    p.px(k.accent, [(x0 + 10 + sh, 58), (x0 + 11 + sh, 58), (x1 - 12 + sh, 58), (x1 - 11 + sh, 58)])


def wyrm_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    if s.mouth:
        p.poly(k.jaw, t([(48, 18), (61, 17), (59, 24), (48, 22)]), shade=False)
        fangs(p, k, [a + x for a in (50, 54)], 21 + y, down=False)
    p.poly(k.steel, t([(40, 14), (45, 7), (54, 8), (62, 13), (60, 18), (44, 20)]))
    p.poly(k.plate, t([(46, 8), (54, 8), (61, 13), (52, 12)]))
    p.rect(k.jaw, (46 + x, 12 + y, 54 + x, 14 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(48, 12, 3, 2) + sq(51, 13, 3, 2)))
    p.line(k.jaw, t([(50, 19), (60, 18)]))
    fangs(p, k, [a + x for a in (51, 57)], 19 + y, h=3)


def _wyrm_side(p, s, k):
    p.poly(k.plate, [(8, 56), (0, 48), (5, 58)])
    p.px(k.accent, sq(0, 48))
    wyrm_coil(p, s, k)
    p.poly(k.steel, [(26, 50), (28, 34), (34, 22), (44, 17), (48, 24), (42, 36), (42, 50)])
    p.poly(lo(k.plate), [(40, 36), (46, 24), (48, 26), (43, 38), (42, 50), (38, 50)])
    for yy in (28, 33, 38, 43):
        p.line(k.joint, [(40, yy + 2), (46, yy - 2)])
    p.poly(k.joint, [(28, 8), (36, 2), (42, 8), (44, 26), (36, 36), (26, 24)])
    for i, (x, y) in enumerate(((31, 10), (30, 18), (35, 24))):
        screen_panel(p, k, x, y, i, s)
    wyrm_head_side(p, s, k, 0, 2)


def _wyrm_front(p, s, k):
    wyrm_coil(p, s, k, 2, 62)
    p.poly(k.joint, [(8, 12), (14, 4), (26, 2), (32, 4), (32, 40), (20, 38), (10, 28)])
    for i, (x, y) in enumerate(((12, 12), (18, 7), (13, 20), (20, 27))):
        screen_panel(p, k, x, y, i, s)
    p.poly(k.steel, [(26, 50), (26, 30), (32, 26), (32, 50)])
    p.poly(lo(k.plate), [(29, 30), (32, 28), (32, 50), (29, 50)])
    for yy in (34, 39, 44):
        p.line(k.joint, [(29, yy), (32, yy)])
    p.poly(k.steel, [(23, 14), (27, 7), (32, 6), (32, 26), (27, 24), (24, 19)])
    p.rect(k.jaw, (24, 12, 33, 14), shade=False)
    if k.eye:
        p.px(k.eye, sq(25, 12, 2, 1) + sq(27, 13, 2, 1) + sq(29, 14, 2, 1))
    p.poly(k.plate, [(28, 16), (32, 15), (32, 26), (29, 24)])
    if s.mouth:
        p.poly(k.jaw, [(27, 22), (32, 22), (32, 30), (28, 29)], shade=False)
        fangs(p, k, [28], 22, h=3)
        fangs(p, k, [30], 29, down=False)
    else:
        p.px(k.jaw, sq(27, 24, 5, 1))
        fangs(p, k, [28], 25, h=3)


def _wyrm_fq(p, s, k):
    p.poly(k.plate, [(8, 56), (0, 48), (5, 58)])
    wyrm_coil(p, s, k)
    p.poly(k.steel, [(26, 50), (28, 34), (34, 24), (42, 20), (46, 26), (42, 36), (42, 50)])
    p.poly(k.joint, [(24, 10), (34, 2), (48, 2), (58, 10), (56, 30), (44, 38), (30, 34), (22, 22)])
    for i, (x, y) in enumerate(((27, 12), (26, 21), (32, 28), (50, 11), (50, 20), (44, 28))):
        screen_panel(p, k, x, y, i, s)
    p.poly(k.steel, [(34, 14), (38, 8), (48, 7), (53, 14), (50, 22), (38, 22)])
    p.rect(k.jaw, (36, 12, 52, 14), shade=False)
    if k.eye:
        p.px(k.eye, sq(37, 12, 2, 1) + sq(39, 13, 2, 1) + sq(41, 14, 1, 1) + sq(45, 14, 1, 1) + sq(46, 13, 2, 1) + sq(48, 12, 2, 1))
    p.poly(k.plate, [(40, 16), (50, 16), (55, 19), (50, 24), (42, 24)])
    if s.mouth:
        p.poly(k.jaw, [(41, 23), (51, 23), (50, 30), (42, 29)], shade=False)
        fangs(p, k, [42, 48], 23, h=3)
        fangs(p, k, [45], 28, down=False)
    else:
        p.line(k.jaw, [(42, 23), (51, 23)])
        fangs(p, k, [43, 48], 24, h=3)


def _wyrm_bq(p, s, k):
    wyrm_head_side(p, s, k, -4, 4)
    p.poly(k.steel, [(24, 50), (26, 34), (32, 22), (42, 18), (46, 26), (40, 36), (40, 50)])
    p.poly(k.joint, [(26, 8), (34, 2), (44, 6), (46, 28), (36, 36), (24, 24)])
    p.poly(k.plate, [(30, 10), (34, 5), (40, 8), (41, 26), (34, 30), (29, 22)])
    for yy in (12, 18, 24):
        p.poly(k.dark, [(33, yy + 3), (36, yy - 3), (38, yy + 3)], shade=False)
    wyrm_coil(p, s, k)
    p.poly(k.plate, [(6, 58), (0, 49), (4, 60)])
    p.px(k.accent, sq(0, 49))


def _wyrm_back(p, s, k):
    p.poly(k.steel, [(26, 50), (26, 30), (32, 26), (32, 50)])
    p.poly(k.joint, [(8, 12), (14, 4), (26, 2), (32, 4), (32, 40), (20, 38), (10, 28)])
    p.poly(k.plate, [(12, 13), (16, 7), (26, 5), (32, 7), (32, 36), (21, 34), (13, 26)])
    for y0 in (14, 22, 30):
        p.poly(k.dark, [(28, y0), (32, y0 - 7), (32, y0)], shade=False)
    wyrm_coil(p, s, k, 2, 62)
    m, p.mirror = p.mirror, False
    p.poly(k.plate, [(44, 56), (54, 48), (50, 58)])
    p.px(k.accent, sq(53, 48))
    p.mirror = m


bingewyrm = design('bingewyrm', 'Streaming', 'play', '#9C1C28', views=ALL_VIEWS, size=64)(by_view({
    'kit': BingewyrmKit, 'side': _wyrm_side, 'front': _wyrm_front, 'fq': _wyrm_fq,
    'bq': _wyrm_bq, 'back': _wyrm_back}))


# ================================================================ FLYING
class FlyingKit(Kit):
    """Storm-blue dark kit for the Flying line; accent is an ice blue."""
    steel, plate, joint, dark = '#454F63', '#5E6A82', '#283042', '#131822'
    accent, flare = '#7FC4FF', '#DDF1FF'


class ZephyrletKit(FlyingKit):
    pass


def bolt(p, k, x, y, t, h=12):
    """Lightning fork hanging from (x, y); flickers on alternate frames."""
    if t % 3 == 2:
        return
    j = 2 if t % 2 else -2
    p.line(k.flare, [(x, y), (x + j, y + h // 3), (x - j, y + 2 * h // 3), (x + j // 2, y + h)], sep=False)
    p.px(k.accent, [(x + 1, y + 1), (x + j + 1, y + h // 3)])


def cloud_lumps(p, k, lumps, base, dy=0):
    """Storm cloud: puffs ((x0, y0, x1, y1) ellipses, back to front) over a
    flat, darker underside `base` (x0, x1, y)."""
    for x0, y0, x1, y1 in lumps:
        p.ell(k.steel, (x0, y0 + dy, x1, y1 + dy))
    bx0, bx1, by = base
    p.poly(k.joint, [(bx0, by - 4), (bx1, by - 4), (bx1 - 2, by), (bx0 + 2, by)])
    p.px(lo(k.accent), [(x, by + 2 + (x * 3) % 4) for x in range(bx0 + 4, bx1 - 2, 5)])


ZEPH_SIDE = [(4, 30, 18, 44), (30, 22, 46, 38), (10, 20, 28, 38), (20, 14, 38, 34), (6, 32, 44, 46)]
ZEPH_FRONT = [(8, 28, 22, 42), (14, 18, 30, 34), (22, 12, 42, 30), (8, 32, 32, 46)]


def zeph_face(p, s, k, x, y, front=False):
    """Slit visor and jagged mouth cut into the leading puff."""
    if front:
        p.rect(k.jaw, (x, y, x + 9, y + 2), shade=False)
        if k.eye:
            p.px(k.eye, sq(x + 1, y, 2, 1) + sq(x + 3, y + 1, 2, 1) + sq(x + 5, y + 2, 2, 1))
        if s.mouth:
            p.poly(k.jaw, [(x + 3, y + 5), (x + 9, y + 5), (x + 9, y + 11), (x + 4, y + 10)], shade=False)
            fangs(p, k, [x + 5], y + 5)
        else:
            p.px(k.jaw, sq(x + 3, y + 6, 6, 1))
            fangs(p, k, [x + 4, x + 7], y + 7)
        return
    p.rect(k.jaw, (x, y, x + 9, y + 2), shade=False)
    if k.eye:
        p.px(k.eye, sq(x + 2, y, 3, 2) + sq(x + 5, y + 1, 3, 2))
    if s.mouth:
        p.poly(k.jaw, [(x + 2, y + 5), (x + 11, y + 5), (x + 9, y + 10), (x + 2, y + 9)], shade=False)
        fangs(p, k, [x + 4, x + 7], y + 5)
    else:
        p.line(k.jaw, [(x + 2, y + 6), (x + 10, y + 5)])
        fangs(p, k, [x + 4, x + 7], y + 6)


def _zeph_side(p, s, k):
    wy = wing_y(s)
    bolt(p, k, 30, 48, s.t, 12)
    p.poly(k.plate, [(14, 28), (4, 16 + wy), (10, 16 + wy), (18, 26)])
    cloud_lumps(p, k, ZEPH_SIDE, (4, 46, 46))
    p.poly(k.steel, [(40, 28), (46, 24), (54, 26), (59, 32), (56, 40), (42, 42)])
    p.poly(k.plate, [(46, 25), (54, 26), (58, 31), (50, 30)])
    zeph_face(p, s, k, 46, 30)
    for x0, y0 in ((26, 16), (34, 24)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 2, y0 - 5), (x0 + 4, y0 + 1)], shade=False)
    p.poly(k.plate, [(22, 30), (12, 18 + wy), (26, 24 + wy), (28, 32)])
    p.px(k.accent, sq(12, 18 + wy))


def _zeph_front(p, s, k):
    wy = wing_y(s)
    m, p.mirror = p.mirror, False
    bolt(p, k, 28, 48, s.t, 12)
    p.mirror = m
    p.poly(k.plate, [(16, 30), (4, 16 + wy), (8, 24 + wy), (14, 36)])
    p.px(k.accent, sq(4, 16 + wy))
    cloud_lumps(p, k, ZEPH_FRONT, (8, 32, 46))
    p.poly(k.steel, [(22, 30), (25, 25), (32, 24), (32, 42), (25, 41), (22, 36)])
    zeph_face(p, s, k, 24, 29, front=True)
    p.poly(k.dark, [(26, 18), (28, 11), (30, 18)], shade=False)


def _zeph_fq(p, s, k):
    wy = wing_y(s)
    bolt(p, k, 28, 48, s.t, 12)
    p.poly(k.plate, [(22, 24), (10, 10 + wy), (18, 11 + wy), (28, 22)])
    cloud_lumps(p, k, [(2, 30, 16, 44), (28, 22, 44, 38), (8, 20, 26, 38), (18, 14, 36, 34), (4, 32, 42, 46)], (4, 42, 46))
    for x0, y0 in ((22, 19), (30, 25)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 2, y0 - 5), (x0 + 4, y0 + 1)], shade=False)
    p.poly(k.steel, [(36, 30), (41, 24), (52, 24), (58, 31), (54, 42), (40, 42)])
    p.rect(k.jaw, (39, 29, 56, 31), shade=False)
    if k.eye:
        p.px(k.eye, sq(40, 29, 2, 1) + sq(42, 30, 2, 1) + sq(44, 31, 1, 1) + sq(50, 31, 1, 1) + sq(51, 30, 2, 1) + sq(53, 29, 2, 1))
    if s.mouth:
        p.poly(k.jaw, [(42, 35), (53, 35), (52, 41), (43, 40)], shade=False)
        fangs(p, k, [44, 49], 35)
    else:
        p.line(k.jaw, [(42, 36), (53, 36)])
        fangs(p, k, [44, 47, 50], 37)
    p.poly(k.plate, [(40, 27), (49, 11 + wy), (55, 13 + wy), (48, 27)])
    p.px(k.accent, sq(49, 11 + wy))


def _zeph_bq(p, s, k):
    wy = wing_y(s)
    bolt(p, k, 26, 48, s.t, 12)
    p.poly(k.plate, [(44, 28), (52, 14 + wy), (56, 16 + wy), (50, 30)])
    p.poly(k.steel, [(40, 30), (46, 26), (56, 28), (59, 34), (53, 40), (42, 40)])
    p.rect(k.jaw, (53, 31, 58, 33), shade=False)
    if k.eye:
        p.px(k.eye, sq(55, 31, 3, 2))
    cloud_lumps(p, k, [(30, 22, 46, 38), (2, 30, 18, 46), (8, 20, 28, 38), (18, 12, 38, 34), (4, 32, 44, 48)], (4, 44, 48))
    for x0, y0 in ((14, 22), (24, 17), (34, 23)):
        p.poly(k.dark, [(x0, y0 + 1), (x0 + 2, y0 - 5), (x0 + 4, y0 + 1)], shade=False)
    p.poly(k.plate, [(24, 26), (10, 10 + wy), (20, 12 + wy), (30, 24)])
    p.px(k.accent, sq(10, 10 + wy))


def _zeph_back(p, s, k):
    wy = wing_y(s)
    m, p.mirror = p.mirror, False
    bolt(p, k, 36, 48, s.t, 12)
    p.mirror = m
    p.poly(k.plate, [(16, 30), (4, 16 + wy), (8, 24 + wy), (14, 36)])
    p.px(k.accent, sq(4, 16 + wy))
    cloud_lumps(p, k, ZEPH_FRONT, (8, 32, 46))
    p.poly(k.plate, [(22, 30), (25, 24), (32, 23), (32, 38), (24, 38)])
    for y0 in (22, 30, 38):
        p.poly(k.dark, [(29, y0), (32, y0 - 6), (32, y0)], shade=False)


zephyrlet = design('zephyrlet', 'Flying', 'wind', '#9FD8FF', float=True, views=ALL_VIEWS, size=64)(by_view({
    'kit': ZephyrletKit, 'side': _zeph_side, 'front': _zeph_front, 'fq': _zeph_fq,
    'bq': _zeph_bq, 'back': _zeph_back}))


class AirstreamKit(FlyingKit):
    pass


def blade_wing(p, k, root, tip, back, wy, far=False, n=3):
    """Swept wing: a plated leading edge from root to tip and jagged
    primaries raking back to `back`."""
    (rx, ry), (tx, ty), (bx, by) = root, (tip[0], tip[1] + wy), back
    pts = [(rx, ry), (tx, ty)]
    for i in range(1, n + 1):
        fx = tx + (bx - tx) * i // (n + 1)
        fy = ty + (by - ty) * i // (n + 1)
        pts += [(fx + 2, fy + 4), (fx, fy + 2)] if i < n else [(fx + 2, fy + 4)]
    pts.append((bx, by))
    p.poly(k.dark if far else k.joint, pts)
    p.line(lo(k.plate) if far else k.plate, [(rx, ry), (tx, ty)], width=2)
    if not far:
        p.px(k.accent, sq(tx, ty))


def swift_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    if s.mouth:
        p.poly(k.dark, t([(54, 24), (63, 26), (56, 28)]))
        p.poly(k.jaw, t([(55, 28), (61, 28), (58, 31)]), shade=False)
        p.poly(k.dark, t([(54, 31), (60, 32), (55, 33)]))
    else:
        p.poly(k.dark, t([(54, 25), (63, 28), (60, 31), (55, 31)]))
        p.px(k.teeth, t([(57, 30), (59, 30)]))
    p.poly(k.steel, t([(44, 27), (48, 22), (55, 22), (58, 26), (55, 31), (46, 32)]))
    p.poly(k.plate, t([(47, 23), (40, 18), (52, 22)]))
    p.rect(k.jaw, (48 + x, 25 + y, 56 + x, 26 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(50, 25, 3, 1) + sq(52, 26, 4, 1)))


def _swift_body_side(p, s, k, x=0):
    def t(pts):
        return [(a + x, b) for a, b in pts]
    p.poly(k.joint, t([(18, 33), (2, 22), (10, 32)]))
    p.poly(k.joint, t([(18, 36), (2, 46), (10, 36)]))
    p.px(k.accent, t([(2, 22), (3, 23), (2, 46), (3, 45)]))
    p.poly(k.steel, t([(14, 34), (22, 28), (40, 27), (48, 30), (46, 37), (30, 40), (18, 38)]))
    p.poly(lo(k.plate), t([(24, 36), (44, 34), (40, 39), (28, 40)]))
    p.px('#AEB8C6', t([(32, 41), (34, 41), (36, 41)]))


def _swift_side(p, s, k):
    wy = wing_y(s) * 3
    blade_wing(p, k, (30, 29), (12, 6), (22, 28), wy, far=True)
    _swift_body_side(p, s, k)
    swift_head_side(p, s, k)
    blade_wing(p, k, (34, 30), (16, 8), (22, 32), wy)


def _swift_front(p, s, k):
    wy = wing_y(s) * 3
    blade_wing(p, k, (26, 30), (2, 16), (22, 38), wy)
    p.poly(k.joint, [(28, 42), (24, 54), (30, 46)])
    p.poly(k.steel, [(23, 32), (26, 26), (32, 25), (32, 46), (27, 44)])
    p.poly(lo(k.plate), [(28, 34), (32, 33), (32, 45), (29, 43)])
    p.poly(k.plate, [(26, 21), (18, 14), (28, 18)])
    p.poly(k.steel, [(24, 22), (27, 17), (32, 16), (32, 30), (26, 28)])
    p.rect(k.jaw, (25, 20, 33, 21), shade=False)
    if k.eye:
        p.px(k.eye, sq(26, 20, 2, 1) + sq(28, 21, 2, 1) + sq(30, 22, 2, 1))
    if s.mouth:
        p.poly(k.dark, [(29, 23), (32, 23), (32, 26)])
        p.poly(k.jaw, [(30, 26), (32, 26), (32, 29)], shade=False)
        p.poly(k.dark, [(30, 29), (32, 29), (32, 32)])
    else:
        p.poly(k.dark, [(29, 23), (32, 23), (32, 31)])


def _swift_fq(p, s, k):
    wy = wing_y(s) * 3
    blade_wing(p, k, (30, 29), (12, 6), (22, 28), wy, far=True)
    _swift_body_side(p, s, k, -2)
    p.poly(k.plate, [(46, 23), (38, 16), (50, 21)])
    p.poly(k.plate, [(52, 21), (60, 15), (56, 23)])
    p.poly(k.steel, [(42, 27), (46, 21), (55, 21), (59, 26), (55, 32), (45, 32)])
    p.rect(k.jaw, (44, 24, 58, 26), shade=False)
    if k.eye:
        p.px(k.eye, sq(45, 24, 2, 1) + sq(47, 25, 2, 1) + sq(49, 26, 1, 1) + sq(53, 26, 1, 1) + sq(54, 25, 2, 1) + sq(56, 24, 2, 1))
    if s.mouth:
        p.poly(k.dark, [(49, 27), (60, 28), (52, 30)])
        p.poly(k.jaw, [(51, 30), (58, 30), (55, 33)], shade=False)
        p.poly(k.dark, [(51, 33), (57, 33), (52, 35)])
    else:
        p.poly(k.dark, [(49, 27), (61, 29), (57, 33), (51, 32)])
    blade_wing(p, k, (34, 31), (18, 8), (24, 33), wy)


def _swift_bq(p, s, k):
    wy = wing_y(s) * 3
    blade_wing(p, k, (38, 29), (58, 8), (46, 28), wy, far=True)
    swift_head_side(p, s, k, -4, -1)
    p.poly(k.steel, [(12, 34), (20, 27), (38, 26), (46, 30), (44, 37), (28, 41), (16, 39)])
    p.poly(k.plate, [(20, 28), (36, 27), (34, 32), (22, 33)])
    p.poly(k.joint, [(16, 33), (0, 20), (8, 32)])
    p.poly(k.joint, [(16, 36), (0, 48), (8, 36)])
    p.px(k.accent, [(0, 20), (1, 21), (0, 48), (1, 47)])
    blade_wing(p, k, (28, 30), (8, 6), (16, 32), wy)


def _swift_back(p, s, k):
    wy = wing_y(s) * 3
    p.poly(k.steel, [(24, 22), (27, 17), (32, 16), (32, 30), (26, 28)])
    blade_wing(p, k, (26, 30), (2, 16), (22, 38), wy)
    p.poly(k.steel, [(23, 32), (26, 26), (32, 25), (32, 46), (27, 44)])
    p.poly(k.plate, [(26, 27), (32, 26), (32, 38), (27, 36)])
    m, p.mirror = p.mirror, False
    p.poly(k.joint, [(31, 44), (22, 58), (29, 48), (32, 50), (35, 48), (42, 58), (33, 44)])
    p.px(k.accent, [(22, 58), (42, 58)])
    p.mirror = m


airstream = design('airstream', 'Flying', 'wind', '#3F8FE0', float=True, views=ALL_VIEWS, size=64)(by_view({
    'kit': AirstreamKit, 'side': _swift_side, 'front': _swift_front, 'fq': _swift_fq,
    'bq': _swift_bq, 'back': _swift_back}))


class StratolordKit(FlyingKit):
    talon = '#C9D1DC'


def raptor_leg(p, s, k, col, x, y, grp):
    """Feathered thigh, scaled shin and a big taloned foot from hip (x, y)."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    fx = x + dx
    jx, jy = x - 3 + dx // 2, y + (g - y) * 2 // 5
    p.poly(col, [(x - 5, y - 4), (x + 5, y - 3), (jx + 3, jy + 1), (jx - 3, jy)])
    p.line(col, [(jx, jy), (fx, g - 2)], width=3)
    p.line(col, [(fx - 4, g), (fx + 5, g)], width=2)
    p.px(k.talon, [(fx + 6, g), (fx + 6, g - 1), (fx - 5, g), (fx + 2, g)])


def feather_wing(p, k, top, bot, rows=3, wy=0, far=False):
    """Folded wing: stacked plated feather rows from the shoulder `top`
    (x, y) raking back to the tips at `bot` (x, y), jagged ends."""
    (tx, ty), (bx, by) = top, bot
    ty += wy
    for r in range(rows):
        f = r / rows
        x0, y0 = tx - 2, ty + round((by - ty) * f * 0.5)
        col = lo(k.plate) if (r % 2) ^ far else k.plate
        if far:
            col = lo(col)
        p.poly(col, [(x0 + 4, y0), (tx + 6, y0 + 6), (bx + 8 + r * 3, by - 4 + r * 2), (bx + r * 3, by + r * 2), (x0 - 6, y0 + 10)])
    p.px(k.accent, sq(bx + (rows - 1) * 3, by + (rows - 1) * 2 - 1))


def strato_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    for tip in ((30, 2), (34, -1), (39, 1)):
        p.poly(k.plate, t([(42, 12), tip, (46, 9)]))
    p.px(k.accent, t(sq(34, -1)))
    if s.mouth:
        p.poly(k.dark, t([(52, 9), (63, 13), (61, 17), (54, 15)]))
        p.poly(k.jaw, t([(53, 15), (60, 17), (57, 20), (53, 19)]), shade=False)
        p.poly(k.dark, t([(52, 19), (59, 20), (54, 22)]))
    else:
        p.poly(k.dark, t([(52, 9), (63, 13), (62, 19), (58, 17), (53, 19)]))
        p.px(k.talon, t([(55, 18), (57, 18)]))
    p.poly(k.steel, t([(38, 14), (42, 7), (50, 6), (55, 11), (52, 19), (42, 20)]))
    p.poly(k.plate, t([(42, 7), (50, 6), (54, 10), (46, 11)]))
    p.rect(k.jaw, (45 + x, 11 + y, 53 + x, 12 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(47, 11, 3, 1) + sq(49, 12, 4, 1)))


def _strato_side(p, s, k):
    wy = wing_y(s)
    p.poly(k.joint, [(18, 40), (2, 34), (6, 40), (1, 46), (6, 48), (18, 45)])
    p.px(k.accent, [(2, 34), (3, 34), (1, 46), (2, 46)])
    raptor_leg(p, s, k, lo(k.joint), 28, 46, 1)
    p.poly(k.steel, [(14, 42), (20, 30), (30, 22), (42, 20), (48, 28), (46, 40), (36, 48), (20, 48)])
    p.poly(lo(k.plate), [(38, 30), (48, 29), (44, 44), (36, 46)])
    for yy in (33, 38, 43):
        p.line(k.joint, [(39, yy), (46, yy - 1)])
    raptor_leg(p, s, k, k.joint, 34, 47, 0)
    p.poly(k.joint, [(36, 26), (40, 14), (48, 16), (46, 30)])
    strato_head_side(p, s, k, 0, 4)
    feather_wing(p, k, (34, 24), (8, 44), wy=wy)


def _strato_front(p, s, k):
    wy = wing_y(s)
    m, p.mirror = p.mirror, False
    for x, grp in ((25, 0), (38, 1)):
        dx, lift = _gait(s, grp)
        g = p.ground - lift * 3
        p.poly(k.joint, [(x - 4, 46), (x + 4, 46), (x + 2, 52), (x - 2, 52)])
        p.line(k.joint, [(x, 52), (x, g - 1)], width=3)
        p.line(k.joint, [(x - 4, g), (x + 4, g)], width=2)
        p.px(k.talon, [(x - 5, g), (x + 5, g), (x, g)])
    p.mirror = m
    p.poly(k.joint, [(26, 44), (22, 56), (32, 50)])
    p.poly(k.steel, [(18, 36), (22, 26), (32, 24), (32, 50), (22, 48)])
    p.poly(lo(k.plate), [(25, 30), (32, 29), (32, 48), (26, 46)])
    for yy in (33, 38, 43):
        p.line(k.joint, [(26, yy), (32, yy)])
    p.poly(k.plate, [(16, 22 + wy), (22, 24), (22, 48), (14, 56), (10, 44 + wy)])
    p.poly(lo(k.plate), [(10, 30 + wy), (16, 26 + wy), (16, 50), (10, 54)])
    p.px(k.accent, sq(10, 53))
    for tip in ((18, 2), (24, 0)):
        p.poly(k.plate, [(26, 12), tip, (30, 9)])
    p.px(k.accent, sq(18, 2, 1, 1) + sq(24, 0, 1, 1))
    p.poly(k.steel, [(22, 16), (25, 9), (32, 8), (32, 26), (26, 24)])
    p.rect(k.jaw, (23, 14, 33, 16), shade=False)
    if k.eye:
        p.px(k.eye, sq(24, 14, 2, 1) + sq(26, 15, 3, 1) + sq(29, 16, 2, 1))
    if s.mouth:
        p.poly(k.dark, [(28, 17), (32, 17), (32, 21)])
        p.poly(k.jaw, [(29, 21), (32, 21), (32, 25), (30, 24)], shade=False)
        p.poly(k.dark, [(30, 25), (32, 25), (32, 28)])
    else:
        p.poly(k.dark, [(27, 17), (32, 17), (32, 27), (30, 26)])


def _strato_fq(p, s, k):
    wy = wing_y(s)
    p.poly(k.joint, [(18, 40), (3, 34), (7, 40), (2, 46), (7, 48), (18, 45)])
    raptor_leg(p, s, k, lo(k.joint), 28, 46, 1)
    p.poly(k.steel, [(14, 42), (20, 30), (30, 22), (42, 20), (50, 28), (48, 42), (38, 50), (20, 48)])
    p.poly(lo(k.plate), [(36, 30), (48, 30), (46, 44), (36, 48)])
    for yy in (34, 39, 44):
        p.line(k.joint, [(37, yy), (47, yy - 1)])
    raptor_leg(p, s, k, k.joint, 38, 49, 0)
    feather_wing(p, k, (30, 24), (6, 44), wy=wy)
    y = 4
    for tip in ((32, 2), (37, -1), (42, 1)):
        p.poly(k.plate, [(44, 12 + y), (tip[0], tip[1] + y), (48, 9 + y)])
    p.px(k.accent, sq(37, -1 + y))
    p.poly(k.steel, [(38, 14 + y), (42, 7 + y), (51, 6 + y), (56, 11 + y), (53, 20 + y), (42, 21 + y)])
    p.rect(k.jaw, (40, 11 + y, 55, 13 + y), shade=False)
    if k.eye:
        p.px(k.eye, sq(41, 11 + y, 2, 1) + sq(43, 12 + y, 2, 1) + sq(45, 13 + y, 1, 1) + sq(50, 13 + y, 1, 1) + sq(51, 12 + y, 2, 1) + sq(53, 11 + y, 2, 1))
    if s.mouth:
        p.poly(k.dark, [(45, 14 + y), (59, 16 + y), (50, 19 + y)])
        p.poly(k.jaw, [(48, 19 + y), (56, 19 + y), (53, 23 + y)], shade=False)
        p.poly(k.dark, [(48, 23 + y), (55, 23 + y), (50, 25 + y)])
    else:
        p.poly(k.dark, [(45, 14 + y), (60, 17 + y), (58, 22 + y), (54, 20 + y), (48, 21 + y)])


def _strato_bq(p, s, k):
    wy = wing_y(s)
    raptor_leg(p, s, k, lo(k.joint), 36, 47, 0)
    p.poly(k.joint, [(34, 26), (38, 14), (46, 16), (44, 30)])
    strato_head_side(p, s, k, -4, 4)
    p.poly(k.steel, [(12, 42), (16, 30), (28, 22), (40, 20), (46, 28), (44, 40), (34, 48), (18, 49)])
    feather_wing(p, k, (38, 22), (10, 42), rows=3, wy=wy)
    feather_wing(p, k, (28, 22), (4, 38), rows=2, wy=wy, far=True)
    raptor_leg(p, s, k, k.joint, 26, 48, 1)
    p.poly(k.joint, [(16, 42), (0, 38), (4, 44), (0, 52), (6, 52), (18, 46)])
    p.px(k.accent, [(0, 38), (1, 38), (0, 52), (1, 52)])


def _strato_back(p, s, k):
    wy = wing_y(s)
    m, p.mirror = p.mirror, False
    for x, grp in ((25, 0), (38, 1)):
        dx, lift = _gait(s, grp)
        g = p.ground - lift * 3
        p.line(k.joint, [(x, 50), (x, g - 1)], width=3)
        p.line(k.joint, [(x - 4, g), (x + 4, g)], width=2)
    p.mirror = m
    for tip in ((18, 2), (24, 0)):
        p.poly(k.plate, [(26, 12), tip, (30, 9)])
    p.px(k.accent, sq(18, 2, 1, 1) + sq(24, 0, 1, 1))
    p.poly(k.steel, [(22, 16), (25, 9), (32, 8), (32, 26), (26, 24)])
    p.poly(k.steel, [(18, 36), (22, 24), (32, 22), (32, 50), (22, 48)])
    p.poly(k.plate, [(14, 22 + wy), (24, 22 + wy), (32, 30), (32, 50), (22, 52), (12, 46 + wy)])
    p.poly(lo(k.plate), [(12, 32 + wy), (22, 34 + wy), (30, 44), (30, 52), (22, 54), (12, 48 + wy)])
    p.line(k.dark, [(32, 30), (32, 52)], width=2)
    p.px(k.accent, sq(22, 53))
    p.poly(k.joint, [(26, 50), (20, 60), (26, 58), (32, 61), (32, 50)])


stratolord = design('stratolord', 'Flying', 'wind', '#264E9C', views=ALL_VIEWS, size=64)(by_view({
    'kit': StratolordKit, 'side': _strato_side, 'front': _strato_front, 'fq': _strato_fq,
    'bq': _strato_bq, 'back': _strato_back}))


# ================================================================ SHOPPING
class CartiniKit(Kit):
    """Green-slate dark kit for Shopping; accent is a till-display green."""
    steel, plate, joint, dark = '#47524A', '#5F6D63', '#29302B', '#141815'
    accent, flare = '#4DFF88', '#C8FFD8'


def caster(p, s, k, x, y, grp, far=False):
    """Jointed leg from (x, y) ending in a caster wheel on the ground."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 2, lift * 2
    g = p.ground - lift
    wx = x + dx
    col = lo(k.joint) if far else k.joint
    p.line(col, [(x, y), (x + 2 + dx // 2, (y + g) // 2), (wx, g - 5)], width=2)
    p.ell(k.dark, (wx - 3, g - 6, wx + 3, g))
    spoke = [(0, -2), (2, 0), (0, 2), (-2, 0)][(s.t + grp) % 4]
    p.px(k.accent if not far else lo(k.accent), [(wx + spoke[0], g - 3 + spoke[1])])


def cart_basket_side(p, s, k, x=0):
    """Wire basket with a hinged lid for a jaw; eyes on the front panel."""
    def t(pts):
        return [(a + x, b) for a, b in pts]
    p.poly(k.steel, t([(10, 27), (56, 25), (50, 48), (16, 48)]))
    for xx in range(17, 52, 5):
        p.line(k.joint, t([(xx, 30), (xx - 1 + (xx - 17) // 12, 46)]))
    p.line(k.joint, t([(13, 37), (53, 36)]))
    p.line(k.plate, t([(10, 27), (56, 25)]), width=2)
    if s.mouth:
        p.poly(k.jaw, t([(14, 25), (55, 20), (56, 25), (14, 27)]), shade=False)
        fangs(p, k, [a + x for a in (34, 40, 46, 52)], 24, down=False, h=3)
        p.poly(k.plate, t([(8, 26), (14, 23), (52, 12), (54, 16), (16, 27)]))
        fangs(p, k, [a + x for a in (36, 42, 48)], 17, h=3)
        p.px(k.jaw, t([(20 + i, 23 - i // 4) for i in range(0, 30, 3)]))
    else:
        p.poly(k.plate, t([(8, 26), (12, 22), (56, 20), (58, 24), (56, 26), (10, 28)]))
        fangs(p, k, [a + x for a in (32, 38, 44, 50)], 26, h=3)
        p.px(k.jaw, t([(16 + i, 24) for i in range(0, 36, 4)]))
    p.rect(k.jaw, (48 + x, 29, 55 + x, 31), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(49, 29, 3, 2) + sq(52, 30, 3, 2)))


def cart_handle_side(p, k, x=0):
    p.line(k.joint, [(14 + x, 28), (6 + x, 16)], width=3)
    p.rect(k.dark, (1 + x, 13, 9 + x, 17))
    p.px(k.accent, sq(3 + x, 14, 2, 2))


def _cart_side(p, s, k):
    caster(p, s, k, 20, 48, 1, far=True)
    caster(p, s, k, 44, 48, 0, far=True)
    cart_handle_side(p, k)
    cart_basket_side(p, s, k)
    p.rect(k.joint, (15, 48, 50, 50))
    p.px(k.hot, sq(30 + 4 * (s.t % 3), 42, 2, 1))
    caster(p, s, k, 23, 49, 0)
    caster(p, s, k, 47, 49, 1)


def _cart_front(p, s, k):
    p.line(k.joint, [(20, 20), (18, 10)], width=3)
    p.rect(k.dark, (16, 8, 32, 11))
    p.px(k.accent, sq(19, 9))
    m, p.mirror = p.mirror, False
    caster(p, s, k, 20, 48, 0)
    caster(p, s, k, 43, 48, 1)
    p.mirror = m
    if s.mouth:
        p.poly(k.plate, [(12, 14), (32, 10), (32, 16), (14, 19)])
        p.poly(k.jaw, [(14, 21), (32, 20), (32, 30), (16, 28)], shade=False)
        fangs(p, k, [18, 24, 29], 17, h=3)
        fangs(p, k, [16, 22, 28], 27, down=False, h=3)
    p.poly(k.steel, [(12, 24 if s.mouth else 21), (32, 22 if s.mouth else 20), (32, 48), (18, 48)])
    for xx in range(17, 32, 5):
        p.line(k.joint, [(xx, 26), (xx + 1, 46)])
    p.line(k.joint, [(15, 36), (32, 36)])
    if not s.mouth:
        p.poly(k.plate, [(10, 20), (32, 18), (32, 22), (12, 23)])
        fangs(p, k, [16, 21, 26, 30], 22, h=3)
    p.rect(k.jaw, (21, 29, 33, 31), shade=False)
    if k.eye:
        p.px(k.eye, sq(22, 29, 2, 1) + sq(24, 30, 3, 1) + sq(27, 31, 3, 1))
    p.rect(k.joint, (17, 48, 32, 50))


def _cart_fq(p, s, k):
    caster(p, s, k, 20, 48, 1, far=True)
    caster(p, s, k, 42, 48, 0, far=True)
    cart_handle_side(p, k, 2)
    cart_basket_side(p, s, k, -2)
    # the front panel turned toward us, with a V of eyes
    p.poly(k.steel, [(46, 25), (58, 23), (56, 48), (46, 48)])
    for xx in (50, 54):
        p.line(k.joint, [(xx, 32), (xx, 46)])
    p.line(k.plate, [(46, 25), (58, 23)], width=2)
    p.rect(k.jaw, (46, 29, 57, 31), shade=False)
    if k.eye:
        p.px(k.eye, sq(47, 29, 2, 1) + sq(49, 30, 1, 1) + sq(50, 31, 1, 1) + sq(53, 31, 1, 1) + sq(54, 30, 1, 1) + sq(55, 29, 2, 1))
    p.rect(k.joint, (14, 48, 56, 50))
    p.px(k.hot, sq(28 + 4 * (s.t % 3), 42, 2, 1))
    caster(p, s, k, 22, 49, 0)
    caster(p, s, k, 50, 49, 1)


def _cart_bq(p, s, k):
    caster(p, s, k, 44, 48, 0, far=True)
    cart_basket_side(p, s, k, 2)
    p.poly(k.steel, [(8, 27), (20, 26), (20, 48), (12, 48)])
    for xx in (12, 16):
        p.line(k.joint, [(xx, 30), (xx + 1, 46)])
    p.line(k.joint, [(10, 28), (4, 16)], width=3)
    p.line(k.joint, [(18, 27), (14, 14)], width=3)
    p.rect(k.dark, (1, 11, 17, 15))
    p.px(k.accent, sq(6, 12))
    p.rect(k.joint, (12, 48, 50, 50))
    caster(p, s, k, 16, 49, 0)
    caster(p, s, k, 26, 49, 1)
    caster(p, s, k, 48, 49, 1)


def _cart_back(p, s, k):
    m, p.mirror = p.mirror, False
    caster(p, s, k, 20, 48, 0)
    caster(p, s, k, 43, 48, 1)
    p.mirror = m
    p.poly(k.plate, [(10, 20), (32, 18), (32, 22), (12, 23)])
    p.poly(k.steel, [(12, 22), (32, 20), (32, 48), (18, 48)])
    for xx in range(17, 32, 5):
        p.line(k.joint, [(xx, 24), (xx + 1, 46)])
    p.line(k.joint, [(15, 34), (32, 34)])
    p.line(k.joint, [(16, 24), (14, 12)], width=3)
    p.rect(k.dark, (10, 9, 32, 13))
    p.px(k.accent, sq(12, 10) + sq(30, 10))
    p.rect(k.joint, (17, 48, 32, 50))


cartini = design('cartini', 'Shopping', 'coin', '#43C463', views=ALL_VIEWS, size=64)(by_view({
    'kit': CartiniKit, 'side': _cart_side, 'front': _cart_front, 'fq': _cart_fq,
    'bq': _cart_bq, 'back': _cart_back}))


# ================================================================ LEGENDARY
class LegendKit(Kit):
    """Legendaries: the shared dark steel with a molten-gold accent."""
    accent, flare = '#F2C84B', '#FFF1C2'


def halo(p, k, cx, y, r=7):
    p.line(k.hot, [(cx - r, y + 1), (cx - r + 2, y), (cx + r - 2, y), (cx + r, y + 1)], sep=False)
    p.line(lo(k.accent), [(cx - r + 1, y + 2), (cx + r - 1, y + 2)], sep=False)


def aegis_orbs(p, s, k, cx, cy, rx, ry):
    for j in (0, 4):
        a = (s.t + j) * math.pi / 4
        x, y = round(cx + rx * math.cos(a)), round(cy + ry * math.sin(a))
        p.px(k.hot, sq(x, y))


def _aegis_side(p, s, k):
    wy = wing_y(s) * 2
    blade_wing(p, k, (26, 26), (6, 6), (14, 30), wy, far=True)
    halo(p, k, 32, 6)
    p.poly(k.steel, [(24, 12), (40, 12), (44, 24), (34, 50), (22, 30)])
    p.poly(k.plate, [(38, 13), (42, 13), (46, 24), (36, 46)])
    p.line(k.accent, [(24, 12), (40, 12), (44, 24), (34, 50)], sep=False)
    p.line(lo(k.accent), [(32, 16), (32, 42)], sep=False)
    p.line(lo(k.accent), [(26, 22), (40, 22)], sep=False)
    p.rect(k.dark, (34, 16, 43, 18), shade=False)
    if k.eye:
        p.px(k.eye, sq(36, 16, 3, 2) + sq(39, 17, 3, 2))
    if s.mouth:
        p.poly(k.jaw, [(34, 26), (40, 26), (38, 34), (35, 32)], shade=False)
    blade_wing(p, k, (28, 28), (10, 8), (18, 34), wy)
    aegis_orbs(p, s, k, 32, 30, 20, 8)


def _aegis_front(p, s, k):
    wy = wing_y(s) * 2
    blade_wing(p, k, (24, 24), (2, 6), (18, 34), wy)
    halo(p, k, 32, 4, 8)
    p.poly(k.steel, [(18, 10), (32, 10), (32, 54), (20, 34), (16, 20)])
    p.poly(k.plate, [(21, 13), (32, 13), (32, 30), (22, 28)])
    p.line(k.accent, [(18, 10), (32, 10)], sep=False)
    p.line(k.accent, [(16, 20), (20, 34), (32, 54)], sep=False)
    p.line(lo(k.accent), [(20, 24), (32, 24)], sep=False)
    p.rect(k.dark, (22, 16, 33, 18), shade=False)
    if k.eye:
        p.px(k.eye, sq(23, 16, 2, 1) + sq(25, 17, 3, 1) + sq(28, 18, 3, 1))
    if s.mouth:
        p.poly(k.jaw, [(27, 28), (32, 28), (32, 38), (29, 36)], shade=False)
    m, p.mirror = p.mirror, False
    aegis_orbs(p, s, k, 32, 32, 26, 6)
    p.mirror = m


aegis = design('aegis', 'Legendary', 'nova', '#F2C84B', float=True, size=64)(by_view({
    'kit': LegendKit, 'side': _aegis_side, 'front': _aegis_front}))


def titan_mane(p, k, cx, cy, r, n=9, a0=-2.6, a1=1.2):
    """Ring of blade spikes (the mane) fanned from a0 to a1 radians."""
    for i in range(n):
        a = a0 + (a1 - a0) * i / (n - 1)
        tx, ty = cx + r * math.cos(a), cy + r * math.sin(a)
        bx, by = cx + (r - 9) * math.cos(a - 0.25), cy + (r - 9) * math.sin(a - 0.25)
        cx2, cy2 = cx + (r - 9) * math.cos(a + 0.25), cy + (r - 9) * math.sin(a + 0.25)
        p.poly(k.joint if i % 2 else k.dark, [(round(bx), round(by)), (round(tx), round(ty)), (round(cx2), round(cy2))])
    p.px(k.accent, sq(round(cx + r * math.cos(a0)) - 1, round(cy + r * math.sin(a0)) - 1))


def _titan_side(p, s, k):
    p.line(k.steel, [(12, 32), (6, 22), (4, 14)], width=3)
    p.poly(k.dark, [(4, 16), (0, 8), (8, 12)])
    p.px(k.accent, sq(1, 8))
    dleg(p, s, lo(k.joint), 18, 40, 1, hind=True)
    dleg(p, s, lo(k.joint), 40, 40, 0)
    p.poly(k.steel, [(10, 34), (16, 26), (40, 23), (48, 30), (46, 42), (38, 46), (18, 46), (12, 42)])
    p.poly(k.plate, [(16, 26), (38, 23), (40, 30), (18, 32)])
    for x0 in (20, 27, 34):
        p.poly(k.dark, [(x0, 26), (x0 + 2, 19), (x0 + 5, 25)], shade=False)
    p.px(k.accent, sq(26, 36, 2, 1) + sq(30, 36, 2, 1) + sq(34, 36, 2, 1))
    dleg(p, s, k.joint, 22, 41, 0, hind=True, paw=k.dark)
    dleg(p, s, k.joint, 44, 41, 1, paw=k.dark)
    titan_mane(p, k, 46, 22, 20)
    if s.mouth:
        p.poly(k.jaw, [(50, 26), (62, 26), (60, 33), (50, 31)], shade=False)
        fangs(p, k, [52, 56, 59], 30, down=False, h=3)
    p.poly(k.steel, [(40, 20), (45, 12), (54, 13), (58, 18), (54, 27), (43, 27)])
    p.poly(k.plate, [(52, 17), (62, 20), (62, 25), (54, 27)])
    p.rect(k.dark, (46, 16, 55, 18), shade=False)
    if k.eye:
        p.px(k.eye, sq(48, 16, 3, 2) + sq(51, 17, 4, 2))
    p.line(k.jaw, [(54, 27), (61, 26)])
    fangs(p, k, [54, 58], 27, h=3)
    p.px(k.jaw, sq(61, 21))


def _titan_front(p, s, k):
    legs_front(p, s, lo(k.joint), 15, 48, 4)
    p.poly(k.steel, [(14, 36), (20, 28), (32, 27), (32, 52), (22, 50), (14, 44)])
    p.poly(k.plate, [(14, 36), (20, 29), (24, 32), (18, 40)])
    p.px(k.accent, sq(30, 42, 2, 2))
    legs_front(p, s, k.joint, 21, 45, 4, foot=k.dark)
    titan_mane(p, k, 32, 20, 22, n=7, a0=math.pi * 0.55, a1=math.pi * 1.5)
    p.poly(k.steel, [(20, 18), (24, 10), (32, 9), (32, 32), (26, 31), (21, 24)])
    p.rect(k.dark, (22, 15, 33, 18), shade=False)
    if k.eye:
        p.px(k.eye, sq(23, 15, 2, 1) + sq(25, 16, 3, 1) + sq(28, 17, 3, 2))
    p.poly(k.plate, [(27, 20), (32, 19), (32, 34), (28, 32)])
    p.px(k.jaw, sq(30, 20, 3, 2))
    if s.mouth:
        p.poly(k.jaw, [(26, 29), (32, 29), (32, 40), (28, 38)], shade=False)
        fangs(p, k, [27, 30], 29, h=3)
        fangs(p, k, [29], 38, down=False, h=3)
    else:
        p.px(k.jaw, sq(26, 31, 6, 1))
        fangs(p, k, [27, 30], 32, h=3)


titan = design('titan', 'Legendary', 'claw', '#B07A3A', size=64)(by_view({
    'kit': LegendKit, 'side': _titan_side, 'front': _titan_front}))


# ================================================================ PEOPLE
class PlayerKit(Kit):
    """The tamer: dark blue-steel armour, a cape, a glowing blue visor."""
    steel, plate, joint, dark = '#44506A', '#5E6C88', '#262D3C', '#12151D'
    accent, flare = '#5CA8FF', '#D0E6FF'
    cape, wood = '#6A2230', '#4A3526'


def hum_leg(p, s, col, x, y, grp, boot=None, w=4):
    """Straight humanoid leg from hip (x, y) with a knee and a boot that
    points right (side views)."""
    dx, lift = _gait(s, grp)
    dx, lift = dx * 3, lift * 3
    g = p.ground - lift
    kx, ky = x + 1 + dx // 2, (y + g) // 2
    p.line(col, [(x, y), (kx, ky)], width=w)
    p.line(col, [(kx, ky), (x + dx, g - 3)], width=w - 1)
    p.poly(boot or col, [(x + dx - 2, g - 4), (x + dx + 2, g - 4), (x + dx + 5, g), (x + dx - 2, g)])


def hum_legs_front(p, s, col, x, y, boot=None, w=4):
    """Pair of legs seen from the front/back, stepping in turn."""
    m, p.mirror = p.mirror, False
    for side, xx in ((0, x), (1, p.size - 1 - x)):
        lift = 3 if (s.step == 1 and side == 0) or (s.step == 3 and side == 1) else 0
        g = p.ground - lift
        p.rect(col, (xx - w // 2, y - lift, xx + w // 2 - 1, g - 3))
        p.rect(boot or col, (xx - w // 2 - 1, g - 3, xx + w // 2, g))
    p.mirror = m


def hum_arm(p, s, col, x, y, hx, hy, grp=0, w=3, hand=None):
    """Arm from shoulder (x, y) to hand (hx, hy), swinging with the step."""
    dx, _ = _gait(s, grp)
    hx += dx * 2
    ex, ey = (x + hx) // 2, (y + hy) // 2
    p.line(col, [(x, y), (ex, ey)], width=w)
    p.line(col, [(ex, ey), (hx, hy)], width=w)
    p.rect(hand or col, (hx - 1, hy - 1, hx + 1, hy + 1))


def staff(p, k, x, top=4, bot=None):
    bot = bot or p.ground
    p.line(k.wood, [(x, top + 5), (x, bot)], width=2)
    p.poly(k.hot, [(x, top), (x + 3, top + 4), (x, top + 8), (x - 3, top + 4)], shade=False)
    p.px('#FFFFFF', [(x, top + 3)])


def player_helm_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(k.steel, t([(25, 10), (29, 3), (38, 3), (42, 9), (41, 17), (29, 18), (26, 15)]))
    p.poly(k.plate, t([(29, 3), (38, 3), (41, 8), (33, 7)]))
    p.poly(k.plate, t([(29, 6), (22, 1), (30, 9)]))
    p.rect(k.dark, (33 + x, 9 + y, 42 + x, 11 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(36, 9, 3, 2) + sq(39, 10, 3, 2)))
    p.poly(k.joint, t([(36, 13), (42, 12), (41, 17), (35, 17)]))
    if s.mouth:
        p.px(k.jaw, t(sq(38, 14, 3, 2)))


def _player_side(p, s, k):
    p.poly(k.cape, [(26, 20), (20, 36), (14, 54), (22, 56), (28, 40)])
    hum_leg(p, s, lo(k.joint), 30, 40, 1)
    hum_arm(p, s, lo(k.joint), 30, 22, 26, 38, 1)
    p.poly(k.steel, [(25, 20), (37, 19), (39, 30), (37, 41), (26, 41), (24, 30)])
    p.poly(k.plate, [(27, 21), (37, 20), (38, 29), (29, 30)])
    p.rect(k.dark, (25, 36, 38, 38), shade=False)
    p.px(k.accent, sq(35, 36, 2, 2))
    hum_leg(p, s, k.joint, 32, 41, 0, boot=k.dark)
    player_helm_side(p, s, k)
    p.poly(k.plate, [(27, 18), (35, 17), (37, 24), (29, 26)])
    staff(p, k, 45)
    hum_arm(p, s, k.joint, 32, 22, 44, 32, 0)


def _player_front(p, s, k):
    p.poly(k.cape, [(20, 22), (14, 54), (22, 56)])
    hum_legs_front(p, s, k.joint, 27, 40, boot=k.dark)
    p.poly(k.steel, [(21, 21), (32, 20), (32, 42), (24, 42), (22, 32)])
    p.poly(k.plate, [(24, 23), (32, 22), (32, 32), (26, 31)])
    p.rect(k.dark, (23, 37, 32, 39), shade=False)
    p.px(k.accent, sq(31, 37, 2, 2))
    p.poly(k.plate, [(16, 20), (24, 18), (26, 25), (18, 28)])
    m, p.mirror = p.mirror, False
    for x, grp in ((18, 0), (46, 1)):
        dx, _ = _gait(s, grp)
        p.line(k.joint, [(x, 26), (x - 1 if x < 32 else x + 1, 38 + dx)], width=3)
    staff(p, k, 50)
    p.mirror = m
    p.poly(k.plate, [(24, 6), (18, 0), (27, 3)])
    p.poly(k.steel, [(24, 10), (27, 3), (32, 2), (32, 19), (26, 17)])
    p.rect(k.dark, (25, 9, 33, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(26, 9, 2, 1) + sq(28, 10, 2, 1) + sq(30, 11, 2, 1))
    p.poly(k.joint, [(27, 13), (32, 13), (32, 18), (28, 17)])
    if s.mouth:
        p.px(k.jaw, sq(30, 15, 3, 2))


def _player_fq(p, s, k):
    p.poly(k.cape, [(24, 20), (18, 36), (12, 54), (20, 56), (26, 40)])
    hum_leg(p, s, lo(k.joint), 28, 40, 1)
    hum_arm(p, s, lo(k.joint), 25, 22, 21, 38, 1)
    p.poly(k.steel, [(23, 20), (39, 19), (41, 30), (39, 42), (26, 42), (23, 30)])
    p.poly(k.plate, [(26, 21), (38, 20), (39, 29), (29, 30)])
    p.rect(k.dark, (24, 37, 40, 39), shade=False)
    p.px(k.accent, sq(33, 37, 2, 2))
    hum_leg(p, s, k.joint, 35, 42, 0, boot=k.dark)
    p.poly(k.plate, [(29, 6), (22, 1), (31, 9)])
    p.poly(k.steel, [(26, 10), (29, 3), (38, 2), (42, 8), (41, 17), (29, 18)])
    p.rect(k.dark, (28, 9, 42, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(29, 9, 2, 1) + sq(31, 10, 2, 1) + sq(33, 11, 1, 1) + sq(36, 11, 1, 1) + sq(37, 10, 2, 1) + sq(39, 9, 2, 1))
    p.poly(k.joint, [(32, 13), (41, 13), (40, 18), (33, 18)])
    if s.mouth:
        p.px(k.jaw, sq(35, 15, 3, 2))
    p.poly(k.plate, [(34, 18), (42, 17), (44, 24), (36, 26)])
    staff(p, k, 48)
    hum_arm(p, s, k.joint, 40, 22, 47, 32, 0)


def _player_bq(p, s, k):
    staff(p, k, 44)
    hum_arm(p, s, lo(k.joint), 34, 22, 43, 32, 0)
    hum_leg(p, s, lo(k.joint), 32, 40, 0)
    p.poly(k.steel, [(23, 20), (37, 19), (39, 30), (37, 41), (24, 41)])
    p.poly(k.steel, [(24, 10), (28, 3), (37, 3), (40, 9), (39, 17), (27, 18)])
    p.poly(k.plate, [(26, 5), (34, 3), (33, 16), (27, 16)])
    p.rect(k.dark, (37, 9, 41, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(39, 9, 2, 2))
    p.poly(k.plate, [(27, 5), (20, 0), (28, 8)])
    hum_leg(p, s, k.joint, 27, 41, 1, boot=k.dark)
    p.poly(k.cape, [(22, 18), (36, 18), (38, 36), (34, 56), (16, 56), (18, 34)])
    p.poly(lo(k.cape), [(24, 30), (30, 30), (28, 55), (22, 55)])
    p.poly(k.plate, [(20, 17), (28, 16), (29, 22), (22, 24)])


def _player_back(p, s, k):
    hum_legs_front(p, s, k.joint, 27, 40, boot=k.dark)
    m, p.mirror = p.mirror, False
    for x, grp in ((18, 0), (46, 1)):
        dx, _ = _gait(s, grp)
        p.line(k.joint, [(x, 26), (x - 1 if x < 32 else x + 1, 38 + dx)], width=3)
    staff(p, k, 13)
    p.mirror = m
    p.poly(k.plate, [(24, 6), (18, 0), (27, 3)])
    p.poly(k.steel, [(24, 10), (27, 3), (32, 2), (32, 19), (26, 17)])
    p.px(lo(k.steel), sq(28, 5, 2, 11))
    p.poly(k.cape, [(19, 19), (32, 18), (32, 56), (15, 55)])
    p.poly(lo(k.cape), [(22, 30), (28, 30), (27, 55), (20, 55)])
    p.poly(k.plate, [(16, 20), (24, 18), (26, 25), (18, 28)])


player = design('player', 'Hero', 'nova', '#2E6FD8', views=ALL_VIEWS, size=64)(by_view({
    'kit': PlayerKit, 'side': _player_side, 'front': _player_front, 'fq': _player_fq,
    'bq': _player_bq, 'back': _player_back}))


class PoacherKit(Kit):
    """The poacher: drab olive coat, black hat, amber goggle lenses."""
    steel, plate, joint, dark = '#4A4E3A', '#62674C', '#2A2D22', '#141610'
    accent, flare = '#FFB02E', '#FFE2A8'
    hat, scarf, gun, net = '#1E1E1C', '#5A2626', '#3A3A44', '#8A7A52'


def poacher_head_side(p, s, k, x=0, y=0):
    def t(pts):
        return [(a + x, b + y) for a, b in pts]
    p.poly(k.joint, t([(27, 9), (38, 8), (40, 16), (29, 18)]))
    p.poly(k.scarf, t([(28, 13), (41, 12), (42, 18), (29, 19)]))
    p.poly(k.hat, t([(20, 8), (46, 6), (46, 8), (22, 10)]))
    p.poly(k.hat, t([(26, 7), (28, 0), (39, 0), (41, 6)]))
    p.line(k.scarf, t([(27, 5), (40, 4)]))
    p.rect(k.dark, (33 + x, 9 + y, 41 + x, 11 + y), shade=False)
    if k.eye:
        p.px(k.eye, t(sq(35, 9, 3, 2) + sq(38, 10, 3, 1)))
    if s.mouth:
        p.px(k.jaw, t(sq(38, 15, 3, 2)))


def netgun(p, k, x, y):
    """Net launcher pointing right, grip at (x, y)."""
    p.rect(k.gun, (x - 2, y - 3, x + 12, y + 1))
    p.rect(k.dark, (x + 12, y - 4, x + 15, y + 2))
    p.px(k.net, [(x + 13, y - 3), (x + 14, y - 2), (x + 13, y - 1), (x + 14, y), (x + 13, y + 1)])
    p.px(k.hot, [(x + 4, y - 3), (x + 5, y - 3)])


def net_pack(p, k, x0, y0, x1, y1):
    p.rect(k.dark, (x0, y0, x1, y1))
    for yy in range(y0 + 2, y1, 3):
        p.line(k.net, [(x0 + 1, yy), (x1 - 1, yy - 1)])


def _poacher_side(p, s, k):
    net_pack(p, k, 17, 20, 24, 36)
    hum_leg(p, s, lo(k.joint), 30, 42, 1)
    p.poly(k.steel, [(24, 19), (36, 18), (39, 30), (40, 50), (22, 50), (24, 34)])
    p.poly(lo(k.steel), [(33, 32), (39, 31), (40, 50), (34, 50)])
    p.rect(k.dark, (25, 33, 38, 35), shade=False)
    p.px(k.accent, sq(34, 33, 2, 2))
    hum_leg(p, s, k.joint, 32, 48, 0, boot=k.dark)
    poacher_head_side(p, s, k)
    p.line(k.steel, [(31, 22), (36, 30), (40, 30)], width=4)
    netgun(p, k, 40, 31)


def _poacher_front(p, s, k):
    hum_legs_front(p, s, k.joint, 27, 46, boot=k.dark)
    p.poly(k.steel, [(21, 20), (32, 19), (32, 50), (19, 50), (22, 34)])
    p.poly(lo(k.steel), [(24, 34), (32, 33), (32, 50), (22, 50)])
    p.line(k.dark, [(31, 20), (31, 50)])
    p.rect(k.dark, (22, 33, 32, 35), shade=False)
    p.px(k.accent, sq(30, 33, 2, 2))
    m, p.mirror = p.mirror, False
    dx, _ = _gait(s, 0)
    p.line(k.steel, [(19, 22), (17, 36 + dx)], width=4)
    p.line(k.steel, [(45, 22), (40, 32)], width=4)
    p.rect(k.gun, (30, 30, 42, 35))
    p.ell(k.dark, (29, 28, 35, 36))
    p.px(k.net, [(31, 30), (33, 31), (31, 32), (33, 33), (32, 34)])
    p.px(k.hot, sq(38, 30, 2, 1))
    p.mirror = m
    p.poly(k.joint, [(25, 8), (32, 8), (32, 18), (26, 17)])
    p.poly(k.scarf, [(24, 12), (32, 12), (32, 19), (25, 18)])
    p.poly(k.hat, [(14, 7), (32, 5), (32, 8), (16, 9)])
    p.poly(k.hat, [(23, 6), (25, 0), (32, 0), (32, 6)])
    p.line(k.scarf, [(24, 4), (32, 4)])
    p.rect(k.dark, (24, 9, 33, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(26, 9, 3, 2))
        p.px(k.flare, [(26, 9)])
    if s.mouth:
        p.px(k.jaw, sq(29, 15, 4, 2))


def _poacher_fq(p, s, k):
    net_pack(p, k, 18, 20, 23, 34)
    hum_leg(p, s, lo(k.joint), 28, 42, 1)
    p.poly(k.steel, [(23, 19), (39, 18), (42, 32), (43, 50), (21, 50), (23, 34)])
    p.poly(lo(k.steel), [(34, 32), (42, 31), (43, 50), (35, 50)])
    p.line(k.dark, [(35, 20), (36, 50)])
    p.rect(k.dark, (23, 33, 42, 35), shade=False)
    p.px(k.accent, sq(33, 33, 2, 2))
    hum_leg(p, s, k.joint, 34, 48, 0, boot=k.dark)
    p.poly(k.joint, [(27, 9), (39, 8), (41, 16), (29, 18)])
    p.poly(k.scarf, [(27, 13), (42, 12), (42, 19), (28, 19)])
    p.poly(k.hat, [(18, 8), (46, 5), (48, 8), (20, 10)])
    p.poly(k.hat, [(25, 7), (27, 0), (39, 0), (42, 6)])
    p.line(k.scarf, [(26, 5), (41, 4)])
    p.rect(k.dark, (27, 9, 42, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(29, 9, 3, 2) + sq(37, 9, 3, 2))
        p.px(k.flare, [(29, 9), (37, 9)])
    if s.mouth:
        p.px(k.jaw, sq(35, 15, 3, 2))
    p.line(k.steel, [(38, 22), (42, 30), (45, 31)], width=4)
    netgun(p, k, 44, 32)


def _poacher_bq(p, s, k):
    p.line(k.steel, [(35, 22), (40, 29), (44, 29)], width=4)
    netgun(p, k, 44, 30)
    hum_leg(p, s, lo(k.joint), 32, 46, 0)
    p.poly(k.steel, [(22, 19), (36, 18), (39, 30), (40, 50), (20, 50), (22, 34)])
    p.poly(k.joint, [(26, 9), (37, 8), (39, 16), (28, 18)])
    p.poly(k.hat, [(18, 8), (44, 6), (44, 8), (20, 10)])
    p.poly(k.hat, [(24, 7), (26, 0), (37, 0), (39, 6)])
    p.line(k.scarf, [(25, 5), (38, 4)])
    p.rect(k.dark, (36, 9, 40, 11), shade=False)
    if k.eye:
        p.px(k.eye, sq(38, 9, 2, 2))
    hum_leg(p, s, k.joint, 27, 48, 1, boot=k.dark)
    net_pack(p, k, 22, 20, 34, 38)
    p.line(k.dark, [(23, 19), (34, 38)], width=2)


def _poacher_back(p, s, k):
    hum_legs_front(p, s, k.joint, 27, 46, boot=k.dark)
    m, p.mirror = p.mirror, False
    for x, grp in ((19, 0), (45, 1)):
        dx, _ = _gait(s, grp)
        p.line(k.steel, [(x, 22), (x - 2 if x < 32 else x + 2, 36 + dx)], width=4)
    p.mirror = m
    p.poly(k.steel, [(21, 20), (32, 19), (32, 50), (19, 50), (22, 34)])
    p.poly(k.joint, [(25, 8), (32, 8), (32, 18), (26, 17)])
    p.poly(k.hat, [(14, 7), (32, 5), (32, 8), (16, 9)])
    p.poly(k.hat, [(23, 6), (25, 0), (32, 0), (32, 6)])
    p.line(k.scarf, [(24, 4), (32, 4)])
    net_pack(p, k, 22, 20, 32, 38)
    p.px(k.accent, sq(23, 21))


poacher = design('poacher', 'Villain', 'net', '#4A4E3A', views=ALL_VIEWS, size=64)(by_view({
    'kit': PoacherKit, 'side': _poacher_side, 'front': _poacher_front, 'fq': _poacher_fq,
    'bq': _poacher_bq, 'back': _poacher_back}))


# ================================================================ OBJECTS
class LaserKit(Kit):
    accent, flare = '#FF3B3B', '#FFC8C8'


def drone_lens(p, s, k, x, y, r=4):
    """Laser lens centred on (x, y): dim when closed, white-hot when firing."""
    p.ell(k.dark, (x - r - 1, y - r - 1, x + r + 1, y + r + 1), shade=False)
    if s.eyes == 'closed':
        p.line(lo(k.accent), [(x - r + 1, y), (x + r - 1, y)], sep=False)
        return
    c = '#FFFFFF' if s.mouth else ('#FFE14F' if s.eyes == 'hurt' else k.accent)
    p.ell(c, (x - r + 1, y - r + 1, x + r - 1, y + r - 1), shade=False, sep=False)
    p.px(k.flare, [(x - 1, y - 1)])


def rotor(p, s, k, cx, y, w=12):
    p.line(k.joint, [(cx, y), (cx, y + 5)], width=2)
    spin = [w, w * 2 // 3, w // 3, w * 2 // 3][s.t % 4]
    p.line('#AEB8C6', [(cx - spin, y), (cx + spin, y)], sep=False)


def _laser_side(p, s, k):
    rotor(p, s, k, 30, 12)
    p.poly(k.joint, [(18, 36), (8, 46), (20, 42)])
    p.poly(k.steel, [(16, 22), (22, 16), (40, 16), (46, 24), (46, 38), (38, 44), (22, 44), (16, 36)])
    p.poly(k.plate, [(22, 17), (38, 17), (42, 22), (24, 24)])
    p.rect(k.joint, (16, 29, 30, 31))
    p.px(k.hot, sq(18, 29, 2, 2))
    p.poly(k.joint, [(40, 22), (52, 24), (52, 36), (40, 38)])
    drone_lens(p, s, k, 48, 30, 4)


def _laser_front(p, s, k):
    rotor(p, s, k, 32, 12, 14)
    p.poly(k.joint, [(18, 36), (10, 46), (22, 42)])
    p.poly(k.steel, [(16, 24), (22, 16), (32, 15), (32, 45), (22, 44), (16, 36)])
    p.poly(k.plate, [(19, 20), (24, 17), (32, 16), (32, 22), (21, 24)])
    p.px(k.hot, sq(18, 30, 2, 2))
    m, p.mirror = p.mirror, False
    drone_lens(p, s, k, 32, 30, 6)
    p.mirror = m


laser = design('laser', 'Object', 'laser', '#FF3B3B', float=True, size=64)(by_view({
    'kit': LaserKit, 'side': _laser_side, 'front': _laser_front}))


class BiteKit(Kit):
    accent, flare = '#FF4D5E', '#FFC2C8'
    gum = '#5A2A30'


def _bite_side(p, s, k):
    gap = 8 if s.mouth else (2 if s.eyes == 'closed' else 4)
    top = 30 - gap
    p.poly(k.jaw, [(12, top), (56, top - 2), (54, 34 + gap), (14, 34 + gap)], shade=False)
    for x in range(18, 54, 7):
        p.poly(k.teeth, [(x, top), (x + 4, top), (x + 2, top + 6)], shade=False)
        p.poly(k.teeth, [(x - 3, 33 + gap), (x + 1, 33 + gap), (x - 1, 27 + gap)], shade=False)
    p.poly(k.steel, [(8, top), (12, 14), (40, 8), (60, top - 6), (58, top)])
    p.poly(k.plate, [(14, 14), (40, 9), (52, 16), (20, 18)])
    for x0 in (20, 30, 40):
        p.poly(k.dark, [(x0, 13), (x0 + 3, 4), (x0 + 5, 12)], shade=False)
    p.poly(k.steel, [(10, 34 + gap), (58, 32 + gap), (50, 46 + gap), (14, 46 + gap)])
    p.poly(k.gum, [(14, 34 + gap), (52, 33 + gap), (50, 36 + gap), (16, 37 + gap)])
    p.rect(k.dark, (38, 16, 48, 18), shade=False)
    if s.eyes != 'closed':
        p.px(k.red if s.eyes in ('hurt', 'angry') else k.hot, sq(40, 16, 3, 2) + sq(43, 17, 3, 2))


def _bite_front(p, s, k):
    gap = 8 if s.mouth else (2 if s.eyes == 'closed' else 4)
    top = 30 - gap
    p.poly(k.jaw, [(12, top), (32, top), (32, 34 + gap), (14, 34 + gap)], shade=False)
    for x in (14, 20, 26):
        p.poly(k.teeth, [(x, top), (x + 4, top), (x + 2, top + 6)], shade=False)
        p.poly(k.teeth, [(x + 1, 34 + gap), (x + 5, 34 + gap), (x + 3, 28 + gap)], shade=False)
    p.poly(k.steel, [(8, top), (12, 14), (32, 10), (32, top)])
    p.poly(k.plate, [(14, 14), (32, 11), (32, 17), (18, 18)])
    p.poly(k.dark, [(18, 13), (21, 4), (23, 12)], shade=False)
    p.poly(k.steel, [(10, 34 + gap), (32, 34 + gap), (32, 46 + gap), (16, 46 + gap)])
    p.rect(k.dark, (20, 18, 33, 20), shade=False)
    if s.eyes != 'closed':
        p.px(k.red if s.eyes in ('hurt', 'angry') else k.hot, sq(21, 18, 2, 1) + sq(23, 19, 3, 1) + sq(26, 20, 2, 1))


bite = design('bite', 'Object', 'claw', '#7A2E2E', size=64)(by_view({
    'kit': BiteKit, 'side': _bite_side, 'front': _bite_front}))


@design('net', 'Object', 'net', '#C9A86A', float=True, outline=False, size=64)
def net(p, s):
    """A thrown net: dark cord mesh with steel weights; the same from any side."""
    rope, weight = '#8A7A52', '#4A5263'
    sway = [0, 2, 0, -2][s.t % 4] if not s.mouth else 0
    spread = 4 if s.mouth else 0
    L, R, T, B = 10 - spread, 53 + spread, 8 - spread, 51 + spread
    m, p.mirror = p.mirror, False
    for i in range(7):
        x = L + (R - L) * i // 6
        p.line(lo(rope), [(x + 1, T + 1), (x + sway + 1, B + 1)], sep=False)
        p.line(rope, [(x, T), (x + sway, B)], sep=False)
        y = T + (B - T) * i // 6
        p.line(lo(rope), [(L + 1, y + 1), (R + 1, y + sway + 1)], sep=False)
        p.line(rope, [(L, y), (R, y + sway)], sep=False)
    knots = [(L + (R - L) * i // 6, T + (B - T) * j // 6) for i in range(7) for j in range(7)]
    p.px(lo(lo(rope)), knots)
    for x, y in ((L, T), (R, T), (L + sway, B), (R + sway, B)):
        p.ell(weight, (x - 2, y - 2, x + 2, y + 2), shade=False)
        p.px('#AEB8C6', [(x - 1, y - 1)])
    p.mirror = m


# ================================================================ WORLD PROPS
# Scenery billboards for the 3D world (prop_<name>.gif). Not in the Studio
# matrix; drawn to fill the full 32px height so trees stand taller than beasts.
PROPS = {}


def prop(name):
    def wrap(fn):
        PROPS[name] = fn
        return fn
    return wrap


@prop('tree')
def tree(p, s):
    sway = s.t % 2
    p.rect('#6A4A2E', (14, 18, 17, 31))
    p.px('#8A6A44', [(15, 20), (15, 24), (15, 28)])
    p.ell('#3F8A3A', (5 + sway, 8, 26 + sway, 24))
    p.ell('#4E9A42', (8 + sway, 1, 23 + sway, 17))
    p.ell('#5DAE4A', (11 + sway, 3, 20 + sway, 10), shade=False, sep=False)


@prop('pine')
def pine(p, s):
    p.rect('#5A3A22', (15, 25, 16, 31))
    for top, half, col in ((0, 5, '#2E6B3A'), (6, 8, '#2A6236'), (13, 11, '#265A32')):
        p.poly(col, [(15.5, top), (15.5 + half, top + 13), (15.5 - half, top + 13)])
    p.px('#DDF3FF', [(15, 1), (16, 1)])


@prop('bush')
def bush(p, s):
    p.ell('#3F8A3A', (4, 16, 17, 31))
    p.ell('#4E9A42', (13, 14, 28, 31))
    p.px('#E05A7A', [(10, 20), (20, 19), (23, 24)])


@prop('stone')
def stone(p, s):
    p.ell('#8A8F99', (5, 14, 27, 31))
    p.ell('#9AA0AA', (9, 14, 20, 22), shade=False, sep=False)
    p.px('#5E8A4A', [(8, 28), (9, 28), (23, 29)])


@prop('tuft')
def tuft(p, s):
    for x, h in ((9, 10), (13, 16), (16, 12), (19, 17), (23, 9)):
        p.line('#5DAE4A', [(x, 31), (x + (1 if x > 15 else -1), 31 - h)])
    p.px('#8FCB5A', [(13, 16), (19, 15)])


@prop('cage')
def cage(p, s):
    """The netbeast cage the player throws in the 3D Wilds."""
    p.rect('#5E6F82', (6, 8, 25, 10))
    p.rect('#5E6F82', (6, 28, 25, 30))
    for x in (7, 11, 15, 19, 23):
        p.rect('#9AA4B2', (x, 10, x + 1, 27), shade=False)
    p.line('#5E6F82', [(12, 8), (15, 3), (19, 8)])
    p.px('#F2C84B', [(15, 17), (16, 17), (15, 18), (16, 18)])
