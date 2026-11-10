package io.github.trini3.veneer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Implemented by every frame block. Frames have up to two appearance slots; blocks that can be split into two halves
 * (slabs and panels) decide which slot a click or a rendered face belongs to.
 */
public interface VeneerHolder {
	/** Which appearance slot the player is pointing at. */
	default int slotForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
		return 0;
	}

	/**
	 * Which appearance slot a rendered face belongs to, given the face's center (in block-local 0..1 coordinates,
	 * nudged slightly inside the block).
	 */
	default int slotForPoint(BlockState state, float x, float y, float z) {
		return 0;
	}

	/** Called on the server after an appearance slot changes, so linked blocks (door halves) can follow. */
	default void onAppearanceChanged(Level level, BlockPos pos, BlockState state, int slot) {
	}
}
