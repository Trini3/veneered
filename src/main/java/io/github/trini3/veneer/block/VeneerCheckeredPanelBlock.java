package io.github.trini3.veneer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** A vertical slab split into a checkerboard of its two appearances. Two of them together make a checkered block. */
public class VeneerCheckeredPanelBlock extends VeneerPanelBlock {
	public VeneerCheckeredPanelBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public int slotForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
		double[] local = VeneerLogic.localHit(pos, hit);
		return Checker.slot(local[0], local[1], local[2]);
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		return Checker.slot(x, y, z);
	}
}
