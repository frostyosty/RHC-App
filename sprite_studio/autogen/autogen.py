#!/usr/bin/env python3
"""Batch-generate every sprite GIF in the Sprite Studio matrix.

Each row in designs.py is drawn once per pose; the animations below are
built from those poses plus frame transforms (lunge, flash, squash, fade),
so every animation of a beast is guaranteed to be the same creature.

  python3 sprite_studio/autogen/autogen.py              # everything
  python3 sprite_studio/autogen/autogen.py --only cacheon,titan
  python3 sprite_studio/autogen/autogen.py --anims idle,attack
  python3 sprite_studio/autogen/autogen.py --skip-existing
  python3 sprite_studio/autogen/autogen.py --preview out.png
  python3 sprite_studio/autogen/autogen.py --turnaround views.png --only cacheon

Output: rhc-android/app/src/main/res/drawable-nodpi/spr_<beast>_<anim>.gif
and fx_<beast>.gif, 64x64 (32x32 art at 2x), the same names the Studio uses.
"""
import argparse
import math
import os
import re
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pixelkit as pk  # noqa: E402
from designs import DESIGNS, PROPS, Pose  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SAVE_DIR = os.path.join(ROOT, 'rhc-android/app/src/main/res/drawable-nodpi')
MODELS_FILE = os.path.join(ROOT, 'rhc-android/app/src/main/java/com/rockhard/blocker/GameModels.kt')
ANIMATIONS = ['idle', 'attack', 'hit', 'evade', 'faint', 'victory', 'explore', 'fx', 'walk_front', 'attack_front']
HOLD_FOREVER = 60000  # GifView loops by time, so a terminal pose just holds a long frame


def draw(name, pose):
    d = DESIGNS[name]
    p = pk.Painter(mirror=pose.symmetric)
    d.fn(p, pose)
    return p.done() if d.outline else p.img


def bend(img, amount=1):
    """Crouch: shift everything above the knees down, feet stay planted."""
    if not amount:
        return img
    bbox = img.getbbox()
    if not bbox:
        return img
    knee = bbox[3] - 4
    out = img.copy()
    top = img.crop((0, 0, img.width, knee))
    out.paste(pk.CLEAR, (0, 0, img.width, knee + amount))
    out.paste(top, (0, amount), top)
    return out


def bob(d, img, i):
    """Idle breathing: floaters hover, walkers crouch."""
    if d.float:
        return pk.translate(img, 0, [0, -1, -1, -1, 0, 0, 1, 0][i % 8])
    return bend(img, [0, 0, 1, 1, 1, 1, 0, 0][i % 8])


# ---------------------------------------------------------------- overlays
def star(img, cx, cy, r, color='#FFF3A0', core='#FFFFFF'):
    pts = [(cx + dx, cy) for dx in range(-r, r + 1)] + [(cx, cy + dy) for dy in range(-r, r + 1)]
    pts += [(cx + k, cy + k) for k in (-r + 1, r - 1)] + [(cx + k, cy - k) for k in (-r + 1, r - 1)]
    img = pk.sprinkle(img, pts, color)
    return pk.sprinkle(img, [(cx, cy)], core)


def sparkles(img, i):
    spots = [(4, 6), (26, 4), (28, 16), (3, 18), (15, 2)]
    for k, (x, y) in enumerate(spots):
        if (i + k) % 3 == 0:
            img = pk.sprinkle(img, [(x, y - 1), (x - 1, y), (x, y), (x + 1, y), (x, y + 1)], '#FFE680')
            img = pk.sprinkle(img, [(x, y)], '#FFFFFF')
    return img


def speed_lines(img, y0, x0=0):
    for k, y in enumerate((y0, y0 + 5, y0 + 10)):
        img = pk.sprinkle(img, [(x0 + k + j, y) for j in range(4)], '#FFFFFF')
    return img


def dizzy(img, i):
    bbox = img.getbbox() or (8, 8, 24, 24)
    cx, cy = (bbox[0] + bbox[2]) // 2, max(2, bbox[1] - 1)
    for k in range(3):
        a = (i * 2 + k * 2.1)
        x, y = round(cx + 5 * math.cos(a)), round(cy + 1.5 * math.sin(a))
        img = pk.sprinkle(img, [(x, y), (x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)], '#FFE14F')
    return img


