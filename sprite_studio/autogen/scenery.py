"""Trees for the 3D Wilds, each kind drawn natively at 10 distance levels, and the
small far-off houses painted along the horizon.

The renderer used to scale one 32px prop_tree with nearest-neighbour, so near
trees went blocky and far ones shimmered. Here every tree is drawn at the
height of each level in D (d0 nearest) as prop_tree_<kind>_d<i>.gif, at 1x:
native pixels are the point. TerrainRenderer picks the level whose height is
the smallest one at or above ~0.9x the on-screen height, so D must match its
TREE_LOD table: change both or neither.

Each tree is one function fn(t) written in relative units (0..1 across and
down the square canvas, ground = 1.0). The level only changes how the parts
are filled, never where they are, so the silhouette and trunk stay put and
switching level never pops:
  d0-d2  bark lines, separate leaf clusters with highlights, roots, twigs, 2-frame sway
  d3-d5  clusters merged into one shaded mass, no twigs, 2-frame sway
  d6-d9  silhouette + two tones, no outline below 16px, 1 frame (static)
"""
import math
import random
import zlib
from dataclasses import dataclass

from PIL import Image, ImageDraw

import pixelkit as pk

D = [128, 96, 72, 56, 44, 32, 24, 16, 12, 8]  # TerrainRenderer.TREE_LOD must match


@dataclass
class TreeDef:
    fn: object
    height: float   # world units, the billboard's width and height
    radius: float   # trunk radius in tiles, for the sim's sidestep
    where: str      # placement note (terrain), read by humans and WorldMap


TREES = {}


def tree(name, height, radius, where):
    def wrap(fn):
        TREES[name] = TreeDef(fn, height, radius, where)
        return fn
    return wrap


def tier(lod):
    return 0 if lod <= 2 else 1 if lod <= 5 else 2


