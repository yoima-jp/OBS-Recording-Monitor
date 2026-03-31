package dev.yoima.reccheck;

import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.hud.ObsHudRenderer;
import dev.yoima.reccheck.obs.ObsConnectionManager;
import dev.yoima.reccheck.ui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class RecCheckClient implements ClientModInitializer {
	public static final String MOD_ID = "reccheck";
	private static RecCheckClient instance;

	private final ModConfigManager configManager = new ModConfigManager();
	private final ObsConnectionManager obsConnectionManager = new ObsConnectionManager();
	private final ObsHudRenderer hudRenderer = new ObsHudRenderer(obsConnectionManager, configManager);
	private final Set<Screen> overlayHookedScreens = Collections.newSetFromMap(new WeakHashMap<>());
	private KeyMapping startRecordKey;

	public RecCheckClient() {
		instance = this;
	}

	@Override
	public void onInitializeClient() {
		configManager.load();
		obsConnectionManager.applyConfig(configManager.getConfig(), false);

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "obs_warning"), hudRenderer);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof OptionsScreen) {
				var buttons = Screens.getWidgets(screen);
				String openLabel = Component.translatable("screen.reccheck.button.open_short").getString();
				String doneLabel = Component.translatable("gui.done").getString();
				buttons.removeIf(existing -> existing.getMessage().getString().equals(openLabel));

				int leftColumnX = Integer.MAX_VALUE;
				int leftColumnWidth = 150;
				int maxGridY = -1;
				int doneY = Integer.MAX_VALUE;
				Object doneButton = null;

				for (var existing : buttons) {
					String text = existing.getMessage().getString();
					if (text.equals(doneLabel)) {
						doneY = existing.getY();
						doneButton = existing;
						continue;
					}

					if (text.equals(openLabel)) {
						continue;
					}

					if (existing.getWidth() >= 140 && existing.getX() <= width / 2 - 2) {
						leftColumnX = Math.min(leftColumnX, existing.getX());
						leftColumnWidth = existing.getWidth();
						maxGridY = Math.max(maxGridY, existing.getY());
					} else if (existing.getWidth() >= 140 && existing.getX() > width / 2 - 2) {
						maxGridY = Math.max(maxGridY, existing.getY());
					}
				}

				if (leftColumnX == Integer.MAX_VALUE) {
					leftColumnX = width / 2 - 155;
				}

				int desiredOpenY = maxGridY >= 0 ? maxGridY + 24 : height - 96;
				if (doneY != Integer.MAX_VALUE && desiredOpenY > doneY - 24 && doneButton instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
					int shiftedDoneY = Math.min(height - 28, doneY + 24);
					if (shiftedDoneY > doneY) {
						widget.setY(shiftedDoneY);
						doneY = shiftedDoneY;
					}
				}

				int openY = desiredOpenY;
				if (doneY != Integer.MAX_VALUE) {
					openY = Math.min(openY, doneY - 24);
				}
				openY = Math.max(24, openY);

				Button button = Button.builder(Component.translatable("screen.reccheck.button.open_short"), b -> client.setScreen(new ConfigScreen(screen, configManager, obsConnectionManager)))
					.bounds(leftColumnX, openY, leftColumnWidth, 20)
					.build();
				buttons.add(button);
			}
			if (overlayHookedScreens.add(screen)) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
					if (client.level == null) {
						hudRenderer.renderOverlay(client, (Screen) s, graphics);
					}
				});
			}
		});
		obsConnectionManager.start();

		startRecordKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.reccheck.start_record",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_F9,
			KeyMapping.Category.MISC
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (startRecordKey.consumeClick()) {
				obsConnectionManager.requestStartRecording(client);
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
