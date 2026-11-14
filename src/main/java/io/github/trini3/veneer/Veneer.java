package io.github.trini3.veneer;

import net.minecraft.resources.Identifier;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import io.github.trini3.veneer.block.VeneerBlockEntity;

public final class Veneer implements ModInitializer {
	public static final String MOD_ID = "veneer";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		VeneerRegistry.init();

		// Breaking a frame in creative removes it without dropping what it holds, like any block in creative.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player.isCreative() && blockEntity instanceof VeneerBlockEntity frame) {
				frame.skipDropsThisTick();

				// The lower half of a door is the one that drops, and it is removed along with the upper half.
				if (state.hasProperty(DoorBlock.HALF)) {
					var other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();

					if (level.getBlockEntity(other) instanceof VeneerBlockEntity otherHalf) {
						otherHalf.skipDropsThisTick();
					}
				}
			}

			return true;
		});
	}
}
