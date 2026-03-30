package dev.yoima.reccheck.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class HelpScreen extends Screen {
	private final Screen parent;
	private int scroll = 0;
	private List<net.minecraft.util.FormattedCharSequence> lines;

	public HelpScreen(Screen parent) {
		super(Component.translatable("screen.reccheck.help.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		lines = font.split(Component.translatable("screen.reccheck.help.body"), width - 70);
		addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.reccheck.button.back"), button -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, width, height, 0xFF132030, 0xFF0E1116);
		graphics.fill(width / 2 - 170, 16, width / 2 + 170, height - 36, 0xCC121821);
		graphics.fill(width / 2 - 170, 16, width / 2 + 170, 17, 0xFF7EB6FF);
		graphics.drawString(font, title, width / 2 - 166, 22, 0xFFFFFFFF, false);

		int left = width / 2 - 160;
		int top = 38 - scroll;
		int y = top;
		for (int i = 0; i < lines.size(); i++) {
			graphics.drawString(font, lines.get(i), left, y, 0xFFB7C4D6, false);
			y += 10;
		}

		super.render(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(scroll + (int) (-verticalAmount * 12), Math.max(0, lines.size() * 10 - (height - 70))));
		return true;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}
}
