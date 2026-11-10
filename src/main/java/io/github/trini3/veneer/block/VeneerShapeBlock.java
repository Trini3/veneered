package io.github.trini3.veneer.block;

import java.util.IdentityHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Base for the mod's own small frame shapes (quarters, half stairs...). Handles the frame plumbing (block entity,
 * right-click, mining speed, light and occlusion) so subclasses only describe their shape and placement.
 * Shapes are computed once per block state.
 */
public abstract class VeneerShapeBlock extends Block implements EntityBlock, VeneerHolder {
	private final Map<BlockState, VoxelShape> shapes = new IdentityHashMap<>();

	protected VeneerShapeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(VeneerProps.defaults(initialState(stateDefinition.any())));

		for (BlockState state : stateDefinition.getPossibleStates()) {
			shapes.put(state, computeShape(state));
		}
	}

	/** Default values for the subclass's own properties. */
	protected abstract BlockState initialState(BlockState any);

	protected abstract void addProperties(StateDefinition.Builder<Block, BlockState> builder);

	protected abstract VoxelShape computeShape(BlockState state);

	@Override
	protected final void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		addProperties(builder);
		builder.add(VeneerProps.LIGHT, VeneerProps.OPAQUE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.get(state);
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return state.getValue(VeneerProps.OPAQUE) ? shapes.get(state) : Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return !state.getValue(VeneerProps.OPAQUE) || super.propagatesSkylightDown(state);
	}

	@Override
	public int slotForHit(BlockState state, BlockPos pos, BlockHitResult hit) {
		double[] local = VeneerLogic.localHit(pos, hit);
		return slotForPoint(state, (float) local[0], (float) local[1], (float) local[2]);
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

	// ---- geometry helpers ----

	/**
	 * A box given in pixels for a shape facing north, rotated to face {@code facing} (same rotation as the
	 * blockstate's y rotation: north 0, east 90, south 180, west 270).
	 */
	protected static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
		double ax = x0, az = z0, bx = x1, bz = z1;

		switch (facing) {
			case EAST -> { ax = 16 - z0; bx = 16 - z1; az = x0; bz = x1; }
			case SOUTH -> { ax = 16 - x0; bx = 16 - x1; az = 16 - z0; bz = 16 - z1; }
			case WEST -> { ax = z0; bx = z1; az = 16 - x0; bz = 16 - x1; }
			default -> { }
		}

		return Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
	}

	/** How far toward {@code side} a local point is: 1 = right against that side, 0 = the opposite side. */
	protected static double depthToward(Direction side, double x, double z) {
		return switch (side) {
			case NORTH -> 1 - z;
			case SOUTH -> z;
			case WEST -> 1 - x;
			case EAST -> x;
			default -> 0.5;
		};
	}

	/** The facing (corner convention: north = NE, east = SE, south = SW, west = NW) whose quarter holds a point. */
	protected static Direction quarterAt(double x, double z) {
		if (x >= 0.5) {
			return z < 0.5 ? Direction.NORTH : Direction.EAST;
		}

		return z >= 0.5 ? Direction.SOUTH : Direction.WEST;
	}
}
