"""Tiny pixel-art toolkit for the sprite autogen pipeline.

Sprites are drawn on a 32x32 logical grid out of flat "parts" (ellipses,
polygons, rects). Each part gets automatic 3-tone shading (light from the
top-left, hue-shifted), a dark line where it overlaps parts behind it, and
the finished silhouette gets a selective outline. Everything is Pillow only.
"""
import colorsys
from PIL import Image, ImageDraw

SIZE = 32
CLEAR = (0, 0, 0, 0)


# ---------------------------------------------------------------- colours
def rgb(c):
    if isinstance(c, tuple):
        return c[:3]
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def shift(c, light, hue=0.0, sat=0.0):
    """Lighten/darken in HLS with a hue shift (pixel-art style ramps)."""
    r, g, b = (v / 255 for v in rgb(c))
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    h = (h + hue) % 1.0
    l = max(0.0, min(1.0, l + light))
    s = max(0.0, min(1.0, s + sat))
    return tuple(round(v * 255) for v in colorsys.hls_to_rgb(h, l, s))


def hi(c):
    return shift(c, 0.14, hue=0.02, sat=0.05)


def lo(c):
    return shift(c, -0.16, hue=-0.03, sat=-0.02)


def ink(c):
    return shift(c, -0.42, hue=-0.04, sat=-0.1)


# ---------------------------------------------------------------- painter
class Painter:
    """Draws parts onto a 32x32 RGBA canvas.

    mirror=True draws every primitive twice, reflected about the centre
    column, which is how the front-facing views stay symmetric.
    """

    def __init__(self, mirror=False):
        self.img = Image.new('RGBA', (SIZE, SIZE), CLEAR)
        self.mirror = mirror

    # -- helpers
    def _mask(self, fn):
        m = Image.new('1', (SIZE, SIZE), 0)
        fn(ImageDraw.Draw(m))
        if self.mirror:
            flipped = m.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
            m = Image.composite(Image.new('1', m.size, 1), m, flipped)
        return m

    def part(self, color, fn, shade=True, sep=True):
        m = self._mask(fn)
        px = self.img.load()
        mp = m.load()
        base, light, dark, line = rgb(color), hi(color), lo(color), ink(color)

        def inside(x, y):
            return 0 <= x < SIZE and 0 <= y < SIZE and mp[x, y]

        cells = [(x, y) for y in range(SIZE) for x in range(SIZE) if mp[x, y]]
        ys = [y for _, y in cells]
        tall = cells and (max(ys) - min(ys)) >= 3
        # separation line: pixels behind this part that touch its edge
        if sep:
            for x, y in cells:
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < SIZE and 0 <= ny < SIZE and not mp[nx, ny] and px[nx, ny][3]:
                        px[nx, ny] = line + (255,)
        for x, y in cells:
            col = base
            if shade and tall:
                if not inside(x, y + 1) or (not inside(x + 1, y) and not inside(x, y + 2)):
                    col = dark
                elif not inside(x, y - 1) or (not inside(x - 1, y) and not inside(x, y - 2)):
                    col = light
            px[x, y] = col + (255,)

    # -- primitives (boxes are inclusive x0, y0, x1, y1)
    def ell(self, color, box, **kw):
        self.part(color, lambda d: d.ellipse(box, fill=1), **kw)

    def rect(self, color, box, **kw):
        self.part(color, lambda d: d.rectangle(box, fill=1), **kw)

    def poly(self, color, pts, **kw):
        self.part(color, lambda d: d.polygon(pts, fill=1), **kw)

    def line(self, color, pts, width=1, **kw):
        kw.setdefault('shade', False)
        self.part(color, lambda d: d.line(pts, fill=1, width=width), **kw)

    def px(self, color, pts):
        """Flat pixels, no shading or separation (eyes, markings, glints)."""
        c = rgb(color) + (255,)
        p = self.img.load()
        for x, y in pts:
            for xx in ((x, SIZE - 1 - x) if self.mirror else (x,)):
                if 0 <= xx < SIZE and 0 <= y < SIZE:
                    p[xx, y] = c

    # -- faces
    def eye(self, x, y, mood='open', big=False, iris='#101018', side=True):
        """Eye with top-left corner at (x, y). Side view looks right."""
        W = '#F4F4F8'
        if mood == 'closed':
            self.px(iris, [(x, y + 1), (x + 1, y + 1)] + ([(x + 2, y + 1)] if big else []))
        elif mood == 'hurt':
            self.px(iris, [(x, y), (x + 1, y + 1), (x, y + 2)] if side else [(x, y), (x + 1, y + 1), (x, y + 2), (x + 2, y), (x + 2, y + 2)])
        elif mood == 'happy':
            self.px(iris, [(x, y + 1), (x + 1, y), (x + 2, y + 1)] if big else [(x, y + 1), (x + 1, y)])
        elif mood == 'angry':
            if big:
                self.px(W, [(x, y + 1), (x + 1, y + 1), (x + 2, y + 1), (x, y + 2), (x + 1, y + 2), (x + 2, y + 2)])
                self.px(iris, [(x, y), (x + 1, y), (x + 2, y + 1) if side else (x + 1, y + 1), (x + 2, y + 2) if side else (x + 1, y + 2)])
            else:
                self.px(W, [(x, y + 1), (x + 1, y + 1)])
                self.px(iris, [(x, y), (x + 1, y + 1) if side else (x, y + 1)])
        else:
            if big:
                self.px(W, [(x + i, y + j) for i in range(3) for j in range(3)])
                pc = x + 2 if side else x + 1
                self.px(iris, [(pc, y + 1), (pc, y + 2)])
                self.px('#FFFFFF', [(x, y)])
            else:
                self.px(W, [(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)])
                self.px(iris, [(x + 1, y), (x + 1, y + 1)] if side else [(x, y + 1)])

    def done(self):
        return outline(self.img)


