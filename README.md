# Veneer

A Fabric mod for Minecraft 26.3 that adds **frame blocks**: shapes that take on the look of any block you put in them.
Make dirt doors, grass walls, glass stairs, or a slab that is half stone and half planks.

## How it works

- Craft **16 Frame Blocks** from 8 sticks around one plank. Put a frame block in a **stonecutter** to get any shape, or craft the usual shapes (slabs, stairs, walls, fences, gates, doors, pressure plates, buttons) with their normal vanilla patterns. Trapdoors use a 2×2 square instead, so they don't clash with walls.
- **Right-click a frame with a block** to make the frame look like that block. The block is used up and given back when you change or remove it.
- **Sneak + right-click with an empty hand** to take the block back out.
- Breaking a frame drops the empty frame and the blocks that were inside as separate items.

### Frames act like what's inside

- **Mining**: a filled frame mines like the hardest block inside it. Same hardness, same best tool, and the same slow-down if you use the wrong tool. An obsidian frame needs a diamond pickaxe to break quickly. An empty frame mines like wood.
- **Light**: a frame glows if the block inside glows (glowstone, sea lanterns, froglights...).
- **Transparency**: solid frames (block, slab, vertical slab, stairs) block light and hide the faces of blocks next to them when they hold a solid block. Holding glass, leaves or ice, they let light through.

### Shapes

Frame Block, Slab, Vertical Slab, Corner Block, Corner Slab, Quarter Slab (a quarter-height layer), Horizontal Quarter (half a slab), Vertical Quarter (a quarter-block pillar), Eighth Block (a half-size cube in one corner), Stairs, Half Stairs, Wall, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button, Frame Panel (shaped like a glass pane). Checkered Frame Block, Half-Checkered Frame Block, Checkered Frame Slab and Checkered Frame Vertical Slab hold two blocks in a checkerboard. Glowing Frame Block, Slab and Stairs always give off full glowstone light, whatever they hold.

### Two-tone blocks

- **Slabs**: place a frame slab on top of another. Each half keeps its own look, so you can have stone on the bottom and planks on top. Right-click the half you want to change.
- **Vertical slabs**: place a second frame vertical slab against the open side of the first (the vertical half-and-half block). The two halves keep separate looks, which gives walls with a different inside and outside.

- **Half-and-half slabs**: place a second horizontal quarter into the empty half of the first. The slab is split down the middle with a different block on each side.
- **Half-and-half stairs**: place a second half stairs into the empty half of the first.
- **Corner blocks and corner slabs**: split 3/4 and 1/4, with the small quarter pointing toward you when you place it. Stand inside the room when placing a corner and the quarter lands on the inside corner, so it lines up with two-tone vertical-slab walls (outside look in the L, inside look in the quarter).
- **Checkered frames**: each one is split into half-size cells, and right-clicking a cell fills every cell of that colour. The pattern lines up across neighbouring checkered pieces, and two checkered slabs or vertical slabs together make a checkered block. The **Half-Checkered Frame Block** has one checkered half and one solid half (the solid half uses the same block as the light cells). Its checkered side faces the side you clicked when placing it: up when placed on a floor, down on a ceiling, outward on a wall. An empty checkered frame shows light and dark cells so you can see the pattern.

Directional blocks keep the orientation they'd get if you placed them normally: a log follows the face you click, a furnace or pumpkin faces you. Right-click again with the same block to re-orient it without using another item.

Doors, trapdoors and fence gates still open with an empty hand (or any item that isn't a block).

## Compatibility and performance

- No changes to vanilla blocks, models or rendering. Frames are the mod's own blocks, rendered through the Fabric Renderer API (works with Indigo and Sodium's Fabric Renderer API support).
- One small client-only mixin: it leaves Veneer's own block entities off vanilla's per-frame block entity list, since frames are drawn by the chunk mesh and have nothing to render there. It only skips Veneer block entities and changes nothing for anything else.
- Vanilla tags are only added to (`replace: false`).
- Frame block entities never tick. Each one stores two block states (about 5 bytes per frame in the compressed save file).
- Repainted frame geometry is cached per block state and per combination of held blocks, and tint colours are computed once per block, so meshing a chunk of frames costs about the same as meshing the same vanilla blocks.
- Light emission is stored in 4 steps (0, 5, 10, 15) to keep the block-state count down.

