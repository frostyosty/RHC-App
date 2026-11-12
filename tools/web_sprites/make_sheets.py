#!/usr/bin/env python3
"""Turns the autogen GIFs into PNG sprite sheets for the web client (netbeasts-web).

Browsers can't reliably pull single frames out of a GIF, so each GIF becomes one
PNG with its frames side by side, and index.txt says how long each frame shows.

The frames are sampled exactly the way the phone's SpriteBank does it, so a
sprite animates the same on both: every 50 ms over the GIF's length, capped at
2 s, and a sample that repeats the one before just makes that frame last longer.

Usage: python3 tools/web_sprites/make_sheets.py <drawable-nodpi dir> <out dir>
Writes <out>/sprites/<key>.png and <out>/sprites/index.txt, a line a sprite:
  spr_cacheon_idle 168 168 150 150 250 100 150 150 50
  (key, frame width and height, then each frame's ms; frame i is at x = i * w)
The netbeasts-web Gradle build runs it; the output stays out of git.
"""
import os
import sys

from PIL import Image

STEP_MS = 50        # SpriteBank's sampling clock
MAX_MS = 2000       # SpriteBank's cap on one play
PREFIXES = ("spr_", "prop_", "fx_")


def frames_of(path):
    """The GIF's composited frames (RGBA) and how long each one shows, in ms."""
    im = Image.open(path)
    frames, delays = [], []
    for i in range(getattr(im, "n_frames", 1)):
        im.seek(i)
        frames.append(im.convert("RGBA"))
        delays.append(int(im.info.get("duration", 0) or 0))
    return frames, delays


def sample(frames, delays):
    """SpriteBank's samples, merged into runs: [(frame image, ms)]."""
    total = min(max(sum(delays), 0), MAX_MS)
    count = max(total // STEP_MS, 1)
    runs = []
    for s in range(count):
        t, acc, idx = s * STEP_MS, 0, len(frames) - 1
        for i, d in enumerate(delays):  # the frame showing at time t
            if t < acc + d:
                idx = i
                break
            acc += d
        img = frames[idx]
        if runs and runs[-1][0].tobytes() == img.tobytes():
            runs[-1][1] += STEP_MS
        else:
            runs.append([img, STEP_MS])
    return runs


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    src, out = sys.argv[1], os.path.join(sys.argv[2], "sprites")
    os.makedirs(out, exist_ok=True)
    index = {}
    for name in sorted(os.listdir(src)):
        key, ext = os.path.splitext(name)
        if ext != ".gif" or not key.startswith(PREFIXES):
            continue
        runs = sample(*frames_of(os.path.join(src, name)))
        w, h = runs[0][0].size
        sheet = Image.new("RGBA", (w * len(runs), h), (0, 0, 0, 0))
        for i, (img, _) in enumerate(runs):
            sheet.paste(img, (i * w, 0))
        sheet.save(os.path.join(out, key + ".png"), optimize=True)
        index[key] = [w, h] + [ms for _, ms in runs]
    with open(os.path.join(out, "index.txt"), "w", encoding="utf-8", newline="\n") as f:
        for key, row in index.items():
            f.write(" ".join([key] + [str(v) for v in row]) + "\n")
    print(f"{len(index)} sprite sheets in {out}")


if __name__ == "__main__":
    main()
