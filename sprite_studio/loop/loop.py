#!/usr/bin/env python3
"""The sprite remake loop, one command a round: one picture plus a line of numbers per cell.

Every command draws straight from autogen (designs.py, effects.py, scenery.py) in memory, writes
ONE picture, sprite_studio/loop/out/<command>.png, and prints a short line per cell. Nothing is
written to the app's drawables unless you pass --write. out/ is scratch and is not committed.

  python3 sprite_studio/loop/loop.py look cacheon               every view, walk step, faces and poses
  python3 sprite_studio/loop/loop.py look cacheon --anims attack,hit   ...and those animations frame by frame
  python3 sprite_studio/loop/loop.py look cacheon --looks 0,1,2  the views in each look (looks.json is not changed)
  python3 sprite_studio/loop/loop.py look bite tree:pine nz     effects, trees (every distance), props, house styles
                                                                  (fx: tree: prop: house: when a name is two things)
  python3 sprite_studio/loop/loop.py redo cacheon               after editing: before | after, cell by cell
  python3 sprite_studio/loop/loop.py redo cacheon --write       ...then autogen.py --only cacheon (the GIFs)

`look` and `redo` keep what they drew in out/snap/<name>/, and `redo` compares against that (the
last look or redo of that name). So: look, edit, redo, edit, redo... The text says which cells
changed (and by how many pixels), so an unchanged cell never needs looking at.

Options:  --look N   draw in look N without saving it (default: the row's current look; 0 = as written)
          --zoom N   zoom (default: 2 for a 64 grid, 4 for a 32 grid)
          --bg R,G,B background (default 104,112,120: dark creatures read on it)
          --out NAME picture name (default: the command)

Numbers per cell:  WxH of the drawing, `gap` (empty rows under the feet: the same in every view
or the creature floats or sinks), `col` (colours), `EDGE` when it touches the left, right or top of
the frame (autogen's clipping warning).
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
AUTOGEN = os.path.join(HERE, '..', 'autogen')
sys.path.insert(0, AUTOGEN)
import autogen as ag  # noqa: E402
import effects  # noqa: E402
import looks  # noqa: E402
import pixelkit as pk  # noqa: E402
import scenery  # noqa: E402
from designs import DESIGNS, PROPS, PROP_TIMING, Pose  # noqa: E402

OUT = os.path.join(HERE, 'out')
VIEWS = ['front', 'fq', 'side', 'bq', 'back']


# ---------------------------------------------------------------- what to draw for a name
def creature_cells(name, look, anims):
    """[(key, label, image)] for a creature row, in its look `look` (None = its current look)."""
    d = DESIGNS[name]
    saved = ag.look_of
    if look is not None:
        ag.look_of = lambda n: looks.recolor(n, look, DESIGNS[n].color) if ag.has_looks(n) else None
    try:
        cells = []
        for v in VIEWS:
            if v in d.views:
                cells.append((f'{v}', v, ag.draw(name, Pose(view=v))))
        main = 'side' if 'side' in d.views else d.views[0]
        cells.append((f'{main}_step1', f'{main} step 1', ag.draw(name, Pose(view=main, step=1))))
        for v in [x for x in ('front', 'fq', 'side') if x in d.views][:2]:
            cells.append((f'{v}_angry', f'{v} angry + mouth', ag.draw(name, Pose(view=v, eyes='angry', mouth=True))))
        cells.append((f'{main}_hurt', f'{main} hurt', ag.draw(name, Pose(view=main, eyes='hurt'))))
        cells.append((f'{main}_closed', f'{main} eyes closed', ag.draw(name, Pose(view=main, eyes='closed'))))
        rows = [('views and poses', cells)]
        for a in anims:
            if a not in ag.ANIM_FNS or a not in ag.anims_for(d, [a]):
                print(f'note: {name} has no animation {a!r}')
                continue
            frames, _ = ag.ANIM_FNS[a](d, name)
            rows.append((f'{a} ({len(frames)} frames)', [(f'{a}_{i}', f'{a} {i}', f) for i, f in enumerate(frames)]))
        return rows
    finally:
        ag.look_of = saved


def cells_for(name, look, anims, looks_list):
    """[(row label, [(key, label, image)])] for any name autogen knows."""
    if name in DESIGNS:
        if looks_list:
            rows = []
            for lk in looks_list:
                (_, cells), *_ = creature_cells(name, lk, [])
                rows.append((f'look {lk}' + (f' ({looks.describe(name, lk)[0]})' if lk else ' (as written)'),
                             [(f'look{lk}_{k}', lbl, im) for k, lbl, im in cells[:len([v for v in VIEWS if v in DESIGNS[name].views])]]))
            return rows
        return creature_cells(name, look, anims)
    kind, _, bare = name.rpartition(':')
    kinds = [k for k, table in (('fx', effects.EFFECTS), ('tree', scenery.TREES), ('prop', PROPS), ('house', scenery.HOUSE_STYLES)) if bare in table]
    if not kind and len(kinds) > 1:
        print(f"note: {bare} is a {' and a '.join(kinds)}: showing the {kinds[0]} ({kinds[1]}:{bare} for the other)")
    kind = kind or (kinds[0] if kinds else '')
    if kind == 'fx' and bare in effects.EFFECTS:
        frames, _ = effects.EFFECTS[bare]()
        return [(f'effect ({len(frames)} frames)', [(f'f{i}', str(i), f) for i, f in enumerate(frames)])]
    if kind == 'tree' and bare in scenery.TREES:
        return [('tree, each distance (1x)', [(f'd{lod}', f'd{lod}', scenery.frames(bare, lod)[0][0]) for lod in range(len(scenery.D))])]
    if kind == 'prop' and bare in PROPS:
        count, _ = PROP_TIMING.get(bare, (2, 700))
        out = []
        for t in range(count):
            p = pk.Painter()
            PROPS[bare](p, Pose(t=t))
            out.append((f't{t}', f't {t}', p.done()))
        return [('prop', out)]
    if kind == 'house' and bare in scenery.HOUSE_STYLES:
        return [('far-off houses', [(f'h{i}', str(i), scenery.house(bare, i)) for i in range(scenery.HOUSE_VARIANTS)])]
    raise SystemExit(f'unknown name {name!r}: not a design, effect, prop, tree kind or house style')


# ---------------------------------------------------------------- numbers
def measure(img, creature=True):
    a = img.getchannel('A')
    box = a.point(lambda v: 255 if v >= 128 else 0).getbbox()
    if not box:
        return {'w': 0, 'h': 0, 'gap': None, 'col': 0, 'edge': False}
    cols = img.getcolors(1 << 16) or []
    return {'w': box[2] - box[0], 'h': box[3] - box[1], 'gap': img.height - box[3],
            'col': len([c for n, c in cols if c[3] >= 128]), 'edge': creature and ag.touches_edge(img)}


def fmt(m):
    if not m['w']:
        return 'empty'
    return f"{m['w']}x{m['h']} gap {m['gap']} col {m['col']}" + (' EDGE' if m['edge'] else '')


def changed(a, b):
    """Pixels that differ between two drawings (None if the sizes differ)."""
    if a.size != b.size:
        return None
    diff = ImageChops.difference(a.convert('RGBA'), b.convert('RGBA')).convert('L').point(lambda v: 255 if v else 0)
    return sum(1 for v in diff.getdata() if v)


# ---------------------------------------------------------------- the one picture
def font(size=11):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:
        return ImageFont.load_default()


def sheet(blocks, path, zoom, bg, title):
    """blocks: [(heading, [(label, [lines], image, highlight)])] -> one PNG, rows wrapped at 1400 px."""
    f, fb = font(11), font(13)
    pad, maxw, cap = 6, 1400, 30
    placed, x, y, rowh, width = [], pad, pad + 20, 0, 0
    for heading, cells in blocks:
        if x > pad:
            x, y, rowh = pad, y + rowh + pad, 0
        placed.append(('h', heading, (pad, y)))
        y += 16
        for label, lines, img, hl in cells:
            z = max(1, min(zoom, 300 // max(img.width, img.height, 1)))
            w, h = img.width * z, img.height * z
            cw = max(w, 128)
            if x + cw > maxw and x > pad:
                x, y, rowh = pad, y + rowh + pad, 0
            placed.append(('c', (label, lines, img.resize((w, h), Image.Resampling.NEAREST), hl), (x, y, cw)))
            x += cw + pad
            rowh = max(rowh, h + cap)
            width = max(width, x)
    height = y + rowh + pad
    out = Image.new('RGBA', (max(width, 400), height), (27, 25, 22, 255))
    dr = ImageDraw.Draw(out)
    dr.text((pad, pad), title, fill=(240, 167, 58), font=fb)
    for kind, what, where in placed:
        if kind == 'h':
            dr.text(where, what, fill=(168, 159, 144), font=f)
            continue
        label, lines, img, hl = what
        cx, cy, cw = where
        dr.rectangle([cx, cy, cx + cw - 1, cy + img.height - 1], fill=bg)
        out.alpha_composite(img, (cx + (cw - img.width) // 2, cy))
        if hl:
            dr.rectangle([cx - 1, cy - 1, cx + cw, cy + img.height], outline=(240, 167, 58), width=2)
        dr.text((cx, cy + img.height + 2), label, fill=(236, 228, 214), font=f)
        for i, ln in enumerate(lines[:1]):
            dr.text((cx, cy + img.height + 15), ln, fill=(168, 159, 144), font=f)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    return out.size


# ---------------------------------------------------------------- main
def main():
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except AttributeError:
        pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('cmd', choices=['look', 'redo'])
    ap.add_argument('names', nargs='+')
    ap.add_argument('--look', type=int)
    ap.add_argument('--looks', help='comma-separated looks to show side by side, e.g. 0,1,2')
    ap.add_argument('--anims', default='', help='comma-separated animations to show frame by frame')
    ap.add_argument('--zoom', type=int)
    ap.add_argument('--bg', default='104,112,120')
    ap.add_argument('--out')
    ap.add_argument('--write', action='store_true', help='redo: then write the GIFs with autogen.py --only <names>')
    args = ap.parse_args()
    bg = tuple(int(v) for v in args.bg.split(',')) + (255,)
    anims = [a for a in args.anims.split(',') if a]
    looks_list = [int(v) for v in args.looks.split(',')] if args.looks else None

    blocks, lines_out = [], []
    zoom = args.zoom
    for name in args.names:
        rows = cells_for(name, args.look, anims, looks_list)
        if zoom is None:
            zoom = 2 if name in DESIGNS and DESIGNS[name].size >= 64 else 4 if name in DESIGNS else 3
        snap = os.path.join(OUT, 'snap', name.replace(':', '_'))
        before = {}
        if args.cmd == 'redo' and os.path.isdir(snap):
            for fn in os.listdir(snap):
                if fn.endswith('.png'):
                    before[fn[:-4]] = Image.open(os.path.join(snap, fn)).convert('RGBA')
        if args.cmd == 'redo' and not before:
            print(f'note: no earlier look at {name} to compare with (run look first); showing it as it is')
        new_snap = {}
        cr = name in DESIGNS
        for heading, cells in rows:
            if args.cmd == 'redo' and before:
                b_cells, a_cells, same = [], [], []
                for key, label, img in cells:
                    new_snap[key] = img
                    old = before.get(key)
                    n = changed(old, img) if old is not None else None
                    m = measure(img, cr)
                    if old is not None and n == 0:
                        same.append(label)
                        continue
                    mo = measure(old, cr) if old is not None else None
                    b_cells.append((label, [fmt(mo) if mo else 'new'], old if old is not None else Image.new('RGBA', img.size), False))
                    a_cells.append((label, [fmt(m)], img, True))
                    lines_out.append(f"  {label:<20} {('changed ' + str(n) + ' px') if n is not None else ('new' if old is None else 'resized')}: {fmt(mo) if mo else '-'} -> {fmt(m)}")
                if b_cells:
                    blocks.append((f'{name} · {heading} · before', b_cells))
                    blocks.append((f'{name} · {heading} · after', a_cells))
                if same:
                    lines_out.append(f"  unchanged: {', '.join(same)}")
            else:
                cs = []
                for key, label, img in cells:
                    new_snap[key] = img
                    m = measure(img, cr)
                    cs.append((label, [fmt(m)], img, m['edge']))
                    lines_out.append(f'  {label:<20} {fmt(m)}')
                blocks.append((f'{name} · {heading}', cs))
        # a design's gap should match across its views
        if name in DESIGNS:
            gaps = {k: measure(im)['gap'] for _, cells in rows for k, _, im in cells if k in VIEWS}
            if len(set(gaps.values())) > 1:
                lines_out.append(f'  ! feet gap differs between views: {gaps} (floating or sinking?)')
        shutil.rmtree(snap, ignore_errors=True)
        os.makedirs(snap, exist_ok=True)
        for k, im in new_snap.items():
            im.save(os.path.join(snap, f'{k}.png'))
        print(name)
        for ln in [x for x in lines_out if x]:
            print(ln)
        lines_out = []
        # redo: autogen's own clip check over every animation, without writing anything
        if args.cmd == 'redo' and name in DESIGNS:
            d = DESIGNS[name]
            clipped = []
            for a in ag.anims_for(d, ag.ANIMATIONS + list(ag.VIEW_ANIMS)):
                if a == 'fx':
                    continue
                frames, _ = ag.ANIM_FNS[a](d, name)
                bad = [i for i, fr in enumerate(frames) if ag.touches_edge(fr)]
                if bad:
                    clipped.append(f'{a} {bad}')
            print('  ! frames touching the edge: ' + '; '.join(clipped) if clipped else '  every animation clear of the edges')

    path = os.path.join(OUT, f'{args.out or args.cmd}.png')
    if blocks:
        size = sheet(blocks, path, zoom or 3, bg, f"{args.cmd} {' '.join(args.names)}" + (f'  (look {args.look})' if args.look is not None else ''))
        print(f'picture: {path}  ({size[0]}x{size[1]})')
    else:
        print('nothing changed: no picture')

    if args.write:
        r = subprocess.run([sys.executable, os.path.join(AUTOGEN, 'autogen.py'), '--only', ','.join(args.names)], capture_output=True, text=True)
        print('\n'.join(ln for ln in (r.stdout + r.stderr).splitlines() if any(s in ln for s in ('⚠', '✋', 'Wrote', 'Error', 'error'))))


if __name__ == '__main__':
    main()