def ghost(img, color='#7FD4FF'):
    return pk.dither(pk.tint(img, color, 0.6), 0.4)


# ---------------------------------------------------------------- anims
def a_idle(d, n, view='side'):
    frames = []
    for i in range(8):
        pose = Pose(view=view, flap=[0, -1, 0, 1][i % 4], eyes='closed' if i == 6 else 'open', t=i)
        frames.append(bob(d, draw(n, pose), i))
    return frames, [130] * 8


def a_attack(d, n):
    wind = draw(n, Pose(eyes='angry', flap=-1, t=0))
    bite = draw(n, Pose(eyes='angry', mouth=True, flap=1, t=1))
    seq = [(wind, -2, 70), (wind, -3, 70), (bite, 2, 50), (bite, 4, 100), (bite, 3, 60), (wind, 1, 50), (wind, 0, 60)]
    frames = []
    for k, (img, dx, _) in enumerate(seq):
        f = pk.translate(img, dx, 0)
        if k in (2, 3):
            f = pk.over(speed_lines(Image.new('RGBA', f.size, pk.CLEAR), 12, 0), f)
        if k == 3:
            f = star(f, 29, 14, 2)
        frames.append(f)
    return frames, [t for _, _, t in seq]


def a_hit(d, n):
    hurt = draw(n, Pose(eyes='hurt', flap=1))
    ok = draw(n, Pose())
    frames = [
        star(pk.translate(pk.flash(hurt), -2, 0), 26, 12, 3),
        pk.translate(hurt, -3, 0),
        pk.translate(pk.flash(hurt), -2, 0),
        pk.translate(hurt, -1, 0),
        hurt,
        ok,
    ]
    return frames, [60, 80, 60, 90, 100, 60]


def a_evade(d, n):
    base = draw(n, Pose(flap=-1))
    frames = [
        pk.over(ghost(base), pk.translate(base, -2, 0)),
        pk.over(ghost(pk.translate(base, -1, 0)), pk.translate(base, -4, 0)),
        pk.translate(base, -4, 0),
        pk.translate(base, -3, 0),
        pk.translate(base, -2, 0),
        base,
    ]
    return frames, [60, 80, 120, 70, 70, 80]


def a_faint(d, n):
    hurt = draw(n, Pose(eyes='hurt', flap=1))
    out = draw(n, Pose(eyes='closed', flap=1))
    ground = lambda img: pk.translate(img, 0, 29 - (img.getbbox() or (0, 0, 0, 29))[3] + 1) if d.float else img  # noqa: E731
    frames = [
        dizzy(pk.translate(hurt, 1, 0), 0),
        dizzy(pk.translate(hurt, -1, 0), 1),
        dizzy(hurt, 2),
        pk.grey(bend(ground(out), 2), 0.4),
        pk.grey(pk.squash(ground(out), 0.8), 0.7),
        pk.grey(pk.squash(ground(out), 0.65), 0.9),
    ]
    return frames, [90, 90, 110, 110, 130, HOLD_FOREVER]


def a_victory(d, n):
    frames = []
    hops = [0, -2, -4, -5, -4, -2, 0, 0]
    for i, dy in enumerate(hops):
        pose = Pose(eyes='happy', mouth=i in (2, 3, 4), flap=[-1, 1][i % 2], t=i)
        img = pk.translate(draw(n, pose), 0, dy)
        if dy == 0 and not d.float and i == 7:
            img = pk.squash(img, 0.9, 1.1)
        frames.append(sparkles(img, i))
    return frames, [110] * 8


def a_walk(d, n, view='side'):
    frames = []
    for i in range(8):
        pose = Pose(view=view, step=i % 4, flap=[-1, 0, 1, 0][i % 4], t=i)
        img = draw(n, pose)
        if d.float:
            img = pk.translate(img, 0, [0, -1, 0, 1][i % 4])
        else:
            img = bend(img, 1 if i % 2 == 0 else 0)
        frames.append(img)
    return frames, [110] * 8


def a_explore(d, n):
    return a_walk(d, n)


def a_walk_front(d, n):
    return a_walk(d, n, 'front')


