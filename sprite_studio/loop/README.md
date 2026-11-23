# sprite_studio/loop

The sprite remake loop, one command a round. Each command draws straight from autogen
(`designs.py`, `effects.py`, `scenery.py`) in memory and ends in **one picture**,
`sprite_studio/loop/out/<command>.png`, plus a line of numbers per cell. A round costs one command, one
image read and a few lines of text. Nothing goes to the app's drawables unless you pass `--write`.
`out/` is ignored by git, so the quicksave commits never pick it up. It needs Python 3 and Pillow,
the same as autogen.

```
python3 sprite_studio/loop/loop.py look cacheon                 # every view, walk step 1, angry + mouth, hurt, eyes closed
python3 sprite_studio/loop/loop.py look cacheon --anims attack  # ...and animations frame by frame
python3 sprite_studio/loop/loop.py look cacheon --looks 0,1,2   # the views in each look (looks.json not changed)
python3 sprite_studio/loop/loop.py look bite tree:pine nz       # an effect's frames, a tree at every distance, a house style
python3 sprite_studio/loop/loop.py redo cacheon                 # after editing: only what changed, before | after
python3 sprite_studio/loop/loop.py redo cacheon --write         # ...then autogen.py --only cacheon writes the GIFs
```

## A round

You give a list, one line per sprite: what it is and why it doesn't satisfy, for example
"cacheon: the back view's head is featureless" or "fx bite: the jaws close too early".

1. `look <names>`. It also keeps what it drew, in `out/snap/<name>/`.
2. Edit the design in `designs.py`, `effects.py` or `scenery.py`, following `sprite_studio/CLAUDE.md`
   (the cacheon style).
3. `redo <names>` compares the drawing with the last `look` or `redo` of that name.
   - The picture has only the cells that changed: a "before" row over an "after" row.
   - The text lists each changed cell with how many pixels changed, and names the unchanged cells.
   - For a creature, it also runs autogen's clipping check over every animation without writing
     anything.
4. Repeat 2 and 3. When it reads well, `redo <names> --write`, or `autogen.py --only <names>`.
   That must print no ⚠️ edge warnings.

Check designs on look 0 (`--look 0`), as `sprite_studio/CLAUDE.md` says.

## The numbers

Each cell gets one line: `WxH gap N col N [EDGE]`.

| number | what it is | what to look for |
|---|---|---|
| `WxH` | the drawing's bounding box | |
| `gap` | empty rows under the feet | It should be the same in every view. If not, the creature floats or sinks in one of them, and the text warns. |
| `col` | how many colours it uses | |
| `EDGE` | for creatures, the drawing touches the left, right or top of the frame | It may be clipped (autogen's warning). |

## Names

A name can be any of these:

- a design row (`cacheon`)
- an effect (`bite`, `fx_data_drain` without the `fx_`)
- a tree kind
- a prop
- a far-off house style (`nz`, `euro`, ...)

When a name is two things (`pine` is a tree and a prop), it shows the first one and says so. Write
`tree:`, `prop:`, `fx:` or `house:` to pick.

## Options

| option | what it does |
|---|---|
| `--look N` | Draws in look N without saving it. 0 means as written. |
| `--zoom N` | Sets the zoom. The default is 2 for a 64 grid and 4 for a 32 grid. |
| `--bg R,G,B` | Sets the background. The default, 104,112,120, lets dark creatures read. |
| `--out NAME` | Names the picture, to keep two side by side. |

It is the same idea as htc's `shape_studio/loop/` (textures for buildings).
