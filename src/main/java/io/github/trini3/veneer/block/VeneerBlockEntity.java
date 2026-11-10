package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.blockgetter.v2.RenderDataBlockEntity;

import io.github.trini3.veneer.VeneerRegistry;

public class VeneerBlockEntity extends BlockEntity implements RenderDataBlockEntity {
	private final @Nullable BlockState[] slots = new BlockState[2];
	private @Nullable Appearance renderData = null;

	public VeneerBlockEntity(BlockPos pos, BlockState state) {
		super(VeneerRegistry.VENEER_BLOCK_ENTITY, pos, state);
	}

	public @Nullable BlockState get(int slot) {
		return slots[slot];
	}

	/** The held block that takes longest to mine (unbreakable blocks count as hardest), or null when empty. */
	public @Nullable BlockState hardest(BlockGetter level, BlockPos pos) {
		BlockState best = null;
		float bestSpeed = 0;

		for (BlockState slot : slots) {
			if (slot == null) {
				continue;
			}

			float speed = slot.getDestroySpeed(level, pos);

			if (speed < 0) {
				speed = Float.MAX_VALUE;
			}

			if (best == null || speed > bestSpeed) {
				best = slot;
				bestSpeed = speed;
			}
		}

		return best;
	}

	public boolean isEmpty() {
		return slots[0] == null && slots[1] == null;
	}

	public void set(int slot, @Nullable BlockState state) {
		if (state != null && state.isAir()) {
			state = null;
		}

		slots[slot] = state;
		updateRenderData();
		setChanged();
		syncStateProperties();
	}

	/** Pushes the held blocks' light and opacity into the frame's block state, where the lighting engine reads them. */
	private void syncStateProperties() {
		if (level == null || level.isClientSide()) {
			return;
		}

		BlockState current = getBlockState();
		BlockState updated = VeneerProps.apply(current, slots);

		if (updated != current) {
			level.setBlock(worldPosition, updated, Block.UPDATE_ALL);
		}
	}

	private void updateRenderData() {
		renderData = isEmpty() ? null : new Appearance(slots[0], slots[1]);
	}

	@Override
	public @Nullable Object getRenderData() {
		return renderData;
	}

	@Override
	protected void loadAdditional(ValueInput data) {
		super.loadAdditional(data);

		for (int i = 0; i < slots.length; i++) {
			BlockState state = data.read("slot" + i, BlockState.CODEC).orElse(null);
			slots[i] = state == null || state.isAir() ? null : state;
		}

		updateRenderData();

		if (level != null && level.isClientSide()) {
			// Forces the chunk section to re-mesh so the new appearance shows up.
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput data) {
		super.saveAdditional(data);

		for (int i = 0; i < slots.length; i++) {
			// Always write something, otherwise clearing a slot would never reach the client.
			data.store("slot" + i, BlockState.CODEC, slots[i] == null ? Blocks.AIR.defaultBlockState() : slots[i]);
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();

		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getChunkSource().blockChanged(worldPosition);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);

		// Both door halves store the appearance; only the lower half gives the blocks back.
		if (state.hasProperty(DoorBlock.HALF) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			return;
		}

		if (level != null && !level.isClientSide()) {
			for (BlockState slot : slots) {
				if (slot != null) {
					Block.popResource(level, pos, new ItemStack(slot.getBlock()));
				}
			}
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}
}
