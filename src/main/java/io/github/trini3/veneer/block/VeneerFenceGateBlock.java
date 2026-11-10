package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public class VeneerFenceGateBlock extends FenceGateBlock implements EntityBlock, VeneerHolder {
	public VeneerFenceGateBlock(WoodType type, BlockBehaviour.Properties properties) {
		super(type, properties);
		registerDefaultState(VeneerProps.defaults(defaultBlockState()));
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VeneerBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		InteractionResult result = VeneerLogic.useItemOn(this, stack, state, level, pos, player, hit);
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

	@Override
	protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(VeneerProps.LIGHT);
	}
}