class T:
    """Relative-unit drawing on a Painter for one level and sway frame."""

    def __init__(self, p, lod, frame, seed):
        self.p, self.lod, self.frame = p, lod, frame
        self.S = p.size
        self.tier = tier(lod)
        self.rng = random.Random(seed)  # same every frame, so texture doesn't crawl
        # canopy sway: whole pixels, a bit more on the big levels
        self.sway = (max(1, round(self.S / 64)) if frame else 0) if self.tier < 2 else 0

    # -- coordinates
    def X(self, v):
        return round(v * (self.S - 1))

    def pt(self, x, y, dx=0):
        return (self.X(x) + dx, self.X(y))

    def box(self, cx, cy, rx, ry, dx=0):
        # keep at least one pixel so tiny levels don't lose a part
        x0, x1 = self.X(cx - rx), self.X(cx + rx)
        y0, y1 = self.X(cy - ry), self.X(cy + ry)
        return (x0 + dx, y0, max(x0, x1) + dx, max(y0, y1))

    def w(self, v):
        """Relative stroke width to whole pixels, at least 1."""
        return max(1, round(v * self.S))

    # -- shapes: ('ell', cx, cy, rx, ry) or ('poly', [(x, y), ...])
    def _draw(self, d, shape, dx=0, grow=0.0):
        if shape[0] == 'ell':
            _, cx, cy, rx, ry = shape
            d.ellipse(self.box(cx, cy, rx + grow, ry + grow, dx), fill=1)
            if self.tier == 0 and rx > 0.06:
                # leafy edge: small lobes around the upper rim (sub-pixel on far levels)
                n = max(5, int((rx + ry) * 22))
                for i in range(n + 1):
                    a = math.pi + math.pi * i / n
                    bx, by = cx + math.cos(a) * rx * 0.93, cy + math.sin(a) * ry * 0.93
                    d.ellipse(self.box(bx, by, .028, .024, dx), fill=1)
        else:
            d.polygon([self.pt(x, y, dx) for x, y in shape[1]], fill=1)

    def shapes(self, color, shapes, sway=True, shade=True, sep=True):
        dx = self.sway if sway else 0
        self.p.part(color, lambda d: [self._draw(d, s, dx) for s in shapes], shade=shade, sep=sep)

    # -- parts
    def trunk(self, color, pts, bark=None, lines=3):
        """Trunk polygon; bark lines on the near levels."""
        self.p.poly(color, [self.pt(x, y) for x, y in pts], shade=self.tier < 2)
        if self.tier == 0 and bark:
            xs = [x for x, _ in pts]; ys = [y for _, y in pts]
            x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
            m = self.p.img.load()
            for i in range(lines):
                bx = x0 + (x1 - x0) * (i + 1) / (lines + 1)
                top = y0 + (y1 - y0) * self.rng.uniform(0.1, 0.4)
                bot = y1 - (y1 - y0) * self.rng.uniform(0.05, 0.3)
                for y in range(self.X(top), self.X(bot)):
                    x = self.X(bx) + (1 if (y // 5) % 3 == 0 else 0)
                    if 0 <= x < self.S and 0 <= y < self.S and m[x, y][3]:
                        m[x, y] = pk.rgb(bark) + (255,)

    def canopy(self, color, shapes, light=None, dark=None, speck=None):
        """Leaf mass. tier 0: each shape shaded and leaf-textured, upper ones
        first so lower ones layer over them; tier 1: one merged mass, lighter
        texture; tier 2: dark silhouette with a lit upper-left."""
        light = light or pk.shift(color, 0.12)
        dark = dark or pk.shift(color, -0.14)
        dx = self.sway
        if self.tier == 2:
            # two tones: dark mass, lit where the mass shifted up-left still covers it
            self.shapes(dark, shapes, shade=False, sep=False)
            k = max(1, self.S // 12)
            lit = self.mask([self._shift(sh, -k / self.S, -k / self.S) for sh in shapes], dx)
            self.fill(color, lit, self.mask(shapes, dx))
            return
        if self.tier == 1:
            # one merged mass, then lighter leaf texture (no lobes, no layers)
            self.shapes(color, shapes)
            for sh in shapes:
                self.clumps(sh, light, dark, None, dx, density=24)
            return
        # back (upper) clusters first, so the lower ones overlap them in layers
        for s in sorted(shapes, key=lambda s: s[2] if s[0] == 'ell' else min(y for _, y in s[1])):
            self.shapes(color, [s])
            self.clumps(s, light, dark, speck, dx)

    # small leaf marks, 2-3 px on the big levels
    LEAF = [[(0, 0), (1, 0), (-1, 1)], [(0, 0), (1, 0), (1, 1)], [(0, 0), (-1, 0), (0, 1), (1, 1)]]

    def clumps(self, shape, light, dark, speck, dx, density=None):
        """Leaf texture inside one shape: light marks upper-left, dark lower-right."""
        m = self.mask([shape], dx).load()
        px = self.p.img.load()
        if shape[0] == 'ell':
            _, cx, cy, rx, ry = shape
        else:
            xs = [x for x, _ in shape[1]]; ys = [y for _, y in shape[1]]
            cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
            rx, ry = (max(xs) - min(xs)) / 2, (max(ys) - min(ys)) / 2
        cells = [(x, y) for y in range(self.S) for x in range(self.S) if m[x, y]]
        big = self.S >= 96
        cols = {c: pk.rgb(c) + (255,) for c in (light, dark, speck or dark)}
        for x, y in self.rng.sample(cells, len(cells) // (density or (9 if big else 14))):
            v = ((y / (self.S - 1)) - cy) / max(ry, 1e-3) - 0.5 * ((x / (self.S - 1)) - cx) / max(rx, 1e-3)
            if v < -0.35:
                c = cols[light]
            elif v > 0.35:
                c = cols[dark]
            elif self.rng.random() < 0.3:
                c = cols[speck or dark]
            else:
                continue
            for ox, oy in (self.rng.choice(self.LEAF) if big else [(0, 0), (1, 0)]):
                if 0 <= x + ox < self.S and 0 <= y + oy < self.S and m[x + ox, y + oy]:
                    px[x + ox, y + oy] = c

    @staticmethod
    def _shift(shape, ox, oy):
        if shape[0] == 'ell':
            return ('ell', shape[1] + ox, shape[2] + oy, shape[3], shape[4])
        return ('poly', [(x + ox, y + oy) for x, y in shape[1]])

    def fill(self, color, mask, clip=None):
        """Flat colour wherever mask (and clip, if given) is set."""
        m, c_, px = mask.load(), clip.load() if clip else None, self.p.img.load()
        c = pk.rgb(color) + (255,)
        for y in range(self.S):
            for x in range(self.S):
                if m[x, y] and (c_ is None or c_[x, y]):
                    px[x, y] = c

    def mask(self, shapes, dx=0):
        m = Image.new('1', (self.S, self.S), 0)
        d = ImageDraw.Draw(m)
        for s in shapes:
            self._draw(d, s, dx)
        return m

    def branch(self, color, pts, width, min_tier=0):
        """A limb as a polyline; hidden on levels past min_tier."""
        if self.tier <= min_tier:
            self.p.line(color, [self.pt(x, y) for x, y in pts], width=self.w(width), sep=self.tier < 2)

    def twigs(self, color, pts):
        if self.tier == 0:
            self.p.px(color, [self.pt(x, y, self.sway) for x, y in pts])

    def dots(self, color, pts, min_tier=0, sway=True):
        if self.tier <= min_tier:
            dx = self.sway if sway else 0
            self.p.px(color, [self.pt(x, y, dx) for x, y in pts])


def render(name, lod, frame=0):
    """One frame of one level as an RGBA image of D[lod] x D[lod]."""
    S = D[lod]
    p = pk.Painter(size=S)
    TREES[name].fn(T(p, lod, frame, seed=zlib.crc32(name.encode()) + lod))
    return p.done() if S >= 16 else p.img


def frames(name, lod):
    """(frames, durations) for the GIF: near levels sway, far ones are static."""
    if tier(lod) == 2:
        return [render(name, lod)], [1000]
    return [render(name, lod, 0), render(name, lod, 1)], [900, 900]


# ================================================================ review sheet
def sheet(names, path, cell=128):
    """One row per kind: every level scaled to `cell` (nearest) to check the
    silhouette holds, then the same levels at native size on a baseline."""
    native_w = sum(D) + 4 * len(D)
    W = len(D) * (cell + 4) + native_w + 16
    img = Image.new('RGBA', (W, len(names) * (cell + 8)), (38, 44, 44, 255))
    for r, n in enumerate(names):
        y = r * (cell + 8) + 4
        for i in range(len(D)):
            f = render(n, i).resize((cell, cell), Image.Resampling.NEAREST)
            img.alpha_composite(f, (i * (cell + 4), y))
        x = len(D) * (cell + 4) + 12
        for i, s in enumerate(D):
            img.alpha_composite(render(n, i), (x, y + cell - s))
            x += s + 4
    img.save(path)
    return path


# ================================================================ the trees
# Moody, a little darker than the old bright props, so they sit with the dark
# creatures. Bark and leaf colours are per kind; pixelkit shades each part.

BARK = '#4A3628'
BARK_LINE = '#2C1F17'


@tree('oak', height=2.8, radius=0.16, where='grass')
def oak(t):
    # roots, trunk, two main limbs, then a broad lumpy crown
    if t.tier == 0:
        t.p.poly(BARK, [t.pt(.34, 1), t.pt(.40, .97), t.pt(.45, .88), t.pt(.46, 1)])
        t.p.poly(BARK, [t.pt(.66, 1), t.pt(.60, .97), t.pt(.55, .88), t.pt(.54, 1)])
    t.trunk(BARK, [(.44, 1), (.56, 1), (.54, .52), (.46, .52)], bark=BARK_LINE)
    t.branch(BARK, [(.49, .6), (.32, .42)], .035, min_tier=1)
    t.branch(BARK, [(.51, .58), (.68, .40)], .035, min_tier=1)
    leaf = '#2F5230'
    crown = [
        ('ell', .30, .40, .20, .15), ('ell', .70, .40, .20, .15),
        ('ell', .50, .22, .26, .18), ('ell', .27, .22, .15, .13),
        ('ell', .73, .23, .15, .13), ('ell', .50, .46, .22, .12),
        ('ell', .50, .12, .16, .10),
    ]
    t.canopy(leaf, crown, speck='#1C3420')
    t.twigs('#3A2A20', [(.10, .38), (.09, .37), (.91, .36), (.92, .35), (.50, .01)])


@tree('pine', height=3.2, radius=0.12, where='grass, hills')
def pine(t):
    t.trunk('#3E2C22', [(.46, 1), (.54, 1), (.52, .70), (.48, .70)], bark='#241910', lines=1)
    tiers = [((.50, .01), .12, .24), ((.50, .14), .20, .40), ((.50, .30), .28, .58), ((.50, .48), .36, .80)]
    shapes = []
    for (x, y), hw, yb in tiers:
        # drooping sides, saw-tooth hem (same outline on every level)
        n = max(3, round(hw * 22))
        hem = [(x + hw - 2 * hw * i / n, yb - (.035 if i % 2 else 0)) for i in range(n + 1)]
        shapes.append(('poly', [(x, y), (x + hw * .55, y + (yb - y) * .6), *hem, (x - hw * .55, y + (yb - y) * .6)]))
    t.canopy('#1F4332', shapes, light='#2F6147', dark='#15302A', speck='#112820')


@tree('birch', height=2.6, radius=0.08, where='grass')
def birch(t):
    t.trunk('#B9B4A6', [(.47, 1), (.53, 1), (.515, .30), (.485, .30)])
    if t.tier <= 1:  # the black bark marks are the birch's whole identity
        marks = [(.49, .92), (.51, .83), (.48, .74), (.50, .66), (.52, .57), (.49, .48), (.51, .40), (.49, .30), (.51, .22)]
        for x, y in marks[:: 1 if t.tier == 0 else 2]:
            t.p.line('#1F1D1B', [t.pt(x - .012, y), t.pt(x + .012, y + .004)], width=t.w(.012))
    crown = [
        ('ell', .50, .16, .15, .12), ('ell', .35, .30, .13, .10), ('ell', .66, .27, .13, .10),
        ('ell', .42, .45, .12, .08), ('ell', .60, .47, .11, .08), ('ell', .31, .52, .07, .05),
    ]
    t.canopy('#4E6A2E', crown, light='#6E8A3C', dark='#34491F', speck='#2A3A18')
    t.twigs('#6E8A3C', [(.20, .30), (.82, .26), (.50, .02)])


@tree('willow', height=3.0, radius=0.18, where='next to water')
def willow(t):
    t.trunk('#3E3024', [(.42, 1), (.60, 1), (.56, .50), (.48, .42), (.46, .50)], bark='#241A12')
    t.branch('#3E3024', [(.50, .48), (.30, .30)], .03, min_tier=1)
    t.branch('#3E3024', [(.54, .46), (.72, .28)], .03, min_tier=1)
    dome = [('ell', .50, .24, .38, .18), ('ell', .30, .30, .20, .14), ('ell', .70, .30, .20, .14)]
    # curtains of hanging fronds down to near the ground
    curtains = [('ell', x, cy, .065, ry) for x, cy, ry in
                ((.13, .52, .30), (.25, .56, .32), (.37, .50, .24), (.63, .50, .24), (.75, .56, .32), (.87, .52, .30))]
    t.canopy('#3A5834', dome + curtains, light='#58784A', dark='#243A22', speck='#1B2C1A')
    if t.tier == 0:  # individual strands
        for x, cy, ry in ((.13, .52, .30), (.25, .56, .32), (.75, .56, .32), (.87, .52, .30)):
            for ox in (-.03, .02):
                t.p.line('#58784A', [t.pt(x + ox, cy - ry * .4, t.sway), t.pt(x + ox, cy + ry * .9, t.sway)])


def frond(base, tip, width, droop):
    """A palm leaf: a curved, tapering polygon from base to tip."""
    (bx, by), (tx, ty) = base, tip
    cx, cy = (bx + tx) / 2, min(by, ty) - droop  # control point lifted: arcs up then droops
    top, bot = [], []
    n = 7
    for i in range(n + 1):
        u = i / n
        x = (1 - u) ** 2 * bx + 2 * (1 - u) * u * cx + u * u * tx
        y = (1 - u) ** 2 * by + 2 * (1 - u) * u * cy + u * u * ty
        hw = width * math.sin(math.pi * min(u * 1.3, 1)) * (1 - u * .6)
        top.append((x, y - hw)); bot.append((x, y + hw))
    return ('poly', top + bot[::-1])


@tree('palm', height=3.0, radius=0.10, where='sand')
def palm(t):
    # leaning, ringed trunk: stacked segments
    segs = [(.50, 1.0), (.52, .84), (.55, .68), (.58, .52), (.60, .36), (.61, .22)]
    for (x0, y0), (x1, y1) in zip(segs, segs[1:]):
        hw0, hw1 = .045 * (1.2 - y0 * .1), .04
        t.p.poly('#6A5236', [t.pt(x0 - hw0, y0), t.pt(x0 + hw0, y0), t.pt(x1 + hw1, y1), t.pt(x1 - hw1, y1)],
                 shade=t.tier < 2, sep=t.tier == 0)
    top = (.61, .20)
    fronds = [frond(top, tip, w, d) for tip, w, d in (
        ((.08, .40), .045, .10), ((.95, .42), .045, .10), ((.22, .08), .04, .06),
        ((.92, .10), .04, .06), ((.30, .52), .035, .02), ((.84, .56), .035, .02), ((.58, .02), .035, .04))]
    t.canopy('#3A6030', fronds, light='#5A8440', dark='#24401E', speck='#1C3218')
    t.dots('#3A2618', [(.57, .24), (.60, .26), (.64, .24)], min_tier=1)  # coconuts


@tree('cypress', height=3.2, radius=0.09, where='grass')
def cypress(t):
    t.trunk('#3A2A20', [(.47, 1), (.53, 1), (.52, .86), (.48, .86)])
    flame = [('poly', [(.50, .01), (.56, .10), (.44, .10)]),
             ('ell', .50, .17, .075, .11), ('ell', .50, .34, .11, .15), ('ell', .50, .54, .13, .17), ('ell', .50, .74, .12, .14)]
    t.canopy('#223F2A', flame, light='#34583A', dark='#152A1C', speck='#10201A')


@tree('maple', height=2.8, radius=0.15, where='tall grass')
def maple(t):
    t.trunk('#43302A', [(.45, 1), (.55, 1), (.535, .55), (.465, .55)], bark='#2A1D18')
    t.branch('#43302A', [(.5, .62), (.34, .46)], .03, min_tier=1)
    t.branch('#43302A', [(.5, .60), (.66, .44)], .03, min_tier=1)
    crown = [
        ('ell', .50, .30, .36, .26), ('ell', .28, .42, .16, .13), ('ell', .72, .42, .16, .13),
        ('ell', .40, .16, .16, .12), ('ell', .62, .16, .16, .12), ('ell', .50, .52, .20, .10),
    ]
    t.canopy('#8E3A1E', crown, light='#C0662A', dark='#5E2414', speck='#D8A040')
    # a few leaves falling, drifting with the sway
    t.dots('#C0662A', [(.20, .66), (.78, .74), (.64, .88)] if t.frame == 0 else [(.22, .70), (.76, .78), (.62, .92)], min_tier=0, sway=False)


@tree('snag', height=2.6, radius=0.10, where='outer stage-3 ring')
def snag(t):
    grey = '#4E4844'
    t.sway = 0  # dead wood doesn't sway
    # broken top, bare angular limbs: the limbs ARE the silhouette, so they stay on every level
    t.trunk(grey, [(.44, 1), (.57, 1), (.535, .20), (.51, .14), (.49, .22), (.465, .24)], bark='#2A2624', lines=2)
    t.branch(grey, [(.49, .62), (.30, .44), (.20, .22)], .035, min_tier=2)
    t.branch(grey, [(.52, .50), (.70, .36), (.78, .14)], .035, min_tier=2)
    t.branch(grey, [(.50, .36), (.38, .22), (.36, .10)], .025, min_tier=2)
    t.branch(grey, [(.30, .44), (.14, .40)], .02, min_tier=1)
    t.branch(grey, [(.70, .36), (.88, .30)], .02, min_tier=1)
    t.twigs('#3A3532', [(.19, .20), (.18, .18), (.79, .12), (.80, .10), (.13, .39), (.89, .29), (.35, .08)])
    if t.tier <= 1:  # a hollow knot
        t.p.ell('#15181F', t.box(.505, .70, .018, .03), shade=False, sep=False)


@tree('wiretree', height=2.8, radius=0.12, where='outer ring (stage-3 hint)')
def wiretree(t):
    """A glitched tech tree: circuit-trace limbs, a crown of dark chips,
    dangling cables and a faint cyan glow (the creatures' Tech accent)."""
    steel, trace, dark = '#2A303C', '#3A4250', '#0B0D12'
    glow = '#3FE0FF' if t.frame == 0 else '#27A8C8'  # the 2nd frame is a glow flicker, not a sway
    t.sway = 0
    t.trunk(steel, [(.44, 1), (.56, 1), (.54, .62), (.53, .40), (.47, .40), (.46, .62)], bark='#15181F', lines=2)
    # right-angle limbs, kept on every level (they're the silhouette)
    limbs = [[(.48, .56), (.32, .56), (.32, .30)], [(.52, .50), (.68, .50), (.68, .32)], [(.50, .42), (.50, .14)],
             [(.32, .44), (.18, .44), (.18, .26)], [(.68, .42), (.84, .42), (.84, .28)]]
    for i, pts in enumerate(limbs):
        t.branch(trace, pts, .03 if i < 3 else .022, min_tier=2)
    # crown: clusters of chips at the limb ends
    chips = []
    for cx, cy, r in ((.32, .24, .09), (.68, .26, .09), (.50, .09, .09), (.18, .21, .07), (.84, .22, .07)):
        chips += [('poly', [(cx - r, cy), (cx, cy - r * .8), (cx + r, cy), (cx, cy + r * .8)]),
                  ('poly', [(cx - r * .9, cy + r * .2), (cx - r * .2, cy + r * .2), (cx - r * .2, cy + r * .9), (cx - r * .9, cy + r * .9)])]
    t.canopy('#23303A', chips, light='#34485A', dark='#151D26', speck=dark)
    ends = []
    if t.tier <= 1:  # cables: sagging between limbs, dangling from the chips
        for a, b in (((.32, .56), (.18, .44)), ((.68, .50), (.84, .42)), ((.32, .40), (.50, .30))):
            t.p.line(dark, [t.pt(*a), t.pt((a[0] + b[0]) / 2, max(a[1], b[1]) + .07), t.pt(*b)], width=t.w(.01), sep=False)
        for x, y0, y1 in ((.26, .30, .62), (.62, .32, .70), (.80, .28, .58), (.44, .16, .38)):
            t.p.line(dark, [t.pt(x, y0), t.pt(x, y1)], width=t.w(.008), sep=False)
            ends.append((x, y1))
    # glow: at the chip clusters and cable ends, one node even far away
    nodes = [(.32, .24), (.68, .26), (.50, .09), (.18, .21), (.84, .22)] if t.tier <= 1 else [(.50, .09)]
    t.dots(glow, nodes + ends, min_tier=2, sway=False)


# ---------------------------------------------------------------- New Zealand
# The NZ flora set (Region.flora = NZ in WorldMap): pōhutukawa on the coast,
# cabbage trees in the paddocks, and tree ferns and nīkau in the bush.

@tree('pohutukawa', height=2.8, radius=0.18, where='coast, sand (NZ)')
def pohutukawa(t):
    """Gnarled, many-trunked and wider than it is tall: a low dark dome of
    grey-green leaves with a few crimson flower tufts."""
    bark = '#4E4038'
    # three twisting trunks leaning out from one base
    t.trunk(bark, [(.43, 1), (.55, 1), (.52, .78), (.47, .60), (.42, .62), (.46, .80)], bark='#2E2622', lines=2)
    t.branch(bark, [(.51, .86), (.62, .70), (.72, .56)], .045, min_tier=1)
    t.branch(bark, [(.46, .82), (.34, .70), (.22, .58)], .04, min_tier=1)
    t.branch(bark, [(.47, .64), (.56, .50)], .03, min_tier=0)
    crown = [
        ('ell', .50, .38, .30, .15), ('ell', .22, .48, .17, .11), ('ell', .78, .47, .17, .11),
        ('ell', .36, .28, .17, .11), ('ell', .65, .29, .17, .11), ('ell', .08, .57, .08, .06),
        ('ell', .92, .56, .08, .06), ('ell', .50, .50, .22, .09),
    ]
    t.canopy('#2F4A2E', crown, light='#52704A', dark='#1C3020', speck='#24382A')
    # a few crimson flower tufts on the sunny side
    t.dots('#C0303A', [(.30, .22), (.44, .20), (.62, .23), (.18, .40), (.73, .38), (.55, .31), (.40, .30), (.84, .48)], min_tier=1)


def star(cx, cy, r, points=9, inner=.42, squash=1.0, lean=0.0):
    """A spiky tuft of sword leaves: a star polygon, flattened by squash."""
    pts = []
    for i in range(points * 2):
        a = -math.pi / 2 + math.pi * i / points
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr + lean * max(0, -math.sin(a)) * r, cy + math.sin(a) * rr * squash))
    return ('poly', pts)


@tree('cabbage', height=2.6, radius=0.07, where='paddocks, tall grass (NZ)')
def cabbage(t):
    """Tī kōuka: a slim grey trunk forking into a few stems, each ending in a
    spiky tuft of sword leaves over a skirt of dead brown ones."""
    grey = '#6A6258'
    t.trunk(grey, [(.47, 1), (.53, 1), (.52, .58), (.48, .58)], bark='#4A443C', lines=1)
    heads = [(.34, .24, .15), (.63, .17, .15), (.74, .38, .12)]
    for (hx, hy, _), base in zip(heads, [(.49, .60), (.51, .58), (.51, .62)]):
        t.branch(grey, [base, (hx, hy + .06)], .03, min_tier=2)
    for hx, hy, r in heads:  # the dead-leaf skirts hang under each tuft
        if t.tier <= 1:
            t.p.poly('#6E5E3E', [t.pt(hx - r * .45, hy + .02, t.sway), t.pt(hx + r * .45, hy + .02, t.sway),
                                 t.pt(hx + r * .3, hy + r * .9), t.pt(hx - r * .3, hy + r * .9)], shade=t.tier == 0)
    t.canopy('#5A7038', [star(hx, hy, r, 11 if t.tier == 0 else 7, .38, .85) for hx, hy, r in heads],
             light='#7E9450', dark='#3C4E26', speck='#8A9A5A')


@tree('ponga', height=2.4, radius=0.08, where='native bush (NZ)')
def ponga(t):
    """Silver tree fern: a dark fibrous trunk under a wide crown of arching,
    feathery fronds, silver underneath."""
    t.trunk('#3A2C22', [(.465, 1), (.535, 1), (.53, .42), (.47, .42)], bark='#22190F', lines=2)
    if t.tier <= 1:  # old fronds hang down the trunk
        t.p.poly('#5A4630', [t.pt(.45, .42), t.pt(.55, .42), t.pt(.57, .56), t.pt(.43, .56)], shade=t.tier == 0)
    top = (.50, .40)
    fronds = [frond(top, tip, w, d) for tip, w, d in (
        ((.03, .56), .05, .08), ((.97, .56), .05, .08), ((.12, .30), .05, .10), ((.88, .30), .05, .10),
        ((.30, .12), .045, .08), ((.70, .12), .045, .08), ((.50, .06), .04, .04), ((.24, .64), .04, .02), ((.76, .64), .04, .02))]
    t.canopy('#3C6230', fronds, light='#5E8646', dark='#264420', speck='#A7B3A0')
    if t.tier == 0:  # the midribs
        for tip in ((.03, .56), (.97, .56), (.12, .30), (.88, .30), (.30, .12), (.70, .12)):
            t.p.line('#243A1E', [t.pt(*top, t.sway), t.pt((top[0] + tip[0]) / 2, min(top[1], tip[1]) - .05, t.sway)], sep=False)


@tree('nikau', height=2.8, radius=0.08, where='native bush, coast (NZ)')
def nikau(t):
    """Nīkau palm: a straight ringed trunk, a smooth green crownshaft, and
    stiff fronds that point up like a feather duster."""
    t.trunk('#6E6A58', [(.475, 1), (.525, 1), (.52, .38), (.48, .38)])
    if t.tier <= 1:  # leaf-scar rings
        for y in (.9, .8, .7, .6, .5, .42):
            t.p.line('#4E4A3C', [t.pt(.475, y), t.pt(.525, y)], sep=False)
    if t.tier < 2:  # the smooth green crownshaft (far away it's just trunk)
        t.p.ell('#6E8A4E', t.box(.50, .34, .035, .07), sep=False)
    t.dots('#8A3A50', [(.47, .42), (.53, .43)], min_tier=0)  # flowers under it
    top = (.50, .28)
    # stiff fronds held up and out: a shuttlecock, not a drooping coconut palm
    fronds = [frond(top, tip, w, d) for tip, w, d in (
        ((.24, .03), .05, .03), ((.76, .03), .05, .03), ((.38, .00), .04, .01), ((.62, .00), .04, .01),
        ((.10, .16), .05, .04), ((.90, .16), .05, .04), ((.50, .00), .035, .0))]
    t.canopy('#3A5A30', fronds, light='#5A7A42', dark='#243E1C', speck='#1E3418')


# ---------------------------------------------------------------- far-off houses
# A town on the horizon, in the house style of the player's country
# (Region.houses): prop_house_<style>_<n>.gif, 8 variants per style, drawn
# small at 1x because they're only ever seen far away. WINDOW pixels are
# swapped by the renderer (Skyline.WINDOW): lit at night, dark glass by day.

WINDOW = '#FFE8A0'
HOUSE_VARIANTS = 8  # Skyline.HOUSE_VARIANTS must match

HOUSE_STYLES = {
    # weatherboard villas and bungalows under corrugated iron
    'nz': dict(walls=['#E6E2D6', '#D8CCA6', '#A6B8C4', '#B2C09E', '#E0D4C0'],
               roofs=['#8A2E28', '#3E5E3A', '#3A3E44', '#8A9096', '#3A5470', '#7A3A2A'],
               shapes=['hip', 'hip', 'gable', 'gable'], two=0.12, width=(13, 20), boards=True, iron=True, veranda=0.5, chimney=0.3),
    # brick and render terraces under tile and slate
    'euro': dict(walls=['#8A4A38', '#9A5A40', '#D6CAAE', '#7E4634', '#C8B89A'],
                 roofs=['#A0482C', '#3E4450', '#8A3E28', '#4A4E58'],
                 shapes=['gable', 'gable', 'hip'], two=0.7, width=(10, 15), boards=False, iron=False, veranda=0.0, chimney=0.8),
    # clapboard houses with asphalt shingles and garages
    'us': dict(walls=['#DCD6C0', '#A8B4BE', '#D8C890', '#E8E6E0', '#B8A48A'],
               roofs=['#4A4E54', '#5A4638', '#3E4248'],
               shapes=['gable', 'gable', 'hip'], two=0.45, width=(14, 22), boards=True, iron=False, veranda=0.25, chimney=0.2),
    # Falu red and ochre timber under steep dark roofs
    'nordic': dict(walls=['#8A2A20', '#8A2A20', '#C89A3A', '#E4E0D4', '#6A7A5A'],
                   roofs=['#2A2A2E', '#3A3434', '#2E3A2E'],
                   shapes=['steep', 'steep', 'gable'], two=0.35, width=(11, 17), boards=True, iron=False, veranda=0.0, chimney=0.4),
    # pastel concrete with flat roofs and the odd tin one
    'tropical': dict(walls=['#E0A6A0', '#86C6BE', '#E6D080', '#E8E4DA', '#B0C88A'],
                     roofs=['#C8C4B8', '#9A5A3A', '#8A9096'],
                     shapes=['flat', 'flat', 'gable'], two=0.4, width=(12, 18), boards=False, iron=True, veranda=0.3, chimney=0.0),
}


def house(style, n):
    """One far-off house as an RGBA image, sized to fit (about 12-24 px)."""
    st = HOUSE_STYLES[style]
    rng = random.Random(zlib.crc32(f'{style}:{n}'.encode()))
    wall, roof = rng.choice(st['walls']), rng.choice(st['roofs'])
    shape = rng.choice(st['shapes'])
    two = rng.random() < st['two']
    bw = rng.randint(*st['width'])
    wall_h = 9 if two else 5
    roof_h = {'hip': max(3, bw // 4), 'gable': max(4, bw // 3), 'steep': max(5, bw // 2), 'flat': 1}[shape]
    W, H = bw + 4, wall_h + roof_h + 3
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, x1 = 2, 2 + bw - 1
    wy0, wy1 = H - wall_h, H - 1
    # walls, the right end a shade darker (light from the left)
    d.rectangle((x0, wy0, x1, wy1), fill=pk.rgb(wall))
    d.rectangle((x1 - max(1, bw // 6), wy0, x1, wy1), fill=pk.lo(wall))
    if st['boards']:  # weatherboards / clapboard
        for y in range(wy0 + 1, wy1, 2):
            d.line((x0, y, x1, y), fill=pk.shift(wall, -0.06))
    # windows (the renderer lights these at night) and a door
    floors = [wy0 + 1] + ([wy0 + 5] if two else [])
    door_x = rng.choice([x0 + 2, x1 - 3, (x0 + x1) // 2])
    for fy in floors:
        for wx in range(x0 + 2, x1 - 1, 4):
            if abs(wx - door_x) <= 1 and fy == floors[-1]:
                continue
            d.rectangle((wx, fy + 1, wx + 1, fy + 2), fill=pk.rgb(WINDOW))
    d.rectangle((door_x, wy1 - 3, door_x + 1, wy1), fill=pk.ink(wall))
    # the roof: eaves overhang a pixel each side
    ry1 = wy0 - 1
    if shape == 'flat':
        d.rectangle((x0 - 1, ry1, x1 + 1, ry1), fill=pk.rgb(roof))
    else:
        top = ry1 - roof_h + 1
        mid = (x0 + x1) / 2
        if shape == 'hip':  # the long side faces you: a trapezoid
            pts = [(x0 - 1, ry1), (x1 + 1, ry1), (x1 - roof_h + 1, top), (x0 + roof_h - 1, top)]
        else:  # a gable end facing you
            pts = [(x0 - 1, ry1), (x1 + 1, ry1), (mid, top)]
        d.polygon(pts, fill=pk.rgb(roof))
        # lit left slope, shaded right
        d.polygon([(mid, top), (x1 + 1, ry1), (mid, ry1)] if shape != 'hip' else [(mid, top), (x1 - roof_h + 1, top), (x1 + 1, ry1), (mid, ry1)],
                  fill=pk.lo(roof))
        if st['iron']:  # corrugated iron ribs
            px = img.load()
            for x in range(x0, x1 + 1, 2):
                for y in range(top, ry1 + 1):
                    if px[x, y][3]:
                        px[x, y] = pk.shift(roof, 0.05) + (255,)
        if rng.random() < st['chimney']:
            cx = int(rng.choice([x0 + 2, x1 - 3]))
            ctop = max(0, top + roof_h // 3 - 3)
            d.rectangle((cx, ctop, cx + 1, ry1 - 1), fill=pk.rgb('#7A4436'))
    if rng.random() < st['veranda']:  # a veranda roof across the front with posts
        vy = wy0 + (5 if two else 2)
        d.line((x0 - 1, vy, x1 + 1, vy), fill=pk.rgb(roof))
        for x in (x0, (x0 + x1) // 2, x1):
            d.line((x, vy + 1, x, wy1), fill=pk.rgb('#E8E4DA'))
    return pk.outline(img)


def house_sheet(path, cell=4):
    """Every style's variants in rows, scaled up `cell` times, then at native size on a baseline."""
    rows = list(HOUSE_STYLES)
    imgs = {s: [house(s, n) for n in range(HOUSE_VARIANTS)] for s in rows}
    W = max(sum(i.width + 2 for i in v) for v in imgs.values())
    H = max(i.height for v in imgs.values() for i in v)
    out = Image.new('RGBA', (W * cell + W + 24, len(rows) * (H * cell + 8)), (120, 150, 170, 255))
    for r, s in enumerate(rows):
        x = 4; y = r * (H * cell + 8) + 4
        for i in imgs[s]:
            out.alpha_composite(i.resize((i.width * cell, i.height * cell), Image.Resampling.NEAREST), (x, y + (H - i.height) * cell))
            x += (i.width + 2) * cell
        x = W * cell + 16
        for i in imgs[s]:
            out.alpha_composite(i, (x, y + H * cell - i.height))
            x += i.width
    out.save(path)
    return path