def outline(img, color=None):
    """Selective 1px outline around the silhouette (darkened neighbour)."""
    out = img.copy()
    src = img.load()
    dst = out.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if src[x, y][3]:
                continue
            for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if 0 <= nx < SIZE and 0 <= ny < SIZE and src[nx, ny][3]:
                    dst[x, y] = (rgb(color) if color else ink(src[nx, ny][:3])) + (255,)
                    break
    return out


# ---------------------------------------------------------------- frame ops
BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]


def translate(img, dx, dy):
    out = Image.new('RGBA', img.size, CLEAR)
    out.paste(img, (dx, dy), img)
    return out


def flash(img, color='#FFFFFF'):
    out = img.copy()
    p = out.load()
    c = rgb(color) + (255,)
    for y in range(img.height):
        for x in range(img.width):
            if p[x, y][3]:
                p[x, y] = c
    return out


def tint(img, color, amount):
    out = img.copy()
    p = out.load()
    tr, tg, tb = rgb(color)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = p[x, y]
            if a:
                p[x, y] = (round(r + (tr - r) * amount), round(g + (tg - g) * amount), round(b + (tb - b) * amount), 255)
    return out


def grey(img, amount=1.0):
    out = img.copy()
    p = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = p[x, y]
            if a:
                v = round(0.3 * r + 0.59 * g + 0.11 * b)
                p[x, y] = (round(r + (v - r) * amount), round(g + (v - g) * amount), round(b + (v - b) * amount), 255)
    return out


def dither(img, keep):
    """Ordered-dither fade: keep ~`keep` (0..1) of the opaque pixels."""
    out = img.copy()
    p = out.load()
    for y in range(img.height):
        for x in range(img.width):
            if p[x, y][3] and BAYER4[y % 4][x % 4] >= keep * 16:
                p[x, y] = CLEAR
    return out


def squash(img, sy, sx=1.0, ground=30):
    """Scale about the bottom-centre (ground line), nearest-neighbour."""
    bbox = img.getbbox()
    if not bbox:
        return img.copy()
    crop = img.crop(bbox)
    w = max(1, round(crop.width * sx))
    h = max(1, round(crop.height * sy))
    crop = crop.resize((w, h), Image.Resampling.NEAREST)
    out = Image.new('RGBA', img.size, CLEAR)
    cx = (bbox[0] + bbox[2]) // 2
    out.paste(crop, (cx - w // 2, bbox[3] - h), crop)
    return out


def over(*layers):
    out = Image.new('RGBA', layers[0].size, CLEAR)
    for layer in layers:
        out.alpha_composite(layer)
    return out


def sprinkle(img, pts, color):
    out = img.copy()
    p = out.load()
    c = rgb(color) + (255,)
    for x, y in pts:
        if 0 <= x < img.width and 0 <= y < img.height:
            p[x, y] = c
    return out


# ---------------------------------------------------------------- GIF out
def save_gif(frames, durations, path, scale=2):
    """Write frames with one shared palette, index 0 = transparent."""
    colours = {}
    for f in frames:
        for c in f.getdata():
            if c[3] and c[:3] not in colours:
                colours[c[:3]] = len(colours) + 1
    if len(colours) > 255:
        raise ValueError(f'{path}: {len(colours)} colours, GIF max is 255')
    palette = [0, 0, 0]
    for c, _ in sorted(colours.items(), key=lambda kv: kv[1]):
        palette.extend(c)
    palette.extend([0, 0, 0] * (256 - len(palette) // 3))
    out = []
    for f in frames:
        f = f.resize((f.width * scale, f.height * scale), Image.Resampling.NEAREST)
        p = Image.new('P', f.size, 0)
        p.putpalette(palette)
        src, dst = f.load(), p.load()
        for y in range(f.height):
            for x in range(f.width):
                c = src[x, y]
                if c[3]:
                    dst[x, y] = colours[c[:3]]
        p.info['transparency'] = 0
        out.append(p)
    out[0].save(path, save_all=True, append_images=out[1:], duration=durations,
                loop=0, disposal=2, transparency=0, optimize=False)
