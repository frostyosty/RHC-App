# Sprite and effect art

How Netbeast creatures, props and battle effects are drawn. Read this before
touching `sprite_studio/autogen` or adding art the game loads. The Studio
itself and autogen's flags are described in `rhc-android/README.md` §7. The
list of moves still waiting for an effect is in `PLAN.md` at the repo root.

## Netbeast sprite art: the cacheon style

`cacheon` in `sprite_studio/autogen/designs.py` is the reference for how every
creature should look and be built. The user signed off on it. How it was made:

- **Look.** Dark and hard, not cute. Small head relative to the body, long
  limbs, angular armour plates (`p.poly`, not round `p.ell` blobs), a
  wedge-shaped snout with teeth, narrow slit eyes that meet in a V from the
  front and turn red (`#FF4D5E`) when angry or hurt, and spikes or blades on
  ears, spine and tail. Deep palette (steel `#4A5263`, plate `#646E82`, joints
  `#2A303C`, near-black `#15181F`/`#0B0D12`) with one glowing accent colour for
  the creature's theme (cacheon's is cyan) on vents, ear tips and eyes. Keep the
  colours in a small per-creature kit class (`CacheonKit`).
- **64 grid.** `@design(..., size=64, views=ALL_VIEWS)`. The feet rest on
  `p.ground` (size - 3). Use helpers that take `p.size`/`p.ground`, not
  hardcoded 32-grid numbers. `dleg` draws a jointed leg (a back-bent hind leg with
  a thigh, or a near-straight front leg) with a 3px walk stride.
- **5 views.** Write one function per view (`_cacheon_side`, `_front`, `_fq`,
  `_bq`, `_back`) and dispatch on `s.view`. Side and the ¾ views face right.
  Front and back are drawn as the left half only, and the Painter mirrors them.
  To draw something off-centre in a mirrored view, turn `p.mirror` off around
  it, the way cacheon's back-view tail does. Draw from back to front: tail, far
  legs, body, near legs, then the head (`fq`), or the far legs and head first
  in `bq` and `back`, where the rump is nearest. Share parts between views
  where you can (`cacheon_head_side`).
- **Poses.** Every view has to handle `s.step` (walk), `s.eyes`
  (open/closed/hurt/happy/angry), `s.mouth` (open jaw showing teeth) and `s.t`
  (glow flicker). Autogen builds every animation from those, including `turn`,
  the 8-direction spin.
- **Loop.** Iterate visually, not blind:
  1. Render the views with `python3 sprite_studio/autogen/autogen.py --turnaround views.png --only <row>`,
     and a big side-by-side of normal and `Pose(mouth=True, eyes='angry')`,
     then look at the images.
  2. Fix whatever reads badly: a floating head, pillar legs, a featureless
     back of the head.
  3. Repeat until every view reads as the same creature.
  4. Write the GIFs with `autogen.py --only <row>` (it must print no ⚠️
     edge warnings).
  5. Check `turn.png` from `bash tools/world_preview/run.sh`.
- **Canvas.** Frames are padded to 42/32 of the grid, so lunges never clip.
  The 3D renderer's `CREATURE_CANVAS` matches that, so don't change one without
  the other.

Every row follows this style (done 2026-09-24). Each creature line has its own
dark `Kit` subclass with one accent colour: Tech is cyan, `SocialKit` magenta
(viralia lime), `GamingKit` violet, `StreamingKit` ember orange (not red, so
the red angry eyes still read), `FlyingKit` ice blue, Shopping green and
`LegendKit` gold. Views are wired with `by_view({...})`. Shared parts are in
`designs.py`: `dleg`, `bird_leg`, `raptor_leg`, `roo_leg`, `stomp_leg`,
`hum_leg`, `gauntlet`, `claw_arm`, `bat_wing`, `blade_wing`, `feather_wing`,
`fangs` and `sq`. The battle-only rows (aegis, titan, net) draw only the front
and side views. Redraw a new row the same way.

## Looks: what REGENERATE changes

The Studio's REGENERATE button steps a row through numbered looks
(`autogen/looks.py`, `autogen.py --only <row> --look next|prev|original`). A
creature's look recolours the drawing through the Painter's `recolor` hook:
dull colours become one of the `ARMOURS` tones, saturated ones (the accent)
shift a little in hue, and `#FF4D5E` never changes. A move effect's look adds
to its `seed` in `MOVES`. Look 0 is the design as written, and the current
looks are in `autogen/looks.json`.

- Draw a design for look 0 and pass colours to the Painter as usual. Don't
  bake a look's colours into `designs.py`.
- A colour that must survive every look goes in `looks.FIXED`.
- When checking a design against the cacheon style, check it on look 0
  (`--look original`).
- Looks only recolour. When the user wants a creature to look different in
  shape, that is a redraw of its design, following the loop above.

## Battle effects are not creatures

Every move has its own one-shot effect, and `laser` and `net` are shared
ones. They live in
`sprite_studio/autogen/effects.py` (`fx_<name>.gif`, 64 grid at 2x, facing
right, ending on an empty frame), written by
`autogen.py --only <name>,...`. Move effects are named after the move in
snake_case (`Data Drain` -> `fx_data_drain.gif`), and each one is a line in
the `MOVES` table: a family (beam, jaws, slash, impact, waves, zap, toxic,
glitch, drain, ward, spiral, grab, nova, blocks, time), a colour line
(`LINE`, the creature Kit accents) and its own seed/shape. The Studio's
💥 Attacks tab lists them with a regenerate button each. They have no idle/walk/faint and aren't
matrix rows. Only real creatures (and player/poacher/aegis/titan) are rows.

- A move's `fx` in `SkillEngine.SKILL_DATABASE` defaults to its own name
  (`SkillEngine.fxName`, same as `effects.slug`). Renamed moves go in
  `SkillEngine.RENAMED` so old saves pick up the new name. `playAttackFx(onPlayer, move)` in
  `BattleSpriteAnim.kt` plays it over the defender when the hit lands.
- Items call `playFx(onPlayer, name)` directly. The mid-battle Net button in
  `GameSetup.kt` plays `fx_net` over the enemy.
- The overlays are `spritePlayerFx`/`spriteEnemyFx` in `game_arena.xml`. The
  player's is mirrored, because the enemy attacks from the right.
- A new move needs a line in `MOVES` (the Attacks tab picks it up). A new
  shared effect is an `@effect` plus a `playFx` call, and its name in
  `BASE_EFFECTS` in `sprite_studio/server.py`. Render it and
  look at it frame by frame, in the dark creature style, the way laser, bite
  and net were built. Then update the effect lists in `PLAN.md`.
