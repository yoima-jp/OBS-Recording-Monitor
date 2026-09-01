package dev.yoima.reccheck.hud;

import dev.yoima.reccheck.RecCheckClient;
import dev.yoima.reccheck.config.HudAnchor;
import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.obs.ObsConnectionSnapshot;
import dev.yoima.reccheck.obs.ObsConnectionState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ObsHudRenderer {
	private static final int ICON_BOX_X = 8;
	private static final int ICON_BOX_Y = 6;
	private static final int ICON_BOX_SIZE = 10;
	private static final int TEXT_X = 22;
	private static final int HEADLINE_Y = 6;
	private static final int DETAIL_Y = 18;
	private static final int HINT_BOX_Y = 28;
	private static final int HINT_TEXT_Y = 31;
	private static final int LITE_ICON_SIZE = 12;

	private final dev.yoima.reccheck.obs.ObsConnectionManager manager;
	private final ModConfigManager configManager;

	public ObsHudRenderer(dev.yoima.reccheck.obs.ObsConnectionManager manager, ModConfigManager configManager) {
		this.manager = manager;
		this.configManager = configManager;
	}

	public void render(GuiGraphics graphics, float tickDelta) {
		Minecraft client = Minecraft.getInstance();
		renderOverlay(client, client.screen, graphics);
	}

	public void renderOverlay(Minecraft client, Screen screen, GuiGraphics graphics) {
		if (client == null) {
			return;
		}
		ObsConnectionSnapshot snapshot = manager.snapshot();
		var config = configManager.getConfig();
		if (!shouldRender(client, screen, config, snapshot)) {
			return;
		}

		int width = client.getWindow().getGuiScaledWidth();
		int height = client.getWindow().getGuiScaledHeight();
		float scale = (float) config.hudScale;
		if (config.liteHud) {
			renderLiteHud(graphics, width, height, scale, config.hudAnchor, snapshot);
			return;
		}
		Component headline = Component.translatable(snapshot.headlineKey());
		Component detail = Component.translatable(snapshot.detailKey());
		Component hint = snapshot.state() == ObsConnectionState.CONNECTED_NOT_RECORDING
			? startHint()
			: Component.empty();

		int panelWidth = computePanelWidth(client, headline, detail, hint);
		int panelHeight = hint.getString().isEmpty() ? 38 : 52;

		int drawWidth = Math.round(panelWidth * scale);
		int drawHeight = Math.round(panelHeight * scale);
		int margin = 8;
		int x = switch (config.hudAnchor) {
			case TOP_RIGHT, BOTTOM_RIGHT -> width - margin - drawWidth;
			case TOP_LEFT, BOTTOM_LEFT -> margin;
		};
		int y = switch (config.hudAnchor) {
			case TOP_LEFT, TOP_RIGHT -> margin;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> height - margin - drawHeight;
		};

		int bg = backgroundColor(snapshot);
		graphics.fill(x, y, x + drawWidth, y + drawHeight, bg);
		graphics.fill(x, y, x + drawWidth, y + 1, 0x80FFFFFF);
		graphics.fill(x, y + drawHeight - 1, x + drawWidth, y + drawHeight, 0x40222222);

		int accent = accentColor(snapshot);
		Component icon = iconFor(snapshot);
		graphics.fill(x, y, x + 3, y + drawHeight, accent);
		graphics.fill(x + ICON_BOX_X, y + ICON_BOX_Y, x + ICON_BOX_X + ICON_BOX_SIZE, y + ICON_BOX_Y + ICON_BOX_SIZE, accent);
		graphics.drawString(client.font, icon, iconX(client, x, icon), iconY(client, y), 0xFFFFFFFF, false);

		graphics.drawString(client.font, headline, x + TEXT_X, y + HEADLINE_Y, 0xFFFFFFFF, false);
		graphics.drawString(client.font, detail, x + TEXT_X, y + DETAIL_Y, 0xFFd7d7d7, false);

		if (!hint.getString().isEmpty()) {
			graphics.fill(x + TEXT_X, y + HINT_BOX_Y, x + drawWidth - 6, y + drawHeight - 4, 0x66000000);
			graphics.drawString(client.font, hint, x + TEXT_X + 4, y + HINT_TEXT_Y, 0xFFFFD35A, false);
		}
	}

	private boolean shouldRender(Minecraft client, Screen screen, dev.yoima.reccheck.config.ModConfig config, ObsConnectionSnapshot snapshot) {
		if (!config.showHud) {
			return false;
		}
		boolean inWorld = client.level != null;
		if (config.worldOnly && !inWorld) {
			return false;
		}
		if (!inWorld && screen == null) {
			return false;
		}
		return snapshot.state() != ObsConnectionState.CONNECTED_RECORDING;
	}

	private int computePanelWidth(Minecraft client, Component headline, Component detail, Component hint) {
		int textWidth = Math.max(client.font.width(headline), client.font.width(detail));
		if (!hint.getString().isEmpty()) {
			textWidth = Math.max(textWidth, client.font.width(hint));
		}
		return Math.max(126, textWidth + 34);
	}

	private static Component startHint() {
		RecCheckClient mod = RecCheckClient.get();
		Component keyName = mod != null && mod.startRecordKey() != null
			? mod.startRecordKey().getTranslatedKeyMessage()
			: Component.literal("F9");
		return Component.translatable("hud.reccheck.start_hint", keyName);
	}

	private static int iconX(Minecraft client, int panelX, Component icon) {
		int iconWidth = client.font.width(icon);
		return panelX + ICON_BOX_X + Math.max(0, (ICON_BOX_SIZE - iconWidth) / 2);
	}

	private static int iconY(Minecraft client, int panelY) {
		int verticalPadding = Math.max(0, (ICON_BOX_SIZE - client.font.lineHeight) / 2);
		return panelY + ICON_BOX_Y + verticalPadding + 1;
	}

	private static int backgroundColor(ObsConnectionSnapshot snapshot) {
		return switch (snapshot.state()) {
			case CONNECTED_NOT_RECORDING -> 0xD0422B10;
			case CONNECTING -> 0xCC1E2B3B;
			case AUTH_FAILED -> 0xD0451E1E;
			case DISCONNECTED -> 0xD03D260E;
			case ERROR -> 0xD03B1C1C;
			default -> 0xCC1E2B3B;
		};
	}

	private static int accentColor(ObsConnectionSnapshot snapshot) {
		return switch (snapshot.state()) {
			case CONNECTED_NOT_RECORDING -> 0xFFE7A72A;
			case CONNECTING -> 0xFF7EB6FF;
			case AUTH_FAILED, ERROR -> 0xFFFF6B6B;
			case DISCONNECTED -> 0xFFFFA54A;
			default -> 0xFFE7A72A;
		};
	}

	private static void renderLiteHud(GuiGraphics graphics, int width, int height, float scale, HudAnchor anchor, ObsConnectionSnapshot snapshot) {
		int size = Math.max(8, Math.round(LITE_ICON_SIZE * scale));
		int margin = 8;
		int x = switch (anchor) {
			case TOP_RIGHT, BOTTOM_RIGHT -> width - margin - size;
			case TOP_LEFT, BOTTOM_LEFT -> margin;
		};
		int y = switch (anchor) {
			case TOP_LEFT, TOP_RIGHT -> margin;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> height - margin - size;
		};

		// Lite HUD intentionally avoids text and a panel so a captured stream only sees the smallest actionable status marker.
		drawCircle(graphics, x, y, size, 0xCC111111);
		drawCircle(graphics, x + 1, y + 1, size - 2, liteColor(snapshot));
	}

	private static void drawCircle(GuiGraphics graphics, int x, int y, int diameter, int color) {
		double center = (diameter - 1) / 2.0D;
		double radius = diameter / 2.0D;
		for (int row = 0; row < diameter; row++) {
			double dy = row - center;
			double halfWidth = Math.sqrt(Math.max(0.0D, radius * radius - dy * dy));
			int left = (int) Math.ceil(center - halfWidth);
			int right = (int) Math.floor(center + halfWidth) + 1;
			graphics.fill(x + left, y + row, x + right, y + row + 1, color);
		}
	}

	private static int liteColor(ObsConnectionSnapshot snapshot) {
		return switch (snapshot.state()) {
			case CONNECTED_NOT_RECORDING -> 0xFFFF4545;
			case CONNECTING -> 0xFFFFD24A;
			case DISCONNECTED -> 0xFFFF9F43;
			case AUTH_FAILED, ERROR -> 0xFFFF5C8A;
			default -> 0xFFFF4545;
		};
	}

	private static Component iconFor(ObsConnectionSnapshot snapshot) {
		return switch (snapshot.state()) {
			case CONNECTED_NOT_RECORDING -> Component.literal("!");
			case CONNECTING -> Component.literal("…");
			case AUTH_FAILED, ERROR -> Component.literal("x");
			case DISCONNECTED -> Component.literal("?");
			default -> Component.literal(" ");
		};
	}
}
