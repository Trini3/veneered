package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Shared right-click behaviour for every frame block. */
public final class VeneerLogic {
	private VeneerLogic() {
	}

	/** Can this block be used as a frame's appearance? */
	public static boolean isValidAppearance(Block block, Level level, BlockPos pos) {
		if (block.defaultBlockState().isAir() || block instanceof EntityBlock || block instanceof VeneerHolder) {
			return false;
		}

		BlockState state = block.defaultBlockState();
		return state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(level, pos);
	}

	/**
	 * Right-click with an item. If the item is a valid block, it is put into the slot the player is pointing at.
	 * Otherwise the click falls through to the block's normal behaviour (opening doors, etc).
	 */
	public static InteractionResult useItemOn(VeneerHolder holder, ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof VeneerBlockEntity frame)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		Block handBlock = Block.byItem(stack.getItem());

		if (!isValidAppearance(handBlock, level, pos)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		int slot = holder.slotForHit(state, pos, hit);
		@Nullable BlockState current = frame.get(slot);
		BlockState appearance = placementState(handBlock, player, hand, stack, hit);

		if (current == appearance) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		if (!level.isClientSide()) {
			boolean creative = player.getAbilities().instabuild;

			// Re-orienting the same block (e.g. turning a log sideways) doesn't use up another item.
			boolean sameBlock = current != null && current.is(handBlock);

			if (!creative && !sameBlock) {
				stack.shrink(1);

				if (current != null) {
					player.getInventory().placeItemBackInInventory(new ItemStack(current.getBlock()), Prediction.SERVER_ONLY);
				}
			}

			frame.set(slot, appearance);
			holder.onAppearanceChanged(level, pos, state, slot);
			level.playSound(null, pos, appearance.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * Sneak + empty hand takes the appearance out of the slot the player is pointing at.
	 * Returns null when the click should fall through to the block's normal behaviour.
	 */
	public static @Nullable InteractionResult useWithoutItem(VeneerHolder holder, BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!player.isSecondaryUseActive() || !(level.getBlockEntity(pos) instanceof VeneerBlockEntity frame)) {
			return null;
		}

		int slot = holder.slotForHit(state, pos, hit);
		@Nullable BlockState current = frame.get(slot);

		if (current == null) {
			// Fall back to whichever slot is filled, so a single slab/panel can always be emptied.
			slot = 1 - slot;
			current = frame.get(slot);
		}

		if (current == null) {
			return null;
		}

		if (!level.isClientSide()) {
			if (!player.getAbilities().instabuild) {
				player.getInventory().placeItemBackInInventory(new ItemStack(current.getBlock()), Prediction.SERVER_ONLY);
			}

			frame.set(slot, null);
			holder.onAppearanceChanged(level, pos, state, slot);
			level.playSound(null, pos, current.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
		}

		return InteractionResult.SUCCESS;
	}

	/** Hit position relative to the block, nudged slightly inside along the clicked face. */
	public static double[] localHit(BlockPos pos, BlockHitResult hit) {
		var loc = hit.getLocation();
		var dir = hit.getDirection();
		return new double[] {
				loc.x - pos.getX() - dir.getStepX() * 0.01,
				loc.y - pos.getY() - dir.getStepY() * 0.01,
				loc.z - pos.getZ() - dir.getStepZ() * 0.01
		};
	}

	/**
	 * Destroy progress per tick for a frame: if it holds anything, it mines exactly like the hardest held block
	 * (hardness, tool speed, and the slow-down for lacking the correct tool). An empty frame mines like the frame.
	 */
	public static float destroyProgress(Player player, BlockGetter level, BlockPos pos, float frameProgress) {
		if (level.getBlockEntity(pos) instanceof VeneerBlockEntity frame) {
			BlockState hardest = frame.hardest(level, pos);

			if (hardest != null) {
				return hardest.getDestroyProgress(player, level, pos);
			}
		}

		return frameProgress;
	}

	/**
	 * The state the held block would get if the player placed it against the face they clicked, so directional
	 * blocks keep their orientation: logs follow the clicked face, furnaces and pumpkins face the player, and so on.
	 */
	public static BlockState placementState(Block block, Player player, InteractionHand hand, ItemStack stack, BlockHitResult hit) {
		BlockState placed = null;

		try {
			placed = block.getStateForPlacement(new BlockPlaceContext(player, hand, stack, hit));
		} catch (RuntimeException e) {
			// Some modded blocks assume a real placement; fall back to their default state.
		}

		return placed != null && placed.is(block) ? placed : block.defaultBlockState();
	}
}
