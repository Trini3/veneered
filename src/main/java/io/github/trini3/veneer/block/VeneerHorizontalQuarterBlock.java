package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Half a slab: a strip along one edge (FACING), at the bottom or top. Placing a second one into the empty half
 * makes a half-and-half slab split down the middle; slot 0 is the original strip, slot 1 the added one.
 */
public class VeneerHorizontalQuarterBlock extends VeneerShapeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty DOUBLE = BooleanProperty.create("double");

	public VeneerHorizontalQuarterBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(FACING, Direction.NORTH).setValue(TOP, false).setValue(DOUBLE, false);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TOP, DOUBLE);
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		double y0 = state.getValue(TOP) ? 8 : 0;

		if (state.getValue(DOUBLE)) {
			return Block.box(0, y0, 0, 16, y0 + 8, 16);
		}

		return box(state.getValue(FACING), 0, y0, 0, 16, y0 + 8, 8);
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		BlockState existing = context.getLevel().getBlockState(pos);

		if (existing.is(this)) {
			return existing.setValue(DOUBLE, true);
		}

		Direction clicked = context.getClickedFace();
		Direction facing = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
		boolean top = clicked == Direction.DOWN || (clicked != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5);
		return defaultBlockState().setValue(FACING, facing).setValue(TOP, top);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if (state.getValue(DOUBLE) || !context.getItemInHand().is(asItem())) {
			return false;
		}

		if (!context.replacingClickedOnBlock()) {
			return true;
		}

		Direction facing = state.getValue(FACING);

		if (context.getClickedFace() == facing.getOpposite()) {
			return true;
		}

		var loc = context.getClickLocation();
		BlockPos pos = context.getClickedPos();
		return context.getClickedFace().getAxis() != facing.getAxis() && depthToward(facing, loc.x - pos.getX(), loc.z - pos.getZ()) < 0.5;
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		return !state.getValue(DOUBLE) || depthToward(state.getValue(FACING), x, z) > 0.5 ? 0 : 1;
	}
}
