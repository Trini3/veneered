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
 * A block (or slab) split into a 3/4 L-shape (slot 0) and a 1/4 corner column (slot 1), for corners where two
 * walls of different materials meet. FACING picks the quarter: north = north-east, east = south-east,
 * south = south-west, west = north-west. When placed, the small quarter points toward the player, so standing
 * inside a room puts the quarter on the inside corner.
 */
public class VeneerCornerBlock extends Block implements EntityBlock, VeneerHolder {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	/** Only used by the slab variant: whether it sits in the top half. */
	public static final BooleanProperty TOP = BooleanProperty.create("top");

	private static final VoxelShape BOTTOM_SHAPE = Block.box(0, 0, 0, 16, 8, 16);
	private static final VoxelShape TOP_SHAPE = Block.box(0, 8, 0, 16, 16, 16);

	private final boolean slab;

	public VeneerCornerBlock(BlockBehaviour.Properties properties, boolean slab) {
		super(properties);
		this.slab = slab;
		BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);

		if (state.hasProperty(TOP)) {
			state = state.setValue(TOP, false);
		}

		registerDefaultState(VeneerProps.defaults(state));
	}

	public static VeneerCornerBlock block(BlockBehaviour.Properties properties) {
		return new VeneerCornerBlock(properties, false);
	}

	public static VeneerCornerBlock slab(BlockBehaviour.Properties properties) {
		return new VeneerCornerBlock(properties, true) {
			@Override
			protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
				super.createBlockStateDefinition(builder);
				builder.add(TOP);
			}
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, VeneerProps.LIGHT, VeneerProps.OPAQUE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!slab) {
			return Shapes.block();
		}

		return state.getValue(TOP) ? TOP_SHAPE : BOTTOM_SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return slab;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(VeneerProps.OPAQUE) ? super.getOcclusionShape(state) : Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return !state.getValue(VeneerProps.OPAQUE) || super.propagatesSkylightDown(state);
	}

	/** The facing whose quarter contains the given local point. */
	private static Direction quarterAt(double x, double z) {
		if (x >= 0.5) {
			return z < 0.5 ? Direction.NORTH : Direction.EAST;
		}

		return z >= 0.5 ? Direction.SOUTH : Direction.WEST;
	}

	@Override
	public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Direction facing = context.getHorizontalDirection().getOpposite();

		if (context.getPlayer() != null) {
			// Point the small quarter at the player.
			var eye = context.getPlayer().position();
			facing = quarterAt(eye.x - pos.getX(), eye.z - pos.getZ());
		}

		BlockState state = defaultBlockState().setValue(FACING, facing);

		if (slab) {
			Direction clicked = context.getClickedFace();
			boolean top = clicked == Direction.DOWN || (clicked != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5);
			state = state.setValue(TOP, top);
		}

		return state;
	}

	@Override
	public int slotForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
		double[] local = VeneerLogic.localHit(pos, hit);
		return slotForPoint(state, (float) local[0], (float) local[1], (float) local[2]);
	}

	@Override
	public int slotForPoint(BlockState state, float x, float y, float z) {
		return quarterAt(x, z) == state.getValue(FACING) ? 1 : 0;
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

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		return VeneerLogic.destroyProgress(player, level, pos, super.getDestroyProgress(state, player, level, pos));
	}
}
