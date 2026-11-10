package io.github.trini3.veneer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import org.joml.Vector3fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
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

		// Tint colours (grass, leaves...) per slot and tint index, computed at most once per call.
		int[][] tints = new int[2][];

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
					emitRepainted(emitter, quad, face, heldQuad, held, level, pos, tints, slot);
					emitted = true;
				}

				for (BakedQuad heldQuad : part.getQuads(null)) {
					if (heldQuad.direction() == face) {
						emitRepainted(emitter, quad, face, heldQuad, held, level, pos, tints, slot);
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

	private static void emitRepainted(QuadEmitter emitter, QuadView shape, Direction face, BakedQuad source, BlockState held, BlockAndTintGetter level, BlockPos pos, int[][] tints, int slot) {
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

		// Project each vertex onto the face (like vanilla's default face UVs) so textures line up across blocks...
		float minU = Float.MAX_VALUE;
		float minV = Float.MAX_VALUE;

		for (int i = 0; i < 4; i++) {
			minU = Math.min(minU, projU(face, shape.x(i), shape.y(i), shape.z(i)));
			minV = Math.min(minV, projV(face, shape.x(i), shape.y(i), shape.z(i)));
		}

		// ...shifting faces that sit outside the block (e.g. lowered fence gates) back into the 0..1 range...
		float shiftU = Mth.floor(minU + INSET);
		float shiftV = Mth.floor(minV + INSET);

		// ...then map through the held quad's own projected-position -> texture mapping. This keeps the held
		// block's texture rotation and flips (a sideways log's bark runs sideways) and any sub-region it uses.
		Vector3fc p0 = source.position0();
		Vector3fc p1 = source.position1();
		Vector3fc p2 = source.position2();
		float a0 = projU(face, p0.x(), p0.y(), p0.z()), b0 = projV(face, p0.x(), p0.y(), p0.z());
		float da1 = projU(face, p1.x(), p1.y(), p1.z()) - a0, db1 = projV(face, p1.x(), p1.y(), p1.z()) - b0;
		float da2 = projU(face, p2.x(), p2.y(), p2.z()) - a0, db2 = projV(face, p2.x(), p2.y(), p2.z()) - b0;
		float det = da1 * db2 - da2 * db1;
		long t0 = source.packedUV0(), t1 = source.packedUV1(), t2 = source.packedUV2();
		float u0 = UVPair.unpackU(t0), v0 = UVPair.unpackV(t0);
		TextureAtlasSprite sprite = source.materialInfo().sprite();
		float minSpriteU = Math.min(sprite.getU0(), sprite.getU1()), maxSpriteU = Math.max(sprite.getU0(), sprite.getU1());
		float minSpriteV = Math.min(sprite.getV0(), sprite.getV1()), maxSpriteV = Math.max(sprite.getV0(), sprite.getV1());
		boolean affine = Math.abs(det) > 1e-6F;
		float du1 = UVPair.unpackU(t1) - u0, dv1 = UVPair.unpackV(t1) - v0;
		float du2 = UVPair.unpackU(t2) - u0, dv2 = UVPair.unpackV(t2) - v0;

		for (int i = 0; i < 4; i++) {
			float pu = Mth.clamp(projU(face, shape.x(i), shape.y(i), shape.z(i)) - shiftU, 0, 1);
			float pv = Mth.clamp(projV(face, shape.x(i), shape.y(i), shape.z(i)) - shiftV, 0, 1);
			float u;
			float v;

			if (affine) {
				// Solve (pu, pv) - P0 = s * (P1 - P0) + t * (P2 - P0), then apply the same s, t to the UVs.
				float da = pu - a0;
				float db = pv - b0;
				float s1 = (da * db2 - da2 * db) / det;
				float s2 = (da1 * db - da * db1) / det;
				u = u0 + s1 * du1 + s2 * du2;
				v = v0 + s1 * dv1 + s2 * dv2;
			} else {
				u = Mth.lerp(pu, sprite.getU0(), sprite.getU1());
				v = Mth.lerp(pv, sprite.getV0(), sprite.getV1());
			}

			emitter.uv(i, Mth.clamp(u, minSpriteU, maxSpriteU), Mth.clamp(v, minSpriteV, maxSpriteV));
		}

		// The renderer would tint using the frame's block colors, so apply the held block's tint (grass, leaves) here.
		int tintIndex = source.materialInfo().tintIndex();

		if (tintIndex >= 0) {
			if (tints[slot] == null || tintIndex >= tints[slot].length) {
				int[] grown = new int[Math.max(tintIndex + 1, 2)];
				java.util.Arrays.fill(grown, Integer.MIN_VALUE);

				if (tints[slot] != null) {
					System.arraycopy(tints[slot], 0, grown, 0, tints[slot].length);
				}

				tints[slot] = grown;
			}

			if (tints[slot][tintIndex] == Integer.MIN_VALUE) {
				tints[slot][tintIndex] = tint(held, level, pos, tintIndex);
			}

			emitter.multiplyColor(tints[slot][tintIndex]);
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

	private static float projU(Direction face, float x, float y, float z) {
		return switch (face) {
			case DOWN, UP, SOUTH -> x;
			case NORTH -> 1 - x;
			case WEST -> z;
			case EAST -> 1 - z;
		};
	}

	private static float projV(Direction face, float x, float y, float z) {
		return switch (face) {
			case DOWN -> 1 - z;
			case UP -> z;
			default -> 1 - y;
		};
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
