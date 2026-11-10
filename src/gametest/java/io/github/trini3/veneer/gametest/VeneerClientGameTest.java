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

/**
 * Places every frame shape, fills them (some through real right-click input, the rest directly), checks the
 * block entity data reaches the client, and takes screenshots of the result.
 */
public class VeneerClientGameTest implements FabricClientGameTest {
	private static final int Y = -60; // superflat surface is at y=-61

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
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
			setBlock(sp, clickPos, Blocks.AIR.defaultBlockState());

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

			if (grass != 1) {
				throw new AssertionError("Expected the broken frame to drop 1 grass block, got " + grass);
			}
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
