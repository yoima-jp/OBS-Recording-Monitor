package dev.yoima.reccheck.config;

public final class ModConfig {
	public String obsHost = "localhost";
	public int obsPort = 4455;
	public String obsPassword = "";
	public boolean autoReconnect = true;
	public boolean showHud = true;
	public HudAnchor hudAnchor = HudAnchor.TOP_RIGHT;
	public double hudScale = 1.0D;
	public boolean worldOnly = false;

	public ModConfig copy() {
		ModConfig copy = new ModConfig();
		copy.obsHost = obsHost;
		copy.obsPort = obsPort;
		copy.obsPassword = obsPassword;
		copy.autoReconnect = autoReconnect;
		copy.showHud = showHud;
		copy.hudAnchor = hudAnchor;
		copy.hudScale = hudScale;
		copy.worldOnly = worldOnly;
		return copy;
	}

	public void copyFrom(ModConfig other) {
		obsHost = other.obsHost;
		obsPort = other.obsPort;
		obsPassword = other.obsPassword;
		autoReconnect = other.autoReconnect;
		showHud = other.showHud;
		hudAnchor = other.hudAnchor;
		hudScale = other.hudScale;
		worldOnly = other.worldOnly;
	}

	public static ModConfig defaults() {
		return new ModConfig();
	}
}
