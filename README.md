# Veneer

A Fabric mod for Minecraft 26.3 that adds **frame blocks**: shapes that take on the look of any block you put in them.
Make dirt doors, grass walls, glass stairs, or a slab that is half stone and half planks.

## How it works

- Craft **16 Frame Blocks** from 8 sticks around one plank. Put a frame block in a **stonecutter** to get any shape, or craft the usual shapes (slabs, stairs, walls, fences, gates, doors, trapdoors, pressure plates, buttons) with their normal vanilla patterns.
- **Right-click a frame with a block** to make the frame look like that block. The block is used up and given back when you change or remove it.
- **Sneak + right-click with an empty hand** to take the block back out.
- Breaking a frame drops the empty frame and the blocks that were inside as separate items.

### Frames act like what's inside

- **Mining**: a filled frame mines like the hardest block inside it. Same hardness, same best tool, and the same slow-down if you use the wrong tool. An obsidian frame needs a diamond pickaxe to break quickly. An empty frame mines like wood.
- **Light**: a frame glows if the block inside glows (glowstone, sea lanterns, froglights...).
- **Transparency**: solid frames (block, slab, vertical slab, stairs) block light and hide the faces of blocks next to them when they hold a solid block. Holding glass, leaves or ice, they let light through.

### Shapes

Frame Block, Slab, Vertical Slab, Corner Block, Corner Slab, Quarter Slab (a quarter-height layer), Horizontal Quarter (half a slab), Vertical Quarter (a quarter-block pillar), Stairs, Half Stairs, Wall, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button.

### Two-tone blocks

- **Slabs**: place a frame slab on top of another. Each half keeps its own look, so you can have stone on the bottom and planks on top. Right-click the half you want to change.
- **Vertical slabs**: place a second frame vertical slab against the open side of the first (the vertical half-and-half block). The two halves keep separate looks, which gives walls with a different inside and outside.

- **Half-and-half slabs**: place a second horizontal quarter into the empty half of the first. The slab is split down the middle with a different block on each side.
- **Half-and-half stairs**: place a second half stairs into the empty half of the first.
- **Corner blocks and corner slabs**: split 3/4 and 1/4, with the small quarter pointing toward you when you place it. Stand inside the room when placing a corner and the quarter lands on the inside corner, so it lines up with two-tone vertical-slab walls (outside look in the L, inside look in the quarter).

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
- **Stonecutter** (1 frame block): 2 slabs, 2 vertical slabs, 1 corner block, 2 corner slabs, 4 quarter slabs, 4 horizontal quarters, 4 vertical quarters, 1 stairs, 2 half stairs, 1 wall, 1 fence, 1 fence gate, 1 door, 1 trapdoor, 2 pressure plates, 4 buttons.
- **Crafting table**, vanilla patterns using frame blocks in place of planks: slab (3 → 6), stairs (6 → 4), wall (6 → 6), fence (with sticks, → 3), fence gate (with sticks), door (6 → 3), trapdoor (6 → 2), pressure plate (2 → 1), button (1 → 1).

## Automated test

`./gradlew runClientGameTest` starts a real client, places every frame shape, fills them (including a real
right-click and sneak-right-click), checks the data syncs to the client and that breaking a frame drops its block,
then saves screenshots to `build/run/clientGameTest/screenshots`. On a headless Linux box, run it under
`xvfb-run` with Mesa's Vulkan driver (`mesa-vulkan-drivers`) installed.