### Stress test

`VENEER_STRESS=1 ./gradlew runClientGameTest` builds about 64,000 filled frames, measures them, swaps every frame
for the vanilla block that looks the same and measures again. Results land in `build/run/clientGameTest/veneer-stress.txt`.
The cost that remains is the one any block entity has: vanilla and Fabric copy a chunk's block entity list each time a
section of it is rebuilt, so chunks packed with thousands of frames take longer to load and re-mesh than plain blocks.

## Building

Requires JDK 25.

```
./gradlew build      # jar ends up in build/libs
./gradlew runClient  # launch a dev client
```

## Recipes

- **16 Frame Block**: 8 sticks around 1 plank (any wood).
- **Stonecutter** (1 frame block): 2 slabs, 2 vertical slabs, 1 corner block, 2 corner slabs, 4 quarter slabs, 4 horizontal quarters, 4 vertical quarters, 8 eighth blocks, 1 stairs, 2 half stairs, 1 wall, 1 fence, 1 fence gate, 1 door, 1 trapdoor, 2 pressure plates, 4 buttons, 1 checkered block, 1 half-checkered block, 2 checkered slabs, 2 checkered vertical slabs.
- **8 Glowing Frame Block**: 8 frame blocks around 1 glowstone. Glowing slabs and stairs come from the stonecutter (2 slabs, 1 stairs) or the usual slab/stairs patterns.
- **8 Frame Panels**: 3 frame blocks in a vertical column (or 2 from the stonecutter).
- **Crafting table**, vanilla patterns using frame blocks in place of planks: slab (3 → 6), stairs (6 → 4), wall (6 → 6), fence (with sticks, → 3), fence gate (with sticks), door (6 → 3), trapdoor (4 in a 2×2 square → 2), pressure plate (2 → 1), button (1 → 1).

## Known issues

- **Fixed 2026-11-14:** a Frame Panel build shared as a zip stopped worlds from loading. Its jar was built before the recipe fix and still used `"category": "decorations"`, which 26.3 rejects for crafting recipes (use `misc`, `building`, `redstone` or `equipment`). The GitHub Actions build was never affected.
- **Releasing:** ship jars only through `tools/package.sh`. It rebuilds the jar and refuses to copy it if its resources differ from the source. `runClientGameTest` does not rebuild `build/libs`.
- **Fixed 2026-11-14, recipe clashes:** wall, trapdoor and Frame Panel all used two rows of 3 frame blocks, so the crafting table could only ever give one of them. Trapdoor moved to a 2×2 square and the panel to a vertical column of 3; the wall kept the vanilla pattern. Veneer recipes can't clash with vanilla ones because they all take Veneer items. Test 0 in `VeneerClientGameTest` now crafts each Veneer recipe's grid and fails if any other crafting recipe (vanilla included) matches it, so check it after adding a recipe.
- **Checkered models:** generated by `tools/gen_checkered.py`, one model per facing instead of blockstate rotations, because a 90° rotation flips the checker parity and the empty frame's dark cells would stop matching `Checker.slot`. The pattern uses world axes so all checkered pieces line up. Each cell is its own element (4 quads per face instead of 1), a small cost only for these blocks.
- **26.3 data formats:** loot tables use `condition` / `modifier` / `minecraft:match_block`. The older `conditions` / `functions` / `block_state_property` keys are silently ignored.

## Automated test

`./gradlew runClientGameTest` starts a real client, places every frame shape, fills them (including a real
right-click and sneak-right-click), checks the data syncs to the client and that breaking a frame drops its block,
then saves screenshots to `build/run/clientGameTest/screenshots`. On a headless Linux box, run it under
`xvfb-run` with Mesa's Vulkan driver (`mesa-vulkan-drivers`) installed.
