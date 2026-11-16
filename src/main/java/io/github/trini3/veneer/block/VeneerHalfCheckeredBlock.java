package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A full frame whose FACING half is checkered and whose other half is solid (slot 0). FACING is the face that was
 * clicked when placing, so the checkered side points away from the block it was placed against: up on a floor,
 * down on a ceiling, outward on a wall.
 */
public class VeneerHalfCheckeredBlock extends VeneerShapeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

	public VeneerHalfCheckeredBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(FACING, Direction.UP);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		return Shapes.block();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		return Checker.inHalf(state.getValue(FACING), x, y, z) ? Checker.slot(x, y, z) : 0;
	}
}
