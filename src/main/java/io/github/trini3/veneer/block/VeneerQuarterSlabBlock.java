package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A layer a quarter of a block tall, at the bottom or the top of the block space. */
public class VeneerQuarterSlabBlock extends VeneerShapeBlock {
	public static final BooleanProperty TOP = BooleanProperty.create("top");

	public VeneerQuarterSlabBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(TOP, false);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TOP);
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		return state.getValue(TOP) ? Block.box(0, 12, 0, 16, 16, 16) : Block.box(0, 0, 0, 16, 4, 16);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		boolean top = clicked == Direction.DOWN || (clicked != Direction.UP && context.getClickLocation().y - context.getClickedPos().getY() > 0.5);
		return defaultBlockState().setValue(TOP, top);
	}
}