def a_attack_front(d, n):
    wind = draw(n, Pose(view='front', eyes='angry', flap=-1))
    bite = draw(n, Pose(view='front', eyes='angry', mouth=True, flap=1))
    frames = [
        wind,
        pk.translate(wind, 0, -2),
        pk.squash(bite, 1.1, 1.1),
        star(star(pk.squash(bite, 1.2, 1.2), 4, 6, 2), 27, 6, 2),
        pk.squash(bite, 1.1, 1.1),
        wind,
    ]
    return frames, [70, 80, 50, 110, 60, 80]


# ---------------------------------------------------------------- fx
def a_fx(d, n):
    col = d.color
    frames = []
    for i in range(8):
        img = Image.new('RGBA', (32, 32), pk.CLEAR)
        dr = ImageDraw.Draw(img)
        style = d.fx
        if style == 'bits':
            r = 5 + (i % 2)
            dr.ellipse((16 - r, 16 - r, 15 + r, 15 + r), fill=pk.rgb(col) + (255,))
            dr.ellipse((13, 13, 18, 18), fill=(230, 255, 255, 255))
            for k in range(6):
                a = i * 0.6 + k * math.pi / 3
                x, y = round(16 + 11 * math.cos(a)), round(16 + 11 * math.sin(a))
                if k % 2:
                    dr.rectangle((x, y, x + 1, y + 2), fill=pk.rgb(col) + (255,))
                else:
                    dr.rectangle((x - 1, y, x + 1, y + 2), outline=pk.rgb(col) + (255,))
        elif style == 'heart':
            s = [1.0, 1.15, 1.25, 1.15, 1.0, 0.9, 0.85, 0.9][i]
            pts = []
            for k in range(40):
                t = k / 40 * 2 * math.pi
                hx = 16 * math.sin(t) ** 3
                hy = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
                pts.append((16 + hx * 0.6 * s, 16 + hy * 0.6 * s))
            dr.polygon(pts, fill=pk.rgb(col) + (255,))
            dr.rectangle((10, 11, 11, 12), fill=(255, 220, 235, 255))
            for k in range(3):
                x, y = [(4, 6), (27, 9), (25, 27)][k]
                if (i + k) % 3 == 0:
                    dr.point([(x, y - 1), (x - 1, y), (x, y), (x + 1, y), (x, y + 1)], fill=(255, 255, 255, 255))
        elif style == 'slash':
            a0 = -2.4 + i * 0.35
            for w, c in ((3, pk.rgb(col)), (1, (240, 230, 255))):
                arc = [(16 + 12 * math.cos(a0 + k * 0.12), 16 + 12 * math.sin(a0 + k * 0.12)) for k in range(14)]
                dr.line(arc, fill=c + (255,), width=w)
            for k in range(2):
                x, y = round(16 + 7 * math.cos(a0 + 1 + k)), round(16 + 7 * math.sin(a0 + 1 + k))
                dr.point([(x, y)], fill=(107, 255, 122, 255))
        elif style == 'play':
            for k in range(2):
                r = (i * 2 + k * 8) % 16
                if r > 4:
                    dr.ellipse((16 - r, 16 - r, 15 + r, 15 + r), outline=pk.rgb(pk.lo(col)) + (255,))
            dr.ellipse((8, 8, 23, 23), fill=pk.rgb(col) + (255,))
            dr.polygon([(13, 11), (13, 20), (21, 15)], fill=(255, 255, 255, 255))
        elif style == 'wind':
            for k in range(3):
                a = i * 0.8 + k * 2.1
                arc = [(16 + (4 + j) * math.cos(a + j * 0.35), 16 + (4 + j) * 0.7 * math.sin(a + j * 0.35)) for j in range(10)]
                dr.line(arc, fill=pk.rgb(col if k else '#FFFFFF') + (255,), width=2 if k == 0 else 1)
        elif style == 'coin':
            w = [7, 6, 4, 1, 4, 6, 7, 7][i]
            dr.ellipse((16 - w, 8, 15 + w, 23), fill=(242, 200, 75, 255))
            if w > 2:
                dr.ellipse((17 - w, 10, 14 + w, 21), outline=(200, 150, 40, 255))
                dr.line([(16, 12), (16, 19)], fill=(200, 150, 40, 255))
            dr.point([(16 - w + 1, 11)], fill=(255, 255, 255, 255))
        elif style == 'nova':
            r = [3, 5, 7, 9, 11, 9, 7, 5][i]
            for k in range(8):
                a = k * math.pi / 4
                ln = r if k % 2 == 0 else r * 0.6
                dr.line([(16, 16), (16 + ln * math.cos(a), 16 + ln * math.sin(a))], fill=pk.rgb(col) + (255,))
            dr.rectangle((14, 9, 17, 22), fill=(255, 246, 216, 255))
            dr.rectangle((10, 13, 21, 16), fill=(255, 246, 216, 255))
        elif style == 'claw':
            for k in range(3):
                prog = min(1.0, max(0.0, (i - k + 1) / 3))
                if prog <= 0 or i >= 7:
                    continue
                x0, y0 = 8 + k * 6, 5
                dr.line([(x0, y0), (x0 + 8 * prog, y0 + 22 * prog)], fill=(255, 255, 255, 255), width=2)
                dr.line([(x0 + 1, y0), (x0 + 1 + 8 * prog, y0 + 22 * prog)], fill=pk.rgb(col) + (255,))
        elif style == 'net':
            r = [4, 6, 8, 10, 12, 13, 13, 13][i]
            for k in range(-2, 3):
                dr.line([(16 + k * r / 2.5, 16 - r), (16 + k * r / 2.5, 16 + r)], fill=pk.rgb(col) + (255,))
                dr.line([(16 - r, 16 + k * r / 2.5), (16 + r, 16 + k * r / 2.5)], fill=pk.rgb(col) + (255,))
            for x, y in ((16 - r, 16 - r), (16 + r, 16 - r), (16 - r, 16 + r), (16 + r, 16 + r)):
                dr.rectangle((x - 1, y - 1, x + 1, y + 1), fill=(94, 111, 130, 255))
        elif style == 'laser':
            h = [1, 2, 3, 2, 3, 2, 1, 2][i]
            dr.rectangle((0, 16 - h - 1, 31, 15 + h + 1), fill=pk.rgb(col) + (255,))
            dr.rectangle((0, 16 - h, 31, 15 + h), fill=(255, 220, 220, 255))
            dr.ellipse((0, 10, 7, 21), fill=(255, 255, 255, 255))
        frames.append(pk.outline(img) if d.outline else img)
    return frames, [70] * 8


