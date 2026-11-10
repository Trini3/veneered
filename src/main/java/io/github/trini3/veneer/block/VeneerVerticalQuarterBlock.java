package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A full-height pillar a quarter of a block across. FACING names the corner it stands in (north = north-east,
 * east = south-east, south = south-west, west = north-west); it goes in the corner you click.
 */
public class VeneerVerticalQuarterBlock extends VeneerShapeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public VeneerVerticalQuarterBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(FACING, Direction.NORTH);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		return box(state.getValue(FACING), 8, 0, 0, 16, 16, 8);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		var loc = context.getClickLocation();
		double x = Mth.clamp(loc.x - pos.getX(), 0, 1);
		double z = Mth.clamp(loc.z - pos.getZ(), 0, 1);
		return defaultBlockState().setValue(FACING, quarterAt(x, z));
	}
}
