package io.github.trini3.veneer.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.trini3.veneer.VeneerRegistry;
import io.github.trini3.veneer.block.Appearance;
import io.github.trini3.veneer.block.VeneerBlockEntity;
import io.github.trini3.veneer.block.VeneerPanelBlock;
import io.github.trini3.veneer.block.Checker;
import io.github.trini3.veneer.block.VeneerHalfCheckeredBlock;
import io.github.trini3.veneer.block.VeneerProps;

/**
 * Places every frame shape, fills them (some through real right-click input, the rest directly), checks the
 * block entity data reaches the client, and takes screenshots of the result.
 */
public class VeneerClientGameTest implements FabricClientGameTest {
	private static final int Y = -60; // superflat surface is at y=-61

	@Override
	public void runTest(ClientGameTestContext context) {
		if (VeneerStressGameTest.ENABLED) {
			return; // stress runs measure performance only
		}

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();

			// --- 0. Every Veneer recipe file loaded (a bad one can stop worlds from loading, or be skipped) ---
			sp.getServer().runOnServer(server -> {
				long files = server.getResourceManager().listResources("recipe", id -> id.getNamespace().equals("veneer") && id.getPath().endsWith(".json")).size();
				long loaded = server.getRecipeManager().getRecipes().stream().filter(r -> r.id().identifier().getNamespace().equals("veneer")).count();

				if (files == 0 || loaded != files) {
					throw new AssertionError("Loaded " + loaded + " Veneer recipes out of " + files + " recipe files");
				}

				// No two crafting recipes (Veneer's or vanilla's) may match the same grid as a Veneer recipe.
				var level = server.overworld();
				var crafting = server.getRecipeManager().getRecipes().stream()
						.filter(r -> r.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe).toList();
				List<String> conflicts = new java.util.ArrayList<>();

				for (var holder : crafting) {
					if (!holder.id().identifier().getNamespace().equals("veneer")) {
						continue;
					}

					net.minecraft.world.item.crafting.CraftingInput input;

					if (holder.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped) {
						List<ItemStack> grid = shaped.getIngredients().stream()
								.map(slot -> slot.map(ing -> new ItemStack(ing.items().findFirst().orElseThrow().value())).orElse(ItemStack.EMPTY))
								.toList();
						input = net.minecraft.world.item.crafting.CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
					} else {
						List<ItemStack> items = holder.value().placementInfo().ingredients().stream()
								.map(ing -> new ItemStack(ing.items().findFirst().orElseThrow().value())).toList();
						input = net.minecraft.world.item.crafting.CraftingInput.of(items.size(), 1, items);
					}

					@SuppressWarnings("unchecked")
					List<String> matching = crafting.stream()
							.filter(other -> ((net.minecraft.world.item.crafting.Recipe<net.minecraft.world.item.crafting.CraftingInput>) other.value()).matches(input, level))
							.map(other -> other.id().identifier().toString()).toList();

					if (matching.size() != 1) {
						conflicts.add(holder.id().identifier() + " -> " + matching);
					}
				}

				if (!conflicts.isEmpty()) {
					throw new AssertionError("Crafting recipe conflicts: " + conflicts);
				}
			});
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");

			// --- 1. Real right-click with a dirt item on a frame block ---
			BlockPos clickPos = new BlockPos(0, Y, 3);
			sp.getServer().runCommand("tp @a 0.5 %d 0.5 0 0".formatted(Y));
			setBlock(sp, clickPos, VeneerRegistry.FRAME_BLOCK.defaultBlockState());
			context.waitFor(client -> client.level.getBlockState(clickPos).is(VeneerRegistry.FRAME_BLOCK));
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT)));
			context.waitTicks(2);
			context.getInput().lookAt(clickPos);
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			assertSlot(sp, clickPos, 0, Blocks.DIRT);
			context.waitFor(client -> client.level.getBlockEntity(clickPos) instanceof VeneerBlockEntity be && be.getRenderData() instanceof Appearance a && a.primary() != null && a.primary().is(Blocks.DIRT), 100);

			// Sneak + empty hand takes it back out.
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
			context.waitTicks(2);
			context.getInput().holdShift();
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			context.getInput().releaseShift();
			assertSlot(sp, clickPos, 0, null);

			// Directional blocks keep the orientation they'd get when placed: clicking the north face with a log
			// gives a north-south (Z axis) log.
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG)));
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				if (!(sp.getConnection().getServerLevel().getBlockEntity(clickPos) instanceof VeneerBlockEntity be)
						|| be.get(0) == null || !be.get(0).is(Blocks.OAK_LOG)
						|| be.get(0).getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != Direction.Axis.Z) {
					throw new AssertionError("Expected a Z-axis oak log in the frame");
				}
			});
			setBlock(sp, clickPos, Blocks.AIR.defaultBlockState());

			// Corner blocks point their small quarter at the player: standing north-west of it gives the NW quarter.
			sp.getServer().runCommand("tp @a 0.2 %d 0.5 0 60".formatted(Y));
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VeneerRegistry.FRAME_CORNER)));
			context.waitTicks(5);
			BlockPos cornerPos = new BlockPos(0, Y, 2);
			context.getInput().lookAt(cornerPos.below());
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				BlockState placed = sp.getConnection().getServerLevel().getBlockState(cornerPos);

				if (!placed.is(VeneerRegistry.FRAME_CORNER) || placed.getValue(io.github.trini3.veneer.block.VeneerCornerBlock.FACING) != Direction.WEST) {
					throw new AssertionError("Expected a corner block with its quarter to the north-west, got " + placed);
				}
			});
			setBlock(sp, cornerPos, Blocks.AIR.defaultBlockState());

			// Eighth blocks go in the corner you click: aim at the north-west quarter of the floor in front.
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VeneerRegistry.FRAME_EIGHTH)));
			context.waitTicks(5);
			{
				double dx = 0.25 - 0.2;
				double dy = -1.62;
				double dz = 2.25 - 0.5;
				float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				context.getInput().lookAt(yaw, pitch);
			}
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				BlockState placed = sp.getConnection().getServerLevel().getBlockState(cornerPos);

				if (!placed.is(VeneerRegistry.FRAME_EIGHTH) || placed.getValue(io.github.trini3.veneer.block.VeneerEighthBlock.FACING) != Direction.WEST
						|| placed.getValue(io.github.trini3.veneer.block.VeneerEighthBlock.TOP)) {
					throw new AssertionError("Expected a bottom eighth block in the north-west corner, got " + placed);
				}
			});
			setBlock(sp, cornerPos, Blocks.AIR.defaultBlockState());

			// Half-checkered blocks put their checkered half on the clicked face: up on a floor, down on a ceiling.
			sp.getServer().runOnServer(server -> sp.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK)));
			context.waitTicks(5);
			context.getInput().lookAt(cornerPos.below());
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			BlockPos ceilingPos = cornerPos.above(2);
			setBlock(sp, ceilingPos, Blocks.STONE.defaultBlockState());
			context.waitFor(client -> client.level.getBlockState(ceilingPos).is(Blocks.STONE));
			{
				// Aim at the middle of the stone's bottom face.
				double dx = 0.5 - 0.2;
				double dy = 2.0 - 1.62;
				double dz = 2.5 - 0.5;
				float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
				float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
				context.getInput().lookAt(yaw, pitch);
			}
			context.waitTick();
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				BlockState floor = level.getBlockState(cornerPos);
				BlockState ceiling = level.getBlockState(cornerPos.above());

				if (!floor.is(VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK) || floor.getValue(VeneerHalfCheckeredBlock.FACING) != Direction.UP
						|| !ceiling.is(VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK) || ceiling.getValue(VeneerHalfCheckeredBlock.FACING) != Direction.DOWN) {
					throw new AssertionError("Expected half-checkered blocks facing up (floor) and down (ceiling), got " + floor + " and " + ceiling);
				}
			});
			setBlock(sp, cornerPos, Blocks.AIR.defaultBlockState());
			setBlock(sp, cornerPos.above(), Blocks.AIR.defaultBlockState());
			setBlock(sp, ceilingPos, Blocks.AIR.defaultBlockState());

			// --- 2. Showcase row: every shape, filled directly ---
			BlockState slabDouble = VeneerRegistry.FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
			BlockState panelDouble = VeneerRegistry.FRAME_PANEL.defaultBlockState().setValue(VeneerPanelBlock.FACING, Direction.SOUTH).setValue(VeneerPanelBlock.DOUBLE, true);

			record Entry(BlockState frame, Block a, Block b) { }
			List<Entry> row = List.of(
					new Entry(VeneerRegistry.FRAME_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK, null),
					new Entry(VeneerRegistry.FRAME_SLAB.defaultBlockState(), Blocks.STONE_BRICKS, null),
					new Entry(slabDouble, Blocks.COBBLESTONE, Blocks.OAK_PLANKS),
					new Entry(panelDouble, Blocks.BRICKS, Blocks.QUARTZ_BLOCK),
					new Entry(VeneerRegistry.FRAME_STAIRS.defaultBlockState(), Blocks.MOSSY_COBBLESTONE, null),
					new Entry(VeneerRegistry.FRAME_WALL.defaultBlockState(), Blocks.GRASS_BLOCK, null),
					new Entry(VeneerRegistry.FRAME_FENCE.defaultBlockState(), Blocks.BIRCH_LOG, null),
					new Entry(VeneerRegistry.FRAME_FENCE_GATE.defaultBlockState(), Blocks.DIAMOND_BLOCK, null),
					new Entry(VeneerRegistry.FRAME_TRAPDOOR.defaultBlockState(), Blocks.OAK_LEAVES, null),
					new Entry(VeneerRegistry.FRAME_PRESSURE_PLATE.defaultBlockState(), Blocks.GOLD_BLOCK, null),
					new Entry(VeneerRegistry.FRAME_BLOCK.defaultBlockState(), null, null) // empty frame for comparison
			);

			for (int i = 0; i < row.size(); i++) {
				Entry e = row.get(i);
				BlockPos pos = new BlockPos(i * 2 - 10, Y, 4);
				setBlock(sp, pos, e.frame());
				fill(sp, pos, e.a(), e.b());
			}

			// A dirt door, placed via its item so both halves exist.
			BlockPos doorPos = new BlockPos(12, Y, 4);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				BlockState lower = VeneerRegistry.FRAME_DOOR.defaultBlockState();
				// Flag 2 = update clients only, so the half-placed door isn't removed by shape updates.
				level.setBlock(doorPos, lower, Block.UPDATE_CLIENTS);
				level.setBlock(doorPos.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
			});
			context.waitTicks(2);
			fill(sp, doorPos, Blocks.DIRT, null);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				((io.github.trini3.veneer.block.VeneerHolder) VeneerRegistry.FRAME_DOOR).onAppearanceChanged(level, doorPos, level.getBlockState(doorPos), 0);
			});
			assertSlot(sp, doorPos.above(), 0, Blocks.DIRT);

			// Look at the row from behind and above.
			sp.getServer().runCommand("tp @a 1.0 %d -4.0 0 25".formatted(Y + 4));
			context.waitTicks(10);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("veneer_row_front");

			sp.getServer().runCommand("tp @a 1.0 %d 12.0 180 25".formatted(Y + 4));
			context.waitTicks(10);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("veneer_row_back");

			// Close-ups of the left and right halves of the row.
			for (int[] shot : new int[][] {{-5, 0}, {7, 1}}) {
				sp.getServer().runCommand("tp @a %d.5 %d 0.5 0 30".formatted(shot[0], Y + 2));
				context.waitTicks(10);
				sp.getConnection().waitForChunksRender();
				context.waitTicks(10);
				context.takeScreenshot("veneer_closeup_" + shot[1]);
			}

			// --- 3. Breaking a filled frame drops its contents ---
			BlockPos breakPos = new BlockPos(-10, Y, 4);
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("setblock -10 %d 4 minecraft:air destroy".formatted(Y));
			context.waitTicks(5);
			int grass = sp.getServer().computeOnServer(server -> sp.getConnection().getServerLevel()
					.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(breakPos).inflate(2))
					.stream().filter(it -> it.getItem().is(Items.GRASS_BLOCK)).mapToInt(it -> it.getItem().getCount()).sum());

			int frames = sp.getServer().computeOnServer(server -> sp.getConnection().getServerLevel()
					.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(breakPos).inflate(2))
					.stream().filter(it -> it.getItem().is(VeneerRegistry.FRAME_BLOCK.asItem())).mapToInt(it -> it.getItem().getCount()).sum());

			if (grass != 1 || frames != 1) {
				throw new AssertionError("Expected the broken frame to drop 1 grass block and 1 frame, got " + grass + " and " + frames);
			}

			// --- 3b. Breaking a filled frame in creative drops nothing (a real left-click) ---
			BlockPos creativePos = new BlockPos(0, Y, 2);
			sp.getServer().runCommand("kill @e[type=item]");
			setBlock(sp, creativePos, VeneerRegistry.FRAME_BLOCK.defaultBlockState());
			fill(sp, creativePos, Blocks.GRASS_BLOCK, null);
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("tp @a 0.5 %d 0.5 0 0".formatted(Y));
			context.waitTicks(5);
			context.getInput().lookAt(creativePos);
			context.waitTick();
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				if (!level.getBlockState(creativePos).isAir()) {
					throw new AssertionError("Expected the creative left-click to break the frame, got " + level.getBlockState(creativePos));
				}

				int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(creativePos).inflate(3)).size();

				if (dropped != 0) {
					throw new AssertionError("Expected no drops from a creative break, got " + dropped + " item(s)");
				}
			});

			// Same for a door broken from its upper half (the lower half is the one holding the drops).
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				BlockState lower = VeneerRegistry.FRAME_DOOR.defaultBlockState();
				level.setBlock(creativePos, lower, Block.UPDATE_CLIENTS);
				level.setBlock(creativePos.above(), lower.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
			});
			fill(sp, creativePos, Blocks.DIRT, null);
			context.waitTicks(10);
			sp.getServer().runOnServer(server -> {
				// Loot tables: only the lower door half drops the door, and a double slab drops two slabs.
				ServerLevel level = sp.getConnection().getServerLevel();
				var upperDrops = Block.getDrops(level.getBlockState(creativePos.above()), level, creativePos.above(), level.getBlockEntity(creativePos.above()));
				var lowerDrops = Block.getDrops(level.getBlockState(creativePos), level, creativePos, level.getBlockEntity(creativePos));
				var slabDrops = Block.getDrops(VeneerRegistry.FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), level, creativePos, null);

				if (!upperDrops.isEmpty() || lowerDrops.size() != 1 || slabDrops.size() != 1 || slabDrops.getFirst().getCount() != 2) {
					throw new AssertionError("Unexpected loot: upper door " + upperDrops + ", lower door " + lowerDrops + ", double slab " + slabDrops);
				}
			});
			context.getInput().lookAt(creativePos.above());
			context.waitTick();
			context.getInput().pressKey(options -> options.keyAttack);
			context.waitTicks(5);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				if (!level.getBlockState(creativePos).isAir() || !level.getBlockState(creativePos.above()).isAir()) {
					throw new AssertionError("Expected the creative left-click to remove both door halves");
				}

				var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(creativePos).inflate(3));

				if (!dropped.isEmpty()) {
					throw new AssertionError("Expected no drops from a creative door break, got " + dropped.stream().map(it -> it.getItem().toString()).toList());
				}
			});
			sp.getServer().runCommand("gamemode survival @a");

			// --- 4. Frames mine like the hardest block inside them ---
			BlockPos hardPos = new BlockPos(0, Y, -4);
			setBlock(sp, hardPos, VeneerRegistry.FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
			fill(sp, hardPos, Blocks.DIRT, Blocks.OBSIDIAN);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				var player = sp.getConnection().getServerPlayer();
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				float frame = level.getBlockState(hardPos).getDestroyProgress(player, level, hardPos);
				float obsidian = Blocks.OBSIDIAN.defaultBlockState().getDestroyProgress(player, level, hardPos);

				if (Math.abs(frame - obsidian) > 1e-6) {
					throw new AssertionError("Expected obsidian-filled frame to mine like obsidian: " + frame + " vs " + obsidian);
				}

				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
				float framePick = level.getBlockState(hardPos).getDestroyProgress(player, level, hardPos);
				float obsidianPick = Blocks.OBSIDIAN.defaultBlockState().getDestroyProgress(player, level, hardPos);

				if (Math.abs(framePick - obsidianPick) > 1e-6 || framePick <= frame) {
					throw new AssertionError("Expected a diamond pickaxe to mine it like obsidian: " + framePick + " vs " + obsidianPick);
				}
			});

			// --- 5. Frames take light emission and opacity from the block inside ---
			BlockPos glowPos = new BlockPos(4, Y, -4);
			BlockPos glassPos = new BlockPos(6, Y, -4);
			setBlock(sp, glowPos, VeneerRegistry.FRAME_BLOCK.defaultBlockState());
			setBlock(sp, glassPos, VeneerRegistry.FRAME_BLOCK.defaultBlockState());
			fill(sp, glowPos, Blocks.GLOWSTONE, null);
			fill(sp, glassPos, Blocks.GLASS, null);
			context.waitTicks(10);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				BlockState glow = level.getBlockState(glowPos);
				BlockState glass = level.getBlockState(glassPos);
				BlockState stone = level.getBlockState(hardPos);
				int lightAbove = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, glowPos.above());

				if (glow.getLightEmission() != 15 || lightAbove < 14) {
					throw new AssertionError("Glowstone frame should glow: emission " + glow.getLightEmission() + ", light above " + lightAbove);
				}

				if (glass.getValue(VeneerProps.OPAQUE) || !glass.propagatesSkylightDown()) {
					throw new AssertionError("Glass frame should let light through: " + glass);
				}

				if (!stone.getValue(VeneerProps.OPAQUE) || stone.propagatesSkylightDown()) {
					throw new AssertionError("Obsidian/dirt frame should block light: " + stone);
				}
			});

			// --- 6. Glowing frames always glow; the pane frame accepts a block ---
			BlockPos glowingPos = new BlockPos(8, Y, -4);
			BlockPos panePos = new BlockPos(10, Y, -4);
			setBlock(sp, glowingPos, VeneerRegistry.GLOWING_FRAME_SLAB.defaultBlockState());
			setBlock(sp, panePos, VeneerRegistry.FRAME_PANE.defaultBlockState());
			fill(sp, glowingPos, Blocks.DIRT, null);
			fill(sp, panePos, Blocks.GLASS, null);
			context.waitTicks(10);
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				int emission = level.getBlockState(glowingPos).getLightEmission();
				int emptyEmission = VeneerRegistry.GLOWING_FRAME_BLOCK.defaultBlockState().getLightEmission();

				if (emission != 15 || emptyEmission != 15) {
					throw new AssertionError("Glowing frames should emit 15 (filled with dirt: " + emission + ", empty: " + emptyEmission + ")");
				}
			});
			assertSlot(sp, panePos, 0, Blocks.GLASS);

			// --- 7. Checkered frames ---
			checkCheckerSlots();
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();
				BlockState slab = VeneerRegistry.CHECKERED_FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
				BlockState panel = VeneerRegistry.CHECKERED_FRAME_PANEL.defaultBlockState().setValue(VeneerPanelBlock.DOUBLE, true);
				int slabDrops = Block.getDrops(slab, level, BlockPos.ZERO, null).stream().mapToInt(ItemStack::getCount).sum();
				int panelDrops = Block.getDrops(panel, level, BlockPos.ZERO, null).stream().mapToInt(ItemStack::getCount).sum();

				if (slabDrops != 2 || panelDrops != 2) {
					throw new AssertionError("Double checkered slab/vertical slab should drop 2, got " + slabDrops + " and " + panelDrops);
				}
			});

			// A small checkered floor plus one of each piece, filled with quartz and coal blocks.
			List<BlockState> pieces = List.of(
					VeneerRegistry.CHECKERED_FRAME_BLOCK.defaultBlockState(),
					VeneerRegistry.CHECKERED_FRAME_BLOCK.defaultBlockState(),
					VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK.defaultBlockState(),
					VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK.defaultBlockState().setValue(VeneerHalfCheckeredBlock.FACING, Direction.NORTH),
					VeneerRegistry.CHECKERED_FRAME_SLAB.defaultBlockState(),
					VeneerRegistry.CHECKERED_FRAME_PANEL.defaultBlockState(),
					VeneerRegistry.CHECKERED_FRAME_BLOCK.defaultBlockState() // left empty
			);

			for (int i = 0; i < pieces.size(); i++) {
				BlockPos pos = new BlockPos(i * 2 - 6, Y, -10);
				setBlock(sp, pos, pieces.get(i));

				if (i < pieces.size() - 1) {
					fill(sp, pos, Blocks.QUARTZ_BLOCK, Blocks.COAL_BLOCK);
				}
			}

			for (int x = -6; x <= -4; x++) {
				for (int z = -13; z <= -12; z++) {
					BlockPos pos = new BlockPos(x, Y - 1, z);
					setBlock(sp, pos, VeneerRegistry.CHECKERED_FRAME_BLOCK.defaultBlockState());
					fill(sp, pos, Blocks.QUARTZ_BLOCK, Blocks.COAL_BLOCK);
				}
			}

			assertSlot(sp, new BlockPos(-6, Y, -10), 1, Blocks.COAL_BLOCK);
			sp.getServer().runCommand("tp @a 0.5 %d -4.5 180 35".formatted(Y + 3));
			context.waitTicks(10);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(10);
			context.takeScreenshot("veneer_checkered");
		}
	}

	/** Checker cells alternate on every axis, and the half-checkered block's solid half is always slot 0. */
	private static void checkCheckerSlots() {
		var half = (VeneerHalfCheckeredBlock) VeneerRegistry.HALF_CHECKERED_FRAME_BLOCK;
		BlockState up = half.defaultBlockState().setValue(VeneerHalfCheckeredBlock.FACING, Direction.UP);
		BlockState west = half.defaultBlockState().setValue(VeneerHalfCheckeredBlock.FACING, Direction.WEST);
		var slab = (io.github.trini3.veneer.block.VeneerHolder) VeneerRegistry.CHECKERED_FRAME_SLAB;
		BlockState topSlab = VeneerRegistry.CHECKERED_FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);

		boolean ok = Checker.slot(0.25, 0.25, 0.25) == 0 && Checker.slot(0.75, 0.25, 0.25) == 1
				&& Checker.slot(0.75, 0.75, 0.25) == 0 && Checker.slot(0.75, 0.75, 0.75) == 1
				&& half.slotForPoint(up, 0.75F, 0.25F, 0.25F) == 0 // solid bottom half
				&& half.slotForPoint(up, 0.25F, 0.75F, 0.25F) == 1 && half.slotForPoint(up, 0.75F, 0.75F, 0.25F) == 0
				&& half.slotForPoint(west, 0.75F, 0.75F, 0.25F) == 0 // solid east half
				&& half.slotForPoint(west, 0.25F, 0.75F, 0.25F) == 1
				&& slab.slotForPoint(topSlab, 0.25F, 0.75F, 0.25F) == 1;

		if (!ok) {
			throw new AssertionError("Checkered slot layout is wrong");
		}
	}

	private static void setBlock(TestSingleplayerContext sp, BlockPos pos, BlockState state) {
		sp.getServer().runOnServer(server -> sp.getConnection().getServerLevel().setBlockAndUpdate(pos, state));
	}

	private static void fill(TestSingleplayerContext sp, BlockPos pos, Block a, Block b) {
		sp.getServer().runOnServer(server -> {
			if (sp.getConnection().getServerLevel().getBlockEntity(pos) instanceof VeneerBlockEntity be) {
				if (a != null) be.set(0, a.defaultBlockState());
				if (b != null) be.set(1, b.defaultBlockState());
			} else {
				throw new AssertionError("No frame block entity at " + pos);
			}
		});
	}

	private static void assertSlot(TestSingleplayerContext sp, BlockPos pos, int slot, Block expected) {
		sp.getServer().runOnServer(server -> {
			if (!(sp.getConnection().getServerLevel().getBlockEntity(pos) instanceof VeneerBlockEntity be)) {
				throw new AssertionError("No frame block entity at " + pos);
			}

			BlockState actual = be.get(slot);
			boolean ok = expected == null ? actual == null : actual != null && actual.is(expected);

			if (!ok) {
				throw new AssertionError("Slot " + slot + " at " + pos + " expected " + expected + " but was " + actual);
			}
		});
	}
}
