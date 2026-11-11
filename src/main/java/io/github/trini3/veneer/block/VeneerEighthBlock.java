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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An eighth of a block: a half-size cube in one corner. FACING names the corner as for the vertical quarter
 * (north = north-east, east = south-east, south = south-west, west = north-west) and TOP puts it in the upper half.
 * It goes in the corner of the block you click.
 */
public class VeneerEighthBlock extends VeneerShapeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty TOP = BooleanProperty.create("top");

	public VeneerEighthBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(FACING, Direction.NORTH).setValue(TOP, false);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TOP);
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		double y0 = state.getValue(TOP) ? 8 : 0;
		return box(state.getValue(FACING), 8, y0, 0, 16, y0 + 8, 8);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		var loc = context.getClickLocation();
		double x = Mth.clamp(loc.x - pos.getX(), 0, 1);
		double y = Mth.clamp(loc.y - pos.getY(), 0, 1);
		double z = Mth.clamp(loc.z - pos.getZ(), 0, 1);
		return defaultBlockState().setValue(FACING, quarterAt(x, z)).setValue(TOP, y > 0.5);
	}
}
