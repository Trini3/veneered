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
 * Stairs cut in half along their length. FACING is the side the step rises toward (like vanilla stairs), LEFT says
 * which half it keeps when looking in the FACING direction, TOP flips it upside down. Placing a second one into the
 * empty half makes half-and-half stairs; slot 0 is the original half, slot 1 the added one.
 */
public class VeneerHalfStairBlock extends VeneerShapeBlock {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LEFT = BooleanProperty.create("left");
	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty DOUBLE = BooleanProperty.create("double");

	public VeneerHalfStairBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected BlockState initialState(BlockState any) {
		return any.setValue(FACING, Direction.NORTH).setValue(LEFT, true).setValue(TOP, false).setValue(DOUBLE, false);
	}

	@Override
	protected void addProperties(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LEFT, TOP, DOUBLE);
	}

	/** The side the kept half sits on: counter-clockwise of FACING for the left half. */
	private static Direction side(BlockState state) {
		Direction facing = state.getValue(FACING);
		return state.getValue(LEFT) ? facing.getCounterClockWise() : facing.getClockWise();
	}

	@Override
	protected VoxelShape computeShape(BlockState state) {
		Direction facing = state.getValue(FACING);
		boolean top = state.getValue(TOP);
		double baseY0 = top ? 8 : 0;
		double stepY0 = top ? 0 : 8;
		VoxelShape shape = Shapes.empty();

		// Facing north, the left half is x 0..8 and the step is at z 0..8.
		for (boolean left : new boolean[] {true, false}) {
			if (!state.getValue(DOUBLE) && left != state.getValue(LEFT)) {
				continue;
			}

			double x0 = left ? 0 : 8;
			shape = Shapes.or(shape,
					box(facing, x0, baseY0, 0, x0 + 8, baseY0 + 8, 16),
					box(facing, x0, stepY0, 0, x0 + 8, stepY0 + 8, 8));
		}

		return shape.optimize();
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		BlockState existing = context.getLevel().getBlockState(pos);

		if (existing.is(this)) {
			return existing.setValue(DOUBLE, true);
		}

		Direction facing = context.getHorizontalDirection();
		Direction clicked = context.getClickedFace();
		var loc = context.getClickLocation();
		boolean top = clicked == Direction.DOWN || (clicked != Direction.UP && loc.y - pos.getY() > 0.5);
		boolean left = depthToward(facing.getCounterClockWise(), loc.x - pos.getX(), loc.z - pos.getZ()) > 0.5;
		return defaultBlockState().setValue(FACING, facing).setValue(TOP, top).setValue(LEFT, left);
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if (state.getValue(DOUBLE) || !context.getItemInHand().is(asItem())) {
			return false;
		}

		if (!context.replacingClickedOnBlock()) {
			return true;
		}

		Direction side = side(state);

		if (context.getClickedFace() == side.getOpposite()) {
			return true;
		}

		var loc = context.getClickLocation();
		BlockPos pos = context.getClickedPos();
		return context.getClickedFace().getAxis() != side.getAxis() && depthToward(side, loc.x - pos.getX(), loc.z - pos.getZ()) < 0.5;
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		return !state.getValue(DOUBLE) || depthToward(side(state), x, z) > 0.5 ? 0 : 1;
	}
}
