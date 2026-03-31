package dev.yoima.reccheck.config;

public enum HudAnchor {
	TOP_RIGHT,
	TOP_LEFT,
	BOTTOM_RIGHT,
	BOTTOM_LEFT;

	public HudAnchor next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
