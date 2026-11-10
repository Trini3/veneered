package io.github.trini3.veneer.gametest;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import io.github.trini3.veneer.VeneerRegistry;
import io.github.trini3.veneer.block.VeneerBlockEntity;
import io.github.trini3.veneer.block.VeneerCornerBlock;
import io.github.trini3.veneer.block.VeneerPanelBlock;

/** Builds a showcase of every frame shape (empty, filled, two-tone, connected) and screenshots it. */
public class VeneerShowcaseGameTest implements FabricClientGameTest {
	private static final int Y = -60;

	private record Placement(BlockPos pos, BlockState state, @Nullable Block a, @Nullable Block b) { }

	private final List<Placement> scene = new ArrayList<>();
	private final List<Object[]> oriented = new ArrayList<>();

	private void put(int x, int z, BlockState state, @Nullable Block a, @Nullable Block b) {
		scene.add(new Placement(new BlockPos(x, Y, z), state, a, b));
	}

	private void put(int x, int z, BlockState state, @Nullable Block a) {
		put(x, z, state, a, null);
	}

	private void door(int x, int z, boolean open, @Nullable Block look) {
		BlockState lower = VeneerRegistry.FRAME_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, open);
		scene.add(new Placement(new BlockPos(x, Y, z), lower, look, null));
		scene.add(new Placement(new BlockPos(x, Y + 1, z), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), look, null));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		BlockState block = VeneerRegistry.FRAME_BLOCK.defaultBlockState();
		BlockState slab = VeneerRegistry.FRAME_SLAB.defaultBlockState();
		BlockState panel = VeneerRegistry.FRAME_PANEL.defaultBlockState().setValue(VeneerPanelBlock.FACING, Direction.SOUTH);
		BlockState stairs = VeneerRegistry.FRAME_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
		BlockState wall = VeneerRegistry.FRAME_WALL.defaultBlockState();
		BlockState fence = VeneerRegistry.FRAME_FENCE.defaultBlockState();
		BlockState gate = VeneerRegistry.FRAME_FENCE_GATE.defaultBlockState();
		BlockState trapdoor = VeneerRegistry.FRAME_TRAPDOOR.defaultBlockState();
		BlockState plate = VeneerRegistry.FRAME_PRESSURE_PLATE.defaultBlockState();
		BlockState button = VeneerRegistry.FRAME_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.FLOOR);

		// Row 1 (z=6): every shape, empty.
		put(0, 6, block, null);
		put(2, 6, slab, null);
		put(4, 6, panel, null);
		put(6, 6, stairs, null);
		put(8, 6, wall, null);
		put(10, 6, fence, null);
		put(12, 6, gate, null);
		door(14, 6, false, null);
		put(16, 6, trapdoor, null);
		put(18, 6, plate, null);
		put(20, 6, button, null);

		// Row 2 (z=10): every shape, filled.
		put(0, 10, block, Blocks.GRASS_BLOCK);
		put(2, 10, slab, Blocks.STONE_BRICKS);
		put(4, 10, panel, Blocks.BRICKS);
		put(6, 10, stairs, Blocks.DIRT);
		put(8, 10, wall, Blocks.MOSSY_COBBLESTONE);
		put(10, 10, fence, Blocks.BIRCH_PLANKS);
		put(12, 10, gate, Blocks.CHERRY_PLANKS);
		door(14, 10, false, Blocks.GRASS_BLOCK);
		put(16, 10, trapdoor, Blocks.GLASS);
		put(18, 10, plate, Blocks.GOLD_BLOCK);
		put(20, 10, button, Blocks.DIAMOND_BLOCK);

		// Row 3 (z=14): two-tone pieces and connected builds.
		put(0, 14, slab, Blocks.SANDSTONE);
		put(1, 14, slab.setValue(SlabBlock.TYPE, SlabType.TOP), null, Blocks.SANDSTONE);
		put(2, 14, slab.setValue(SlabBlock.TYPE, SlabType.DOUBLE), Blocks.COBBLESTONE, Blocks.OAK_PLANKS);
		put(4, 14, panel, Blocks.QUARTZ_BLOCK);
		put(5, 14, panel.setValue(VeneerPanelBlock.DOUBLE, true), Blocks.BRICKS, Blocks.QUARTZ_BLOCK);
		put(7, 14, wall, Blocks.STONE_BRICKS);
		put(8, 14, wall, Blocks.STONE_BRICKS);
		put(9, 14, wall, Blocks.STONE_BRICKS);
		put(11, 14, fence, Blocks.SPRUCE_LOG);
		put(12, 14, gate, Blocks.SPRUCE_LOG);
		put(13, 14, fence, Blocks.SPRUCE_LOG);
		door(15, 14, true, Blocks.DIRT);
		put(17, 14, trapdoor.setValue(TrapDoorBlock.OPEN, true), Blocks.OAK_LEAVES);
		put(19, 14, block, Blocks.POLISHED_ANDESITE);
		put(19, 13, VeneerRegistry.FRAME_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH), Blocks.REDSTONE_BLOCK);

		// Stair corners: an outer corner and a run.
		put(21, 14, stairs, Blocks.STONE);
		put(22, 14, stairs, Blocks.STONE);
		put(22, 15, stairs.setValue(StairBlock.FACING, Direction.EAST), Blocks.STONE);

		// Row 4 (z=19): a wall of double panels, bricks outside (south), planks inside (north).
		for (int x = 0; x < 5; x++) {
			for (int y = 0; y < 3; y++) {
				scene.add(new Placement(new BlockPos(x, Y + y, 19), panel.setValue(VeneerPanelBlock.DOUBLE, true), Blocks.BRICKS, Blocks.SPRUCE_PLANKS));
			}
		}

		// Lighting (z=19, x=8..16): glowing blocks in frames, shown at night.
		put(8, 19, block, Blocks.GLOWSTONE);
		put(10, 19, slab, Blocks.SEA_LANTERN);
		put(12, 19, stairs, Blocks.SHROOMLIGHT);
		put(14, 19, panel, Blocks.JACK_O_LANTERN);
		put(16, 19, wall, Blocks.OCHRE_FROGLIGHT);

				// Directional blocks and corners (z=6, x=24..36).
		put(24, 6, block, null, null);
		put(26, 6, block, null, null);
		put(28, 6, block, null, null);
		put(30, 6, block, null, null);
		put(32, 6, stairs, null, null);
		put(34, 6, VeneerRegistry.FRAME_CORNER.defaultBlockState().setValue(VeneerCornerBlock.FACING, Direction.WEST), Blocks.STONE_BRICKS, Blocks.OAK_PLANKS);
		put(36, 6, VeneerRegistry.FRAME_CORNER_SLAB.defaultBlockState().setValue(VeneerCornerBlock.FACING, Direction.WEST), Blocks.SMOOTH_STONE, Blocks.CRIMSON_PLANKS);
		oriented.add(new Object[] {new BlockPos(24, Y, 6), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)});
		oriented.add(new Object[] {new BlockPos(26, Y, 6), Blocks.OAK_LOG.defaultBlockState()});
		oriented.add(new Object[] {new BlockPos(28, Y, 6), Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z)});
		oriented.add(new Object[] {new BlockPos(30, Y, 6), Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, Direction.NORTH)});
		oriented.add(new Object[] {new BlockPos(32, Y, 6), Blocks.BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)});

		// A room corner (x=24..28, z=20..24): vertical-slab walls with bricks outside and planks inside,
		// meeting at corner blocks whose quarter sits on the inside.
		for (int y = 0; y < 2; y++) {
			for (int x = 24; x < 28; x++) {
				scene.add(new Placement(new BlockPos(x, Y + y, 24), panel.setValue(VeneerPanelBlock.DOUBLE, true), Blocks.BRICKS, Blocks.SPRUCE_PLANKS));
			}

			for (int z = 20; z < 24; z++) {
				scene.add(new Placement(new BlockPos(28, Y + y, z), panel.setValue(VeneerPanelBlock.FACING, Direction.EAST).setValue(VeneerPanelBlock.DOUBLE, true), Blocks.BRICKS, Blocks.SPRUCE_PLANKS));
			}

			scene.add(new Placement(new BlockPos(28, Y + y, 24), VeneerRegistry.FRAME_CORNER.defaultBlockState().setValue(VeneerCornerBlock.FACING, Direction.WEST), Blocks.BRICKS, Blocks.SPRUCE_PLANKS));
		}

				try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");
			context.getInput().resizeWindow(1600, 900);
			context.getInput().pressKey(options -> options.keyToggleGui); // hide the HUD

			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				for (Placement p : scene) {
					level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS);
				}

				// Second pass: let walls, fences, gates and stairs connect to their neighbours.
				for (Placement p : scene) {
					if (!(p.state().getBlock() instanceof DoorBlock)) {
						level.setBlock(p.pos(), Block.updateFromNeighbourShapes(level.getBlockState(p.pos()), level, p.pos()), Block.UPDATE_CLIENTS);
					}
				}

				for (Placement p : scene) {
					if (level.getBlockEntity(p.pos()) instanceof VeneerBlockEntity be) {
						if (p.a() != null) be.set(0, p.a().defaultBlockState());
						if (p.b() != null) be.set(1, p.b().defaultBlockState());
					}
				}

				for (Object[] o : oriented) {
					if (level.getBlockEntity((BlockPos) o[0]) instanceof VeneerBlockEntity be) {
						be.set(0, (BlockState) o[1]);
					}
				}
			});

			shot(context, sp, "showcase_overview", 11, 9, 0, 0, 45);
			shot(context, sp, "showcase_empty_left", 5, 3, 2, 0, 25);
			shot(context, sp, "showcase_empty_right", 15.5, 3, 2, 0, 25);
			shot(context, sp, "showcase_filled_left", 5, 3, 6, 0, 25);
			shot(context, sp, "showcase_filled_right", 15.5, 3, 6, 0, 25);
			shot(context, sp, "showcase_twotone_left", 5, 3, 10, 0, 25);
			shot(context, sp, "showcase_twotone_right", 17, 3, 10, 0, 25);
			shot(context, sp, "showcase_panel_wall_outside", 2.5, 2.5, 25, 180, 10);
			shot(context, sp, "showcase_panel_wall_inside", 2.5, 2.5, 15.5, 0, 10);

			shot(context, sp, "showcase_directional_corners", 30, 3, 2, 0, 25);
			shot(context, sp, "showcase_room_corner_outside", 32.5, 3, 28.5, 135, 20);
			shot(context, sp, "showcase_room_corner_inside", 25, 2.5, 21, -45, 15);

			sp.getServer().runCommand("time set midnight");
			context.waitTicks(20);
			shot(context, sp, "showcase_lights_night", 12.5, 3, 16, 0, 30);
		}
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double up, double z, float yaw, float pitch) {
		sp.getServer().runCommand("tp @a %s %s %s %s %s".formatted(x, Y + up, z, yaw, pitch));
		context.waitTicks(10);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot(name);
	}
}
