package io.github.trini3.veneer.block;

import net.minecraft.core.Direction;

/**
 * The checker pattern shared by every checkered frame. A block is split into eight half-size cells; a cell's slot
 * is the parity of its corner (slot 0 holds the cell at the block's lowest x, y and z corner). The pattern uses
 * world axes, never the block's facing, so checkered pieces of any kind line up into one continuous checkerboard
 * (a checkered slab under a checkered slab makes the same pattern as a checkered block).
 */
public final class Checker {
	private Checker() {
	}

	/** Slot of the cell holding a local point (0..1 coordinates). */
	public static int slot(double x, double y, double z) {
		return (x > 0.5 ? 1 : 0) ^ (y > 0.5 ? 1 : 0) ^ (z > 0.5 ? 1 : 0);
	}

	/** Whether a local point lies in the half of the block on the {@code side} side. */
	public static boolean inHalf(Direction side, double x, double y, double z) {
		double along = switch (side.getAxis()) {
			case X -> x;
			case Y -> y;
			case Z -> z;
		};
		return side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along > 0.5 : along < 0.5;
	}
}
