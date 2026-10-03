# Art for Homevisits

**Any time.** Each step lists the art it needs, and placeholders are fine until then.
**Read first:** [README](README.md); `sprite_studio/CLAUDE.md`, for how autogen draws,
the 5 views and the review loop. The Netbeasts "cacheon style" described there is for
creatures, not for Homevisits.

## The look (Ask)

**Sprites, decided by the user (2026-09-27).** Very high-resolution pixel art with
deep rotation detail: more views than the Netbeasts creatures, so turning feels
smooth and solid. The options considered were:

| | Sprites (proposed) | Voxel models | 3D models |
|---|---|---|---|
| What they are | 2D pixel art from autogen | little blocks, built in code the way autogen builds sprites | modelled, rigged and animated meshes |
| In AR | a bit flat: they turn in 45° steps as you walk round them | solid; they turn smoothly and still look pixel-y | the most "really there" |
| Making them | the existing pipeline: iterate on turnaround PNGs | a new generator and a mesh renderer; parts are rigid, so there's no skinning | a new pipeline (Blender or bought models, and skinned animation); they can't be generated and reviewed the way autogen's are |
| Cost | cheapest | medium | the most work per character, a bigger APK, more battery |

Paintings hang flat on walls, so they're pictures whichever option is chosen.

Homevisits is warm and homely: the opposite of the Netbeasts' dark, hard creatures.
Decided (2026-09-27):

- **Pixel art from autogen,** like everything else in the game, so it's made the same
  way and fits the pipeline.
- **People on a 128 grid.** They stand 1–3 m from the camera and fill much of the
  screen. On the 64 grid, each art pixel would be about 30 screen pixels. Add a
  64-grid level for when they're further away, picked by on-screen height, as the
  Wilds do for trees.
- **Very high resolution with deep rotation detail.** More than the 5 views the
  creatures use: draw at least 8 views (every 45°) and mirror only where it doesn't
  show, so turning in AR looks smooth. The renderer picks the nearest view, so the
  number of views sets the granularity.
- **A warm palette per character,** in a `Kit` like the creatures' (skin, hair,
  clothes and one accent colour). Soft outlines, and friendly proportions: neither
  chibi nor realistic.
- **Portraits for the dialogue panel:** 64 × 64, head and shoulders, one per face:
  happy, sad, angry, surprised, laugh, frown, shy, touched and neutral. These are the
  faces 06 and 07 use.
- **Rachel first.** Draw her first, and show the user her turnaround and faces before
  drawing anyone else. The same thing happened with cacheon, which was signed off
  before the other creatures.

## People

Add a new autogen module, `sprite_studio/autogen/homevisits.py`. It uses the Painter
from `pixelkit`, and the human helpers already in `designs.py` (`hum_leg`,
`hum_legs_front` and `hum_arm`, which the `player` and `poacher` rows use).

For each character:

- **Views.** The 5 views (front, fq, side, bq, back), dispatched on `s.view`, as
  `sprite_studio/CLAUDE.md` describes.
- **Poses:**
  - walking (`s.step`), idle, talking (mouth frames), waving, pointing or looking at
    something;
  - the faces, through `s.eyes` and `s.mouth`, extended with sad, surprised and shy.
- **Animations:**
  - `idle_*` and `walk_*` for each view;
  - `talk_front`, `talk_fq`, `wave_front` and `look_*`;
  - `turn`, the 8-direction spin, for checking.
- **Output** goes to this flavor's own resources, as
  `rhc-android/app/src/gamersFemaleHomevisits/res/drawable-nodpi/hv_<name>_<anim>.gif`,
  so the other APKs don't carry it.
- **Canvas padding** is a single number shared with the renderer: `PERSON_CANVAS`
  (see [10](10-visitors-in-ar.md)).
- **The review loop** from `sprite_studio/CLAUDE.md`: render a turnaround, look at
  it, fix it, and repeat. There must be no ⚠️ edge warnings.

## Things for the home

- **Paintings.**
  - Framed pictures, with the subject drawn inside. Reuse the way `scenery.py` draws
    trees, sea and hills, for NZ subjects.
  - A few frame styles: wood, gold, white.
  - Each painting has tags for 06: seascape, landscape, flowers, abstract, portrait.
  - They hang flat on walls, so one front view is enough.
- **Small things** (pot plants, vases, lamps, candles, a clock, books) can be
  billboards, which work for roughly round things. Avoid boxy furniture at first: a
  box drawn as a billboard looks wrong when you walk around it.
- **The parcel:**
  - closed, in brown paper and string;
  - three tearing frames;
  - open;
  - a "carried" version for the bottom of the screen.
- **Icons:** Momentum, the knock, the calendar, messages, and the tag icons for the
  catalogue.
- **2D rooms** (step 09): an entry with a front door, a kitchen with a bench, a
  lounge with a couch, and a bedroom. Each room has slots for items.

## Sounds

These come from the Studio's audio synth, and go into this flavor's `res/raw/`:
- a knock and a doorbell;
- the parcel's paper;
- soft footsteps;
- a chime for a new item;
- each character's voice blip (one short blip, pitched for that character).

## What each step needs

| Step | Art |
|---|---|
| 02 | Nothing new: the `player` row is the test sprite |
| 04 | The parcel; the first painting ("Harbour at dusk"); Rachel (idle, walk, wave and talk, in all views) and her portraits; the knock and the doorbell |
| 07 | Portraits for Rachel and Alfred |
| 08 | Portraits for everyone who's been introduced; the calendar and who's-who icons |
| 09 | The four 2D rooms |
| 10 | Every introduced character, in all views and animations; the voice blips |
| 11 | The catalogue items; the parcel for bigger things |
| 12 | Nothing new: the portraits from 08 |
