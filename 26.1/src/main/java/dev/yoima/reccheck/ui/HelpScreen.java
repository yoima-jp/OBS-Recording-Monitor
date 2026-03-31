package dev.yoima.reccheck.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class HelpScreen extends Screen {
	private static final int PANEL_MAX_WIDTH = 420;
	private static final int PANEL_MARGIN_TOP = 16;
	private static final int PANEL_MARGIN_BOTTOM = 36;
	private static final int CONTENT_SIDE_PADDING = 12;
	private static final int HEADER_HEIGHT = 28;
	private static final int CONTENT_TOP = PANEL_MARGIN_TOP + HEADER_HEIGHT + 10;
	private static final int CONTENT_BOTTOM_PADDING = 10;
	private static final int IMAGE_MAX_HEIGHT = 120;
	private static final int IMAGE_SPACING = 12;
	private static final int SECTION_SPACING = 14;
	private static final int CARD_PADDING = 6;
	private static final int LABEL_IMAGE_GAP = 8;

	private final Screen parent;
	private int scroll = 0;
	private List<net.minecraft.util.FormattedCharSequence> lines;
	private List<HelpImage> images = List.of();
	private int maxScroll = 0;
	private int panelWidth = PANEL_MAX_WIDTH;

	public HelpScreen(Screen parent) {
		super(Component.translatable("screen.reccheck.help.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(PANEL_MAX_WIDTH, width - 24);
		lines = font.split(Component.translatable("screen.reccheck.help.body"), contentWidth());
		images = buildImages();
		maxScroll = computeMaxScroll();
		scroll = Math.min(scroll, maxScroll);
		addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.reccheck.button.back"), button -> onClose()).bounds(width / 2 - 40, height - 28, 80, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, width, height, 0xFF132030, 0xFF0E1116);
		int panelLeft = width / 2 - panelWidth / 2;
		int panelRight = panelLeft + panelWidth;
		int panelBottom = height - PANEL_MARGIN_BOTTOM;
		graphics.fill(panelLeft, PANEL_MARGIN_TOP, panelRight, panelBottom, 0xCC121821);
		graphics.fill(panelLeft, PANEL_MARGIN_TOP, panelRight, PANEL_MARGIN_TOP + 1, 0xFF7EB6FF);
		graphics.text(font, title, panelLeft + CONTENT_SIDE_PADDING, PANEL_MARGIN_TOP + 6, 0xFFFFFFFF, false);

		int contentLeft = panelLeft + CONTENT_SIDE_PADDING;
		int contentWidth = contentWidth();
		int contentTop = CONTENT_TOP;
		int contentBottom = panelBottom - CONTENT_BOTTOM_PADDING;
		int y = CONTENT_TOP - scroll;

		graphics.enableScissor(contentLeft, contentTop, panelRight - CONTENT_SIDE_PADDING, contentBottom);
		graphics.text(font, Component.translatable("screen.reccheck.help.images"), contentLeft, y, 0xFFFFFFFF, false);
		y += SECTION_SPACING;

		for (HelpImage image : images) {
			graphics.text(font, Component.translatable(image.labelKey()), contentLeft, y, 0xFFB7C4D6, false);
			y += 12 + LABEL_IMAGE_GAP;
			ImageSize size = scaledSize(image, contentWidth - CARD_PADDING * 2);
			int drawWidth = size.width();
			int drawHeight = size.height();
			int drawX = contentLeft + (contentWidth - drawWidth) / 2;
			graphics.fill(drawX - CARD_PADDING, y, drawX + drawWidth + CARD_PADDING, y + drawHeight + CARD_PADDING * 2, 0x44272F3A);
			graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture(), drawX, y, 0.0F, 0.0F, drawWidth, drawHeight, image.width(), image.height(), image.width(), image.height());
			y += drawHeight + CARD_PADDING * 2 + IMAGE_SPACING;
		}

		graphics.text(font, Component.translatable("screen.reccheck.help.details"), contentLeft, y, 0xFFFFFFFF, false);
		y += SECTION_SPACING;
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(font, lines.get(i), contentLeft, y, 0xFFB7C4D6, false);
			y += 10;
		}
		graphics.disableScissor();

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(scroll + (int) (-verticalAmount * 12), maxScroll));
		return true;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	private int computeMaxScroll() {
		int totalHeight = SECTION_SPACING;
		for (HelpImage image : images) {
			ImageSize size = scaledSize(image, contentWidth() - CARD_PADDING * 2);
			totalHeight += 12 + LABEL_IMAGE_GAP + size.height() + CARD_PADDING * 2 + IMAGE_SPACING;
		}
		totalHeight += SECTION_SPACING + lines.size() * 10;
		int visibleHeight = height - CONTENT_TOP - PANEL_MARGIN_BOTTOM - CONTENT_BOTTOM_PADDING;
		return Math.max(0, totalHeight - visibleHeight);
	}

	private int contentWidth() {
		return panelWidth - CONTENT_SIDE_PADDING * 2;
	}

	private static ImageSize scaledSize(HelpImage image, int maxWidth) {
		int drawWidth = Math.min(maxWidth, image.width());
		int drawHeight = Math.max(1, image.height() * drawWidth / image.width());
		if (drawHeight > IMAGE_MAX_HEIGHT) {
			drawHeight = IMAGE_MAX_HEIGHT;
			drawWidth = Math.max(1, image.width() * drawHeight / image.height());
		}
		return new ImageSize(drawWidth, drawHeight);
	}

	private List<HelpImage> buildImages() {
		boolean japanese = isJapanese();
		String prefix = japanese ? "ja" : "en";
		List<HelpImage> result = new ArrayList<>();
		result.add(new HelpImage(texture(prefix + "1"), japanese ? "screen.reccheck.help.step1" : "screen.reccheck.help.step1", japanese ? 904 : 964, japanese ? 373 : 335));
		result.add(new HelpImage(texture(prefix + "2"), japanese ? "screen.reccheck.help.step2" : "screen.reccheck.help.step2", japanese ? 671 : 659, japanese ? 329 : 337));
		result.add(new HelpImage(texture(prefix + "3"), japanese ? "screen.reccheck.help.step3" : "screen.reccheck.help.step3", japanese ? 437 : 434, japanese ? 166 : 204));
		return result;
	}

	private static Identifier texture(String name) {
		return Identifier.fromNamespaceAndPath("reccheck", "textures/help/" + name + ".png");
	}

	private static boolean isJapanese() {
		String code = Minecraft.getInstance().getLanguageManager().getSelected();
		return code != null && code.toLowerCase(Locale.ROOT).startsWith("ja");
	}

	private record HelpImage(Identifier texture, String labelKey, int width, int height) {
	}

	private record ImageSize(int width, int height) {
	}
}
