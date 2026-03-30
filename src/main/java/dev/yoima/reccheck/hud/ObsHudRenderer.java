package dev.yoima.reccheck.hud;

import dev.yoima.reccheck.config.HudAnchor;
import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.obs.ObsConnectionSnapshot;
import dev.yoima.reccheck.obs.ObsConnectionState;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ObsHudRenderer implements HudElement {
	private final dev.yoima.reccheck.obs.ObsConnectionManager manager;
	private final ModConfigManager configManager;

	public ObsHudRenderer(dev.yoima.reccheck.obs.ObsConnectionManager manager, ModConfigManager configManager) {
		this.manager = manager;
		this.configManager = configManager;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
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
		Component headline = Component.translatable(snapshot.headlineKey());
		Component detail = Component.translatable(snapshot.detailKey());
		Component hint = config.showStartRecordHint && snapshot.state() == ObsConnectionState.CONNECTED_NOT_RECORDING
			? Component.translatable("hud.reccheck.start_hint")
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
		graphics.fill(x, y, x + 3, y + drawHeight, accent);
		graphics.fill(x + 8, y + 8, x + 14, y + 14, accent);
		graphics.drawString(client.font, iconFor(snapshot), x + 8, y + 4, 0xFFFFFFFF, false);

		graphics.drawString(client.font, headline, x + 22, y + 6, 0xFFFFFFFF, false);
		graphics.drawString(client.font, detail, x + 22, y + 18, 0xFFd7d7d7, false);

		if (!hint.getString().isEmpty()) {
			graphics.fill(x + 22, y + 28, x + drawWidth - 6, y + drawHeight - 4, 0x66000000);
			graphics.drawString(client.font, hint, x + 26, y + 31, 0xFFFFD35A, false);
		}
	}

	private boolean shouldRender(Minecraft client, Screen screen, dev.yoima.reccheck.config.ModConfig config, ObsConnectionSnapshot snapshot) {
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
