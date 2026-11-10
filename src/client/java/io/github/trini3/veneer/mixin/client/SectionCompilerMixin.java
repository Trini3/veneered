package io.github.trini3.veneer.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.github.trini3.veneer.block.VeneerBlockEntity;

/**
 * Frames are drawn entirely by the chunk mesh and have no block entity renderer, but vanilla lists every block entity
 * in a section for per-frame extraction anyway. Leaving frames off that list means a large build of frames costs
 * nothing per frame once meshed. Only Veneer's own block entities are affected.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
	@Inject(method = "handleBlockEntity", at = @At("HEAD"), cancellable = true)
	private void veneer$skipFrames(SectionCompiler.Results results, BlockEntity blockEntity, CallbackInfo ci) {
		if (blockEntity instanceof VeneerBlockEntity) {
			ci.cancel();
		}
	}
}
