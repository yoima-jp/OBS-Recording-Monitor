package dev.yoima.reccheck;

import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.hud.ObsHudRenderer;
import dev.yoima.reccheck.obs.ObsConnectionManager;
import dev.yoima.reccheck.ui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class RecCheckClient implements ClientModInitializer {
	public static final String MOD_ID = "reccheck";
	private static RecCheckClient instance;

	private final ModConfigManager configManager = new ModConfigManager();
	private final ObsConnectionManager obsConnectionManager = new ObsConnectionManager();
	private KeyMapping startRecordKey;

	public RecCheckClient() {
		instance = this;
	}

	@Override
	public void onInitializeClient() {
		configManager.load();
		obsConnectionManager.applyConfig(configManager.getConfig(), false);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "obs_warning"), new ObsHudRenderer(obsConnectionManager, configManager));

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) {
				Button button = Button.builder(Component.translatable("screen.reccheck.config.title"), b -> client.setScreen(new ConfigScreen(screen, configManager, obsConnectionManager)))
					.bounds(width / 2 - 100, height / 4 + 132, 200, 20)
					.build();
				Screens.getButtons(screen).add(button);
			}
		});
		obsConnectionManager.start();

		startRecordKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.reccheck.start_record",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_F9,
			KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (startRecordKey.consumeClick()) {
				if (configManager.getConfig().showStartRecordHint) {
					obsConnectionManager.requestStartRecording(client);
				}
			}
		});

	}

	public static RecCheckClient get() {
		return instance;
	}

	public ModConfigManager configManager() {
		return configManager;
	}

	public ObsConnectionManager obsConnectionManager() {
		return obsConnectionManager;
	}

	public KeyMapping startRecordKey() {
		return startRecordKey;
	}
}
