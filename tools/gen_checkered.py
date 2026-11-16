#!/usr/bin/env python3
"""Generates the checkered frame models, blockstates, item models, loot tables, recipes and the dark frame texture.

Models are generated per facing instead of using blockstate rotations: a 90-degree rotation flips the checker
parity, and the empty frame's light/dark cells must match Checker.slot so you can see which half a click fills.
Run from the repo root: python3 tools/gen_checkered.py
"""
import json
from pathlib import Path

from PIL import Image

RES = Path("src/main/resources")
ASSETS = RES / "assets/veneer"
DATA = RES / "data/veneer"
DIRS = {"down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1)}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def slot(cell):
    return cell[0] ^ cell[1] ^ cell[2]


def cell_elements(cells, filled=()):
    """One 8x8x8 element per cell, with only the faces on the outside of the shape (cells in filled also hide faces)."""
    elements = []
    for cell in sorted(cells):
        faces = {}
        for name, (axis, sign) in DIRS.items():
            neighbour = list(cell)
            neighbour[axis] += sign
            if tuple(neighbour) in cells or tuple(neighbour) in filled:
                continue
            face = {"uv": [0, 0, 16, 16], "texture": "#b" if slot(cell) else "#a"}
            if neighbour[axis] in (-1, 2):
                face["cullface"] = name
            faces[name] = face
        elements.append({"from": [c * 8 for c in cell], "to": [c * 8 + 8 for c in cell], "faces": faces})
    return elements


def half_cells(side):
    axis, sign = DIRS[side]
    value = 1 if sign > 0 else 0
    return {(x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1) if (x, y, z)[axis] == value}


def solid_half(side):
    """One element filling the half opposite to the checkered side, all slot 0."""
    axis, sign = DIRS[side]
    lo, hi = [0, 0, 0], [16, 16, 16]
    if sign > 0:
        hi[axis] = 8
    else:
        lo[axis] = 8
    faces = {}
    for name, (a, s) in DIRS.items():
        if a == axis and s == sign:
            continue  # faces the checkered half
        faces[name] = {"texture": "#a", "cullface": name}
    return {"from": lo, "to": hi, "faces": faces}


def model(name, elements):
    write(ASSETS / f"models/block/{name}.json", {
        "parent": "minecraft:block/block",
        "textures": {"a": "veneer:block/frame", "b": "veneer:block/checkered_frame_dark", "particle": "veneer:block/frame"},
        "elements": elements,
    })


def item(name, block_model):
    write(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"veneer:block/{block_model}"}})


def loot(name, double_state=None):
    entry = {"type": "minecraft:item", "name": f"veneer:{name}"}
    if double_state:
        entry["modifier"] = [{"type": "minecraft:set_count", "condition": {
            "type": "minecraft:match_block", "blocks": f"veneer:{name}", "state": double_state}, "count": 2}]
    write(DATA / f"loot_table/blocks/{name}.json", {
        "type": "minecraft:block",
        "pools": [{"condition": {"type": "minecraft:survives_explosion"}, "entries": [entry], "rolls": 1}],
        "random_sequence": f"veneer:blocks/{name}",
    })


def stonecutting(name, count):
    write(DATA / f"recipe/{name}_from_stonecutting.json", {
        "type": "minecraft:stonecutting", "ingredient": "veneer:frame_block", "result": {"id": f"veneer:{name}", "count": count}})


ALL = {(x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)}

# Checkered block
model("checkered_frame_block", cell_elements(ALL))
write(ASSETS / "blockstates/checkered_frame_block.json", {"variants": {"": {"model": "veneer:block/checkered_frame_block"}}})
item("checkered_frame_block", "checkered_frame_block")
loot("checkered_frame_block")
stonecutting("checkered_frame_block", 1)

# Half-checkered block: FACING half checkered, the rest solid
variants = {}
for side in DIRS:
    model(f"half_checkered_frame_block_{side}", cell_elements(half_cells(side), ALL - half_cells(side)) + [solid_half(side)])
    variants[f"facing={side}"] = {"model": f"veneer:block/half_checkered_frame_block_{side}"}
write(ASSETS / "blockstates/half_checkered_frame_block.json", {"variants": variants})
item("half_checkered_frame_block", "half_checkered_frame_block_up")
loot("half_checkered_frame_block")
stonecutting("half_checkered_frame_block", 1)

# Checkered slab
model("checkered_frame_slab", cell_elements(half_cells("down")))
model("checkered_frame_slab_top", cell_elements(half_cells("up")))
write(ASSETS / "blockstates/checkered_frame_slab.json", {"variants": {
    "type=bottom": {"model": "veneer:block/checkered_frame_slab"},
    "type=top": {"model": "veneer:block/checkered_frame_slab_top"},
    "type=double": {"model": "veneer:block/checkered_frame_block"},
}})
item("checkered_frame_slab", "checkered_frame_slab")
loot("checkered_frame_slab", {"type": "double"})
stonecutting("checkered_frame_slab", 2)

# Checkered vertical slab (FACING = the side it sits against)
variants = {}
for side in ("north", "east", "south", "west"):
    model(f"checkered_frame_panel_{side}", cell_elements(half_cells(side)))
    variants[f"double=false,facing={side}"] = {"model": f"veneer:block/checkered_frame_panel_{side}"}
    variants[f"double=true,facing={side}"] = {"model": "veneer:block/checkered_frame_block"}
write(ASSETS / "blockstates/checkered_frame_panel.json", {"variants": variants})
item("checkered_frame_panel", "checkered_frame_panel_north")
loot("checkered_frame_panel", {"double": "true"})
stonecutting("checkered_frame_panel", 2)

# Dark frame texture for the second checker slot, so an empty checkered frame shows its pattern.
src = Image.open(ASSETS / "textures/block/frame.png").convert("RGBA")
r, g, b, a = src.split()
dark = Image.merge("RGBA", (r.point(lambda v: int(v * 0.55)), g.point(lambda v: int(v * 0.5)), b.point(lambda v: int(v * 0.45)), a))
dark.save(ASSETS / "textures/block/checkered_frame_dark.png")
