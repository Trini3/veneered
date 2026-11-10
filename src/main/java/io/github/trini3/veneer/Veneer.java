package io.github.trini3.veneer;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

public final class Veneer implements ModInitializer {
	public static final String MOD_ID = "veneer";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		VeneerRegistry.init();
	}
}