ANIM_FNS = {
    'idle': a_idle, 'attack': a_attack, 'hit': a_hit, 'evade': a_evade, 'faint': a_faint,
    'victory': a_victory, 'explore': a_explore, 'fx': a_fx,
    'walk_front': a_walk_front, 'attack_front': a_attack_front,
}

# 8-direction art for the 3D Wilds: walk + idle for each extra view a design
# draws. The side walk is `explore` and the side idle is `idle`, and
# walk_front already exists, so only the missing combinations are added.
VIEW_ANIMS = {}
for _v in ('front', 'fq', 'bq', 'back'):
    if _v != 'front':
        VIEW_ANIMS[f'walk_{_v}'] = (_v, lambda d, n, v=_v: a_walk(d, n, v))
    VIEW_ANIMS[f'idle_{_v}'] = (_v, lambda d, n, v=_v: a_idle(d, n, v))
ANIM_FNS.update({a: fn for a, (_, fn) in VIEW_ANIMS.items()})


def anims_for(d, anims):
    """The requested animations this design can draw (view anims need the view)."""
    return [a for a in anims if a not in VIEW_ANIMS or VIEW_ANIMS[a][0] in d.views]


def filename(beast, anim):
    return f'fx_{beast}.gif' if anim == 'fx' else f'spr_{beast}_{anim}.gif'


def matrix_rows():
    """Same row list as the Studio dashboard (server.py)."""
    rows = {'player', 'poacher', 'aegis', 'titan', 'laser', 'bite', 'net'}
    if os.path.exists(MODELS_FILE):
        with open(MODELS_FILE) as f:
            rows.update(b.lower().replace(' ', '_') for b in re.findall(r'BeastDef\("([^"]+)"', f.read()))
    return sorted(rows)


