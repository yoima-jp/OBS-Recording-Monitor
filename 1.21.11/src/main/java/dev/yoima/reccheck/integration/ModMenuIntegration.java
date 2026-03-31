package dev.yoima.reccheck.integration;

import dev.yoima.reccheck.RecCheckClient;
import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.obs.ObsConnectionManager;
import dev.yoima.reccheck.ui.ConfigScreen;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class ModMenuIntegration implements com.terraformersmc.modmenu.api.ModMenuApi {
	@Override
	public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> new ConfigScreen(parent, getConfigManager(), getConnectionManager());
	}

	private static ModConfigManager getConfigManager() {
		return RecCheckClient.get() != null ? RecCheckClient.get().configManager() : new ModConfigManager();
	}

	private static ObsConnectionManager getConnectionManager() {
		return RecCheckClient.get() != null ? RecCheckClient.get().obsConnectionManager() : new ObsConnectionManager();
	}
}
