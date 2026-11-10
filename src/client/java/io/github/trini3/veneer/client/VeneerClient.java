package io.github.trini3.veneer.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

import io.github.trini3.veneer.block.VeneerHolder;

public final class VeneerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Wrap every baked frame model so it can repaint its faces with the stored appearance.
		ModelLoadingPlugin.register(pluginContext -> pluginContext.modifyBlockModelAfterBake().register((model, context) -> {
			if (context.state().getBlock() instanceof VeneerHolder holder) {
				return new VeneerBlockStateModel(model, holder);
			}

			return model;
		}));
	}
}
