package io.github.trini3.veneer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import net.fabricmc.fabric.api.blockgetter.v2.FabricBlockGetter;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;

import io.github.trini3.veneer.block.Appearance;
import io.github.trini3.veneer.block.VeneerHolder;

/**
 * Renders a frame. The frame's own model supplies the shape (stairs, door, slab...). When the frame holds an
 * appearance, every face of that shape is repainted with the matching face of the held block: same texture,
 * same tint, same render layer, with UVs projected from the face's position so textures line up across blocks.
 */
public final class VeneerBlockStateModel implements BlockStateModel {
	private static final float INSET = 0.001F;

	private final BlockStateModel frame;
	private final VeneerHolder holder;
	private volatile @Nullable Mesh frameMesh;

	public VeneerBlockStateModel(BlockStateModel frame, VeneerHolder holder) {
		this.frame = frame;
		this.holder = holder;
	}

	private static @Nullable Appearance appearance(BlockAndTintGetter level, BlockPos pos) {
		return ((FabricBlockGetter) level).getBlockEntityRenderData(pos) instanceof Appearance appearance ? appearance : null;
	}

	private static BlockStateModel modelOf(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	/** The frame's geometry never changes for a given block state, so capture it once with no culling applied. */
	private Mesh frameMesh(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		Mesh mesh = frameMesh;

		if (mesh == null) {
			MutableMesh mutable = Renderer.get().mutableMesh();
			frame.emitQuads(mutable.emitter(), level, pos, state, random, face -> false);
			mesh = mutable.immutableCopy();
			frameMesh = mesh;
		}

		return mesh;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest) {
		Appearance appearance = appearance(level, pos);

		if (appearance == null) {
			frame.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}

		// Collect the parts of each held block's model once per call.
		@SuppressWarnings("unchecked")
		List<BlockStateModelPart>[] parts = new List[2];

		for (int slot = 0; slot < 2; slot++) {
			BlockState held = appearance.get(slot);

			if (held != null) {
				parts[slot] = new ArrayList<>();
				modelOf(held).collectParts(random, parts[slot]);
			}
		}

		frameMesh(level, pos, state, random).forEach(quad -> {
			Direction cull = quad.cullFace();

			if (cull != null && cullTest.test(cull)) {
				return;
			}

			Direction face = quad.lightFace();
			int slot = holder.slotForPoint(state,
					centerX(quad) - face.getStepX() * 0.01F,
					centerY(quad) - face.getStepY() * 0.01F,
					centerZ(quad) - face.getStepZ() * 0.01F);
			BlockState held = appearance.get(slot);

			if (held == null) {
				// This half is still empty: show the bare frame.
				emitter.copyFrom(quad);
				emitter.emit();
				return;
			}

			boolean emitted = false;

			for (BlockStateModelPart part : parts[slot]) {
				for (BakedQuad heldQuad : part.getQuads(face)) {
					emitRepainted(emitter, quad, face, heldQuad, held, level, pos);
					emitted = true;
				}

				for (BakedQuad heldQuad : part.getQuads(null)) {
					if (heldQuad.direction() == face) {
						emitRepainted(emitter, quad, face, heldQuad, held, level, pos);
						emitted = true;
					}
				}
			}

			if (!emitted) {
				emitter.copyFrom(quad);
				emitter.emit();
			}
		});
	}

	private static void emitRepainted(QuadEmitter emitter, QuadView shape, Direction face, BakedQuad source, BlockState held, BlockAndTintGetter level, BlockPos pos) {
		// Start from the held block's quad to inherit its material (render layer, atlas, emissive, shading)...
		emitter.fromBakedQuad(source);

		// ...then take the frame's geometry.
		for (int i = 0; i < 4; i++) {
			emitter.pos(i, shape.x(i), shape.y(i), shape.z(i));

			if (shape.hasNormal(i)) {
				emitter.normal(i, shape.normalX(i), shape.normalY(i), shape.normalZ(i));
			}
		}

		emitter.cullFace(shape.cullFace());
		emitter.nominalFace(shape.nominalFace());

		// Project UVs from the face position (like vanilla's default face UVs), so textures tile seamlessly.
		float[] us = new float[4];
		float[] vs = new float[4];
		float minU = Float.MAX_VALUE;
		float minV = Float.MAX_VALUE;

		for (int i = 0; i < 4; i++) {
			float x = shape.x(i);
			float y = shape.y(i);
			float z = shape.z(i);

			switch (face) {
				case DOWN -> { us[i] = x; vs[i] = 1 - z; }
				case UP -> { us[i] = x; vs[i] = z; }
				case NORTH -> { us[i] = 1 - x; vs[i] = 1 - y; }
				case SOUTH -> { us[i] = x; vs[i] = 1 - y; }
				case WEST -> { us[i] = z; vs[i] = 1 - y; }
				case EAST -> { us[i] = 1 - z; vs[i] = 1 - y; }
			}

			minU = Math.min(minU, us[i]);
			minV = Math.min(minV, vs[i]);
		}

		// Shift faces that sit outside the block (e.g. lowered fence gates) back into the 0..1 texture range.
		float shiftU = Mth.floor(minU + INSET);
		float shiftV = Mth.floor(minV + INSET);
		TextureAtlasSprite sprite = source.materialInfo().sprite();

		for (int i = 0; i < 4; i++) {
			float u = Mth.clamp(us[i] - shiftU, 0, 1);
			float v = Mth.clamp(vs[i] - shiftV, 0, 1);
			emitter.uv(i, Mth.lerp(u, sprite.getU0(), sprite.getU1()), Mth.lerp(v, sprite.getV0(), sprite.getV1()));
		}

		// The renderer would tint using the frame's block colors, so apply the held block's tint (grass, leaves) here.
		int tintIndex = source.materialInfo().tintIndex();

		if (tintIndex >= 0) {
			emitter.multiplyColor(tint(held, level, pos, tintIndex));
			emitter.tintIndex(-1);
		}

		emitter.emit();
	}

	private static int tint(BlockState held, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
		List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(held);

		if (tintIndex >= sources.size()) {
			return -1;
		}

		BlockTintSource source = sources.get(tintIndex);
		return source == null ? -1 : 0xFF000000 | source.colorInWorld(held, level, pos);
	}

	private static float centerX(QuadView quad) {
		return (quad.x(0) + quad.x(1) + quad.x(2) + quad.x(3)) * 0.25F;
	}

	private static float centerY(QuadView quad) {
		return (quad.y(0) + quad.y(1) + quad.y(2) + quad.y(3)) * 0.25F;
	}

	private static float centerZ(QuadView quad) {
		return (quad.z(0) + quad.z(1) + quad.z(2) + quad.z(3)) * 0.25F;
	}

	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		Appearance appearance = appearance(level, pos);

		if (appearance != null) {
			BlockState held = appearance.primary() != null ? appearance.primary() : appearance.secondary();

			if (held != null) {
				return modelOf(held).particleMaterial(level, pos, held);
			}
		}

		return frame.particleMaterial(level, pos, state);
	}

	@Override
	@BakedQuad.MaterialFlags
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		@BakedQuad.MaterialFlags int flags = frame.materialFlags(level, pos, state, random);
		Appearance appearance = appearance(level, pos);

		if (appearance != null) {
			for (int slot = 0; slot < 2; slot++) {
				BlockState held = appearance.get(slot);

				if (held != null) {
					flags |= modelOf(held).materialFlags(level, pos, held, random);
				}
			}
		}

		return flags;
	}

	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
		// Used where the renderer API isn't (block breaking overlay, moving pistons): show the bare frame shape.
		frame.collectParts(random, parts);
	}

	@Override
	public Material.Baked particleMaterial() {
		return frame.particleMaterial();
	}

	@Override
	@BakedQuad.MaterialFlags
	public int materialFlags() {
		// A frame can hold anything, including translucent and animated blocks.
		return frame.materialFlags() | BakedQuad.FLAG_ANIMATED | BakedQuad.FLAG_TRANSLUCENT;
	}
}
