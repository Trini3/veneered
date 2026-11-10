# Veneer

A Fabric mod for Minecraft 26.3 that adds **frame blocks**: shapes that take on the look of any block you put in them.
Make dirt doors, grass walls, glass stairs, or a slab that is half stone and half planks.

## How it works

- Craft a **Frame Block** (8 from sticks and planks), then craft it into the other shapes.
- **Right-click a frame with a block** to make the frame look like that block. The block is used up and given back when you change or remove it.
- **Sneak + right-click with an empty hand** to take the block back out.
- Breaking a frame drops the empty frame and the blocks that were inside as separate items.

### Frames act like what's inside

- **Mining**: a filled frame mines like the hardest block inside it. Same hardness, same best tool, and the same slow-down if you use the wrong tool. An obsidian frame needs a diamond pickaxe to break quickly. An empty frame mines like wood.
- **Light**: a frame glows if the block inside glows (glowstone, sea lanterns, froglights...).
- **Transparency**: solid frames (block, slab, vertical slab, stairs) block light and hide the faces of blocks next to them when they hold a solid block. Holding glass, leaves or ice, they let light through.

### Shapes

Frame Block, Slab, Vertical Slab, Stairs, Wall, Fence, Fence Gate, Door, Trapdoor, Pressure Plate, Button.

### Two-tone blocks

- **Slabs**: place a frame slab on top of another. Each half keeps its own look, so you can have stone on the bottom and planks on top. Right-click the half you want to change.
- **Vertical slabs**: place a second frame vertical slab against the open side of the first (the vertical half-and-half block). The two halves keep separate looks, which gives walls with a different inside and outside.

Doors, trapdoors and fence gates still open with an empty hand (or any item that isn't a block).

## Building

Requires JDK 25.

```
./gradlew build      # jar ends up in build/libs
./gradlew runClient  # launch a dev client
```

## Recipes

| Item | Recipe |
| --- | --- |
| 8 Frame Block | sticks in the corners, planks on the edges, empty center |
| 6 Frame Slab | 3 frame blocks in a row |
| 6 Frame Vertical Slab | 3 frame blocks in a column |
| 4 Frame Stairs | 6 frame blocks, stair pattern |
| 6 Frame Wall | 6 frame blocks, 2 rows |
| 3 Frame Fence | frame block, stick, frame block (2 rows) |
| Frame Fence Gate | stick, frame block, stick (2 rows) |
| 3 Frame Door | 6 frame blocks, 2 wide by 3 tall |
| 2 Frame Trapdoor | 6 frame blocks, 3 wide by 2 tall |
| Frame Pressure Plate | 2 frame blocks side by side |
| Frame Button | 1 frame block |

## Automated test

`./gradlew runClientGameTest` starts a real client, places every frame shape, fills them (including a real
right-click and sneak-right-click), checks the data syncs to the client and that breaking a frame drops its block,
then saves screenshots to `build/run/clientGameTest/screenshots`. On a headless Linux box, run it under
`xvfb-run` with Mesa's Vulkan driver (`mesa-vulkan-drivers`) installed.
