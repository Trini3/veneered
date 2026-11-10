package io.github.trini3.veneer.gametest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;

import io.github.trini3.veneer.Veneer;
import io.github.trini3.veneer.VeneerRegistry;
import io.github.trini3.veneer.block.VeneerBlockEntity;
import io.github.trini3.veneer.block.VeneerPanelBlock;

/**
 * Performance check, only run when the VENEER_STRESS environment variable is set. Builds a large region of filled
 * frames, measures it, swaps every frame for the vanilla block that looks the same, measures again, then clears the
 * region for an empty baseline. Results go to the log and to veneer-stress.txt in the run directory.
 */
public class VeneerStressGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("veneer-stress");
	public static final boolean ENABLED = System.getenv("VENEER_STRESS") != null;

	private static final int SIZE_X = 80;
	private static final int SIZE_Y = 20;
	private static final int SIZE_Z = 80;
	private static final int Y0 = -60;
	private static final String VIEW = "tp @a 40 -30 -8 0 35";
	private static final String AWAY = "tp @a 30000 -30 30000 0 30";

	private static final Block[] CONTENTS = {Blocks.STONE, Blocks.OAK_PLANKS, Blocks.BRICKS, Blocks.GRASS_BLOCK, Blocks.GLASS, Blocks.OAK_LOG};
	private static final Block[] SLABS = {Blocks.STONE_SLAB, Blocks.OAK_SLAB, Blocks.BRICK_SLAB, Blocks.COBBLESTONE_SLAB, Blocks.STONE_BRICK_SLAB, Blocks.SPRUCE_SLAB};
	private static final Block[] STAIRS = {Blocks.STONE_STAIRS, Blocks.OAK_STAIRS, Blocks.BRICK_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_STAIRS};
	private static final Block[] WALLS = {Blocks.COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL, Blocks.BRICK_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL, Blocks.COBBLESTONE_WALL};
	private static final Block[] FENCES = {Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE, Blocks.OAK_FENCE, Blocks.SPRUCE_FENCE};

	private record Placement(BlockPos pos, BlockState frame, BlockState content, BlockState vanilla) { }

	private final List<Placement> build = new ArrayList<>();
	private final StringBuilder report = new StringBuilder();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ENABLED) {
			return;
		}

		plan();
		line("Veneer stress test: %d frames in an %dx%dx%d region (random 50%% fill, worst case for face culling)", build.size(), SIZE_X, SIZE_Y, SIZE_Z);
		blockStateCounts();

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");
			context.getInput().resizeWindow(1280, 720);
			sp.getServer().runCommand(VIEW);
			sp.getConnection().waitForChunksRender(6000);

			// Frames.
			long placeStart = System.nanoTime();
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				for (Placement p : build) {
					level.setBlock(p.pos(), p.frame(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}

				for (Placement p : build) {
					if (level.getBlockEntity(p.pos()) instanceof VeneerBlockEntity be) {
						be.set(0, p.content());
					}
				}
			});
			line("Server: placing and filling all frames took %.0f ms", ms(placeStart));
			sp.getConnection().waitForChunksRender(6000);
			arrive(context, sp, "warm-up");
			measure(context, sp, "Frames");
			saveSize(sp);

			// The same build in vanilla blocks.
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				for (Placement p : build) {
					level.setBlock(p.pos(), p.vanilla(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			});
			// Breaking a frame drops what it holds; clear those items so they don't skew the numbers.
			sp.getServer().runCommand("kill @e[type=item]");
			sp.getConnection().waitForChunksRender(6000);
			arrive(context, sp, "warm-up");
			measure(context, sp, "Vanilla");

			// Empty region.
			sp.getServer().runOnServer(server -> {
				ServerLevel level = sp.getConnection().getServerLevel();

				for (Placement p : build) {
					level.setBlock(p.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			});
			sp.getConnection().waitForChunksRender(6000);
			arrive(context, sp, "warm-up");
			measure(context, sp, "Empty");
		}

		try {
			Files.writeString(Path.of("veneer-stress.txt"), report.toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void plan() {
		Random random = new Random(1234);
		Direction[] horizontal = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

		for (int x = 0; x < SIZE_X; x++) {
			for (int y = 0; y < SIZE_Y; y++) {
				for (int z = 0; z < SIZE_Z; z++) {
					if (random.nextBoolean()) {
						continue;
					}

					int c = random.nextInt(CONTENTS.length);
					BlockState content = CONTENTS[c].defaultBlockState();

					if (content.hasProperty(RotatedPillarBlock.AXIS)) {
						content = content.setValue(RotatedPillarBlock.AXIS, Direction.Axis.values()[random.nextInt(3)]);
					}

					Direction facing = horizontal[random.nextInt(4)];
					boolean top = random.nextBoolean();
					int shape = random.nextInt(20);
					BlockState frame;
					BlockState vanilla;

					if (shape < 8) {
						frame = VeneerRegistry.FRAME_BLOCK.defaultBlockState();
						vanilla = content;
					} else if (shape < 11) {
						SlabType type = top ? SlabType.TOP : SlabType.BOTTOM;
						frame = VeneerRegistry.FRAME_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type);
						vanilla = SLABS[c].defaultBlockState().setValue(SlabBlock.TYPE, type);
					} else if (shape < 14) {
						Half half = top ? Half.TOP : Half.BOTTOM;
						frame = VeneerRegistry.FRAME_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
						vanilla = STAIRS[c].defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
					} else if (shape < 16) {
						// No vanilla vertical slab; compare against a slab, which has the same face count.
						frame = VeneerRegistry.FRAME_PANEL.defaultBlockState().setValue(VeneerPanelBlock.FACING, facing);
						vanilla = SLABS[c].defaultBlockState();
					} else if (shape < 18) {
						frame = VeneerRegistry.FRAME_WALL.defaultBlockState();
						vanilla = WALLS[c].defaultBlockState();
					} else {
						frame = VeneerRegistry.FRAME_FENCE.defaultBlockState();
						vanilla = FENCES[c].defaultBlockState();
					}

					build.add(new Placement(new BlockPos(x, Y0 + y, z), frame, content, vanilla));
				}
			}
		}
	}

	private void blockStateCounts() {
		int total = 0;
		int veneer = 0;

		for (Block block : BuiltInRegistries.BLOCK) {
			int states = block.getStateDefinition().getPossibleStates().size();
			total += states;

			if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Veneer.MOD_ID)) {
				veneer += states;
			}
		}

		line("Block states: %d added by Veneer out of %d total (%.1f%%)", veneer, total, 100.0 * veneer / total);
	}

	/** Teleports far away and back, timing how long the build takes to download, load and render. */
	private void arrive(ClientGameTestContext context, TestSingleplayerContext sp, String label) {
		sp.getServer().runCommand(AWAY);
		context.waitTicks(5);
		sp.getConnection().waitForChunksRender(6000);
		context.waitTicks(40);
		long start = System.nanoTime();
		sp.getServer().runCommand(VIEW);
		context.waitTicks(1);
		int ticks = sp.getConnection().waitForChunksRender(6000);
		double ms = ms(start);

		if (!label.equals("warm-up")) {
			line("  %s: arriving at the build, all chunks loaded and rendered in %.0f ms (%d frames)", label, ms, ticks);
		}
	}

	private void measure(ClientGameTestContext context, TestSingleplayerContext sp, String label) {
		line("%s:", label);
		arrive(context, sp, label + " run 1");
		arrive(context, sp, label + " run 2");

		// Steady-state frame time while looking at the build.
		context.waitTicks(40);
		long start = System.nanoTime();
		int frames = 200;
		context.waitTicks(frames);
		line("  %s: %.2f ms per frame looking at the build (software renderer, compare relatively)", label, ms(start) / frames);

		// Meshing cost: the same per-block model work the chunk builder does, on one thread.
		context.runOnClient(mc -> mesh(mc, label));

		long mspt = sp.getServer().computeOnServer(server -> server.getAverageTickTimeNanos());
		line("  %s: server average tick %.2f ms", label, mspt / 1e6);

		line("  %s: heap in use %.0f MB (client + integrated server)", label, heapMb());
	}

	private void mesh(Minecraft mc, String label) {
		ClientLevel level = mc.level;
		MutableMesh mesh = Renderer.get().mutableMesh();
		RandomSource random = RandomSource.create();
		long best = Long.MAX_VALUE;
		int quads = 0;
		int blocks = 0;

		for (int round = 0; round < 5; round++) {
			quads = 0;
			blocks = 0;
			long start = System.nanoTime();

			for (BlockPos pos : BlockPos.betweenClosed(0, Y0, 0, SIZE_X - 1, Y0 + SIZE_Y - 1, SIZE_Z - 1)) {
				BlockState state = level.getBlockState(pos);

				if (state.isAir()) {
					continue;
				}

				BlockPos immutable = pos.immutable();
				BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
				random.setSeed(state.getSeed(immutable));
				model.emitQuads(mesh.emitter(), level, immutable, state, random,
						dir -> dir != null && !Block.shouldRenderFace(state, level.getBlockState(immutable.relative(dir)), dir));
				quads += mesh.size();
				blocks++;
				mesh.clear();
			}

			best = Math.min(best, System.nanoTime() - start);
		}

		if (blocks > 0) {
			line("  %s: meshing %d blocks took %.1f ms on one thread (%.2f us per block, %d quads); a full 16x16x16 section would take ~%.1f ms",
					label, blocks, best / 1e6, best / 1e3 / blocks, quads, best / 1e6 / blocks * 4096);
		}
	}

	private void saveSize(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			ServerLevel level = sp.getConnection().getServerLevel();
			ListTag disk = new ListTag();
			long rawDisk = 0;
			long rawSync = 0;
			int count = 0;

			for (Placement p : build) {
				if (level.getBlockEntity(p.pos()) instanceof VeneerBlockEntity be) {
					CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
					disk.add(saved);
					rawDisk += size(saved);
					rawSync += size(be.getUpdateTag(level.registryAccess()));
					count++;
				}
			}

			CompoundTag all = new CompoundTag();
			all.put("block_entities", disk);
			ByteArrayOutputStream compressed = new ByteArrayOutputStream();

			try {
				NbtIo.writeCompressed(all, compressed);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			line("  Frames: block entity data per frame: %.0f bytes saved (about %.1f bytes after the save file's compression), %.0f bytes sent to clients before compression",
					(double) rawDisk / count, (double) compressed.size() / count, (double) rawSync / count);
		});
	}

	private static int size(CompoundTag tag) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.write(tag, new DataOutputStream(bytes));
			return bytes.size();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static double heapMb() {
		Runtime runtime = Runtime.getRuntime();

		for (int i = 0; i < 3; i++) {
			System.gc();

			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		return (runtime.totalMemory() - runtime.freeMemory()) / 1048576.0;
	}

	private static double ms(long start) {
		return (System.nanoTime() - start) / 1e6;
	}

	private synchronized void line(String format, Object... args) {
		String text = String.format(Locale.ROOT, format, args);
		LOGGER.info(text);
		report.append(text).append('\n');
	}
}
