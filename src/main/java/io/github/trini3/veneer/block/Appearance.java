package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable snapshot of what a frame looks like, handed to the client renderer as block entity render data.
 * Slot 0 is the primary half (bottom slab, front panel, or the whole block); slot 1 is the second half.
 */
public record Appearance(@Nullable BlockState primary, @Nullable BlockState secondary) {
	public @Nullable BlockState get(int slot) {
		return slot == 0 ? primary : secondary;
	}
}
