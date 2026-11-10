package io.github.trini3.veneer.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Block state properties that mirror the held block, because the lighting engine only looks at block states.
 * LIGHT is the light the held block gives off; OPAQUE says whether the frame blocks light and hides
 * neighbouring faces (true for an empty frame or for blocks like stone, false for glass, leaves, ice...).
 */
public final class VeneerProps {
	public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
	public static final BooleanProperty OPAQUE = BooleanProperty.create("opaque");

	private VeneerProps() {
	}

	/** Default values for a freshly placed, empty frame. */
	public static BlockState defaults(BlockState state) {
		if (state.hasProperty(LIGHT)) {
			state = state.setValue(LIGHT, 0);
		}

		if (state.hasProperty(OPAQUE)) {
			state = state.setValue(OPAQUE, true);
		}

		return state;
	}

	/** The frame state with LIGHT and OPAQUE matching what it holds. */
	public static BlockState apply(BlockState state, @Nullable BlockState[] slots) {
		int light = 0;
		boolean opaque = true;

		for (BlockState slot : slots) {
			if (slot != null) {
				light = Math.max(light, slot.getLightEmission());
				opaque &= slot.canOcclude();
			}
		}

		if (state.hasProperty(LIGHT)) {
			state = state.setValue(LIGHT, light);
		}

		if (state.hasProperty(OPAQUE)) {
			state = state.setValue(OPAQUE, opaque);
		}

		return state;
	}
}