def preview(rows, path):
    """Contact sheet: one row per beast, a few key frames, 3x zoom."""
    picks = [('idle', 0), ('idle', 6), ('explore', 1), ('attack', 3), ('hit', 1), ('faint', 5),
             ('victory', 3), ('evade', 1), ('walk_front', 0), ('attack_front', 3), ('fx', 2)]
    z = 3
    sheet = Image.new('RGBA', (len(picks) * 34 * z, len(rows) * 34 * z), (40, 44, 52, 255))
    for r, n in enumerate(rows):
        d = DESIGNS[n]
        for c, (anim, k) in enumerate(picks):
            frames, _ = ANIM_FNS[anim](d, n)
            f = frames[min(k, len(frames) - 1)].resize((32 * z, 32 * z), Image.Resampling.NEAREST)
            sheet.alpha_composite(f, (c * 34 * z + z, r * 34 * z + z))
    sheet.save(path)
    print(f'🖼️  Preview written to {path}')


def turnaround(rows, path):
    """One row per beast: the 5 drawn views (walk steps 0 and 1), 4x zoom."""
    views = ['front', 'fq', 'side', 'bq', 'back']
    z = 4
    sheet = Image.new('RGBA', (len(views) * 2 * 34 * z, len(rows) * 34 * z), (40, 44, 52, 255))
    for r, n in enumerate(rows):
        for c, v in enumerate(views):
            if v not in DESIGNS[n].views:
                continue
            for k in (0, 1):
                f = draw(n, Pose(view=v, step=k)).resize((32 * z, 32 * z), Image.Resampling.NEAREST)
                sheet.alpha_composite(f, ((c * 2 + k) * 34 * z + z, r * 34 * z + z))
    sheet.save(path)
    print(f'🔄 Turnaround written to {path}')


def write_props(out):
    """3D-world scenery: prop_<name>.gif, a 2-frame gentle sway."""
    for name, fn in PROPS.items():
        frames = []
        for t in range(2):
            p = pk.Painter()
            fn(p, Pose(t=t))
            frames.append(p.done())
        pk.save_gif(frames, [700, 700], os.path.join(out, f'prop_{name}.gif'))
    print(f'🌲 {len(PROPS)} world props')
    return len(PROPS)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--only', help='comma-separated rows, e.g. cacheon,titan')
    ap.add_argument('--anims', help='comma-separated animations (default: all)')
    ap.add_argument('--skip-existing', action='store_true', help="don't overwrite GIFs already on disk (keeps hand edits)")
    ap.add_argument('--preview', metavar='PNG', help='write a contact sheet instead of GIFs')
    ap.add_argument('--turnaround', metavar='PNG', help='write a sheet of the 5 drawn views instead of GIFs')
    ap.add_argument('--out', default=SAVE_DIR, help='output directory')
    args = ap.parse_args()

    rows = matrix_rows()
    missing = [r for r in rows if r not in DESIGNS]
    if missing:
        print(f"⚠️  No design yet for: {', '.join(missing)} (add one to designs.py)")
    rows = [r for r in rows if r in DESIGNS]
    if args.only:
        want = [x.strip().lower() for x in args.only.split(',')]
        rows = [r for r in want if r in DESIGNS]
    anims = [a.strip() for a in args.anims.split(',')] if args.anims else ANIMATIONS + list(VIEW_ANIMS)

    if args.turnaround:
        turnaround(rows, args.turnaround)
        return
    if args.preview:
        preview(rows, args.preview)
        return

    os.makedirs(args.out, exist_ok=True)
    written = skipped = 0
    if not args.only and not args.anims:
        written += write_props(args.out)
    for n in rows:
        for anim in anims_for(DESIGNS[n], anims):
            path = os.path.join(args.out, filename(n, anim))
            if args.skip_existing and os.path.exists(path):
                skipped += 1
                continue
            frames, durations = ANIM_FNS[anim](DESIGNS[n], n)
            pk.save_gif(frames, durations, path)
            written += 1
        print(f'✅ {n}')
    print(f'🎨 Wrote {written} GIFs to {os.path.relpath(args.out, ROOT)}' + (f' (skipped {skipped} existing)' if skipped else ''))


if __name__ == '__main__':
    main()
