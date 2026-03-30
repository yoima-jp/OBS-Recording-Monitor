package dev.yoima.reccheck.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path = FabricLoader.getInstance().getConfigDir().resolve("reccheck.json");
	private ModConfig config = ModConfig.defaults();

	public synchronized void load() {
		if (!Files.exists(path)) {
			config = ModConfig.defaults();
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			ModConfig loaded = ModConfig.defaults();
			loaded.obsHost = getString(root, "obsHost", loaded.obsHost);
			loaded.obsPort = getInt(root, "obsPort", loaded.obsPort);
			loaded.obsPassword = getString(root, "obsPassword", loaded.obsPassword);
			loaded.autoReconnect = getBoolean(root, "autoReconnect", loaded.autoReconnect);
			loaded.hudAnchor = parseHudAnchor(getString(root, "hudAnchor", loaded.hudAnchor.name()));
			loaded.hudScale = getDouble(root, "hudScale", loaded.hudScale);
			loaded.notificationSound = getBoolean(root, "notificationSound", loaded.notificationSound);
			loaded.worldOnly = getBoolean(root, "worldOnly", loaded.worldOnly);
			loaded.showOnTitleScreen = getBoolean(root, "showOnTitleScreen", loaded.showOnTitleScreen);
			loaded.showStartRecordHint = getBoolean(root, "showStartRecordHint", loaded.showStartRecordHint);
			config = normalize(loaded);
		} catch (Exception ex) {
			config = ModConfig.defaults();
		}
	}

	public synchronized void save(ModConfig draft) {
		config = normalize(draft.copy());
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(toJson(config), writer);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to save config", ex);
		}
	}

	public synchronized ModConfig getConfig() {
		return config.copy();
	}

	private static ModConfig normalize(ModConfig config) {
		if (config.obsHost == null || config.obsHost.isBlank()) {
			config.obsHost = "localhost";
		}
		if (config.obsPort < 1 || config.obsPort > 65535) {
			config.obsPort = 4455;
		}
		if (config.hudScale < 0.5D) {
			config.hudScale = 0.5D;
		}
		if (config.hudScale > 2.5D) {
			config.hudScale = 2.5D;
		}
		return config;
	}

	private static JsonObject toJson(ModConfig config) {
		JsonObject root = new JsonObject();
		root.addProperty("obsHost", config.obsHost);
		root.addProperty("obsPort", config.obsPort);
		root.addProperty("obsPassword", config.obsPassword);
		root.addProperty("autoReconnect", config.autoReconnect);
		root.addProperty("hudAnchor", config.hudAnchor.name());
		root.addProperty("hudScale", config.hudScale);
		root.addProperty("notificationSound", config.notificationSound);
		root.addProperty("worldOnly", config.worldOnly);
		root.addProperty("showOnTitleScreen", config.showOnTitleScreen);
		root.addProperty("showStartRecordHint", config.showStartRecordHint);
		return root;
	}

	private static String getString(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static int getInt(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}

	private static double getDouble(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}

	private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	private static HudAnchor parseHudAnchor(String value) {
		try {
			return HudAnchor.valueOf(value);
		} catch (IllegalArgumentException ex) {
			return HudAnchor.TOP_RIGHT;
		}
	}
}
