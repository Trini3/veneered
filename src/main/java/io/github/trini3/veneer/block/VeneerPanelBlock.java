package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A vertical half-block (a "vertical slab"). FACING is the side of the block the panel sits against.
 * Placing a second panel into the empty half makes a double panel whose two halves keep separate appearances,
 * which gives walls with a different look on the inside and the outside.
 * Slot 0 is the original (FACING-side) half, slot 1 the half added second.
 */
public class VeneerPanelBlock extends Block implements EntityBlock, VeneerHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty DOUBLE = BooleanProperty.create("double");

	private static final VoxelShape NORTH = Block.box(0, 0, 0, 16, 16, 8);
	private static final VoxelShape SOUTH = Block.box(0, 0, 8, 16, 16, 16);
	private static final VoxelShape WEST = Block.box(0, 0, 0, 8, 16, 16);
	private static final VoxelShape EAST = Block.box(8, 0, 0, 16, 16, 16);

	public VeneerPanelBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(VeneerProps.defaults(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOUBLE, false)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, DOUBLE, VeneerProps.LIGHT, VeneerProps.OPAQUE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(DOUBLE)) {
			return Shapes.block();
		}

		return switch (state.getValue(FACING)) {
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			case EAST -> EAST;
			default -> NORTH;
		};
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return !state.getValue(DOUBLE);
	}

	/** How far toward the FACING side a local point is: 1 = right against the FACING side, 0 = the opposite side. */
	private static double depthTowardFacing(Direction facing, double x, double z) {
		return switch (facing) {
			case NORTH -> 1 - z;
			case SOUTH -> z;
			case WEST -> 1 - x;
			default -> x;
		};
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
		return defaultBlockState().setValue(FACING, facing);
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

		// Clicking the top, bottom or a side edge: only fill in if the click lands on the empty half.
		var loc = context.getClickLocation();
		BlockPos pos = context.getClickedPos();
		double depth = depthTowardFacing(facing, loc.x - pos.getX(), loc.z - pos.getZ());
		return context.getClickedFace().getAxis() != facing.getAxis() && depth < 0.5;
	}

	@Override
	public int slotForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
		if (!state.getValue(DOUBLE)) {
			return 0;
		}

		double[] local = VeneerLogic.localHit(pos, hit);
		return depthTowardFacing(state.getValue(FACING), local[0], local[2]) > 0.5 ? 0 : 1;
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		if (!state.getValue(DOUBLE)) {
			return 0;
		}

		return depthTowardFacing(state.getValue(FACING), x, z) > 0.5 ? 0 : 1;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VeneerBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = VeneerLogic.useItemOn(this, stack, state, level, pos, player, hand, hit);
		return result == InteractionResult.TRY_WITH_EMPTY_HAND ? super.useItemOn(stack, state, level, pos, player, hand, hit) : result;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		InteractionResult result = VeneerLogic.useWithoutItem(this, state, level, pos, player, hit);
		return result != null ? result : super.useWithoutItem(state, level, pos, player, hit);
	}

	/** Mines like the hardest block inside the frame: same hardness, same preferred tool, same correct-tool rule. */
	@Override
	protected float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		return VeneerLogic.destroyProgress(player, level, pos, super.getDestroyProgress(state, player, level, pos));
	}

	/** Only hides neighbouring faces and blocks light when the held block would. */
	@Override
	protected net.minecraft.world.phys.shapes.VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(VeneerProps.OPAQUE) ? super.getOcclusionShape(state) : net.minecraft.world.phys.shapes.Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return !state.getValue(VeneerProps.OPAQUE) || super.propagatesSkylightDown(state);
	}
}
