package dev.yoima.reccheck.ui;

import dev.yoima.reccheck.config.HudAnchor;
import dev.yoima.reccheck.config.ModConfig;
import dev.yoima.reccheck.config.ModConfigManager;
import dev.yoima.reccheck.obs.ObsConnectionManager;
import dev.yoima.reccheck.obs.ObsTestResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ConfigScreen extends Screen {
	private static final String NEW_ISSUE_URL = "https://github.com/yoima-jp/OBS-Recording-Monitor/issues/new/choose";

	private final Screen parent;
	private final ModConfigManager configManager;
	private final ObsConnectionManager connectionManager;
	private final ModConfig draft;
	private boolean passwordModified = false;

	private EditBox hostField;
	private EditBox portField;
	private EditBox passwordField;
	private Button autoReconnectButton;
	private Button showHudButton;
	private Button worldOnlyButton;
	private Button hudAnchorButton;
	private Button hudScaleButton;
	private Button passwordClearButton;
	private Button testButton;
	private Button saveButton;
	private Button cancelButton;
	private Button helpButton;
	private Button issueButton;
	private ObsTestResult lastTestResult = ObsTestResult.failure("test.reccheck.pending", "test.reccheck.pending.detail", dev.yoima.reccheck.obs.ObsIssueKind.UNKNOWN, List.of());

	public ConfigScreen(Screen parent, ModConfigManager configManager, ObsConnectionManager connectionManager) {
		super(Component.translatable("screen.reccheck.config.title"));
		this.parent = parent;
		this.configManager = configManager;
		this.connectionManager = connectionManager;
		this.draft = configManager.getConfig();
	}

	@Override
	protected void init() {
		int panelX = width / 2 - 170;
		int leftX = panelX + 12;
		int rightX = panelX + 178;
		int fieldWidth = 150;
		int inputY = 80;
		int toggleY = 80;
		int footerY = 196;
		int footerButtonWidth = 72;
		int footerButtonGap = 4;
		int footerLeftX = panelX + 12;
		int issueButtonSize = 20;

		hostField = addRenderableWidget(new EditBox(font, leftX, inputY, fieldWidth, 20, Component.translatable("screen.reccheck.config.obs_host")));
		hostField.setValue(draft.obsHost);

		portField = addRenderableWidget(new EditBox(font, leftX, inputY + 22, fieldWidth, 20, Component.translatable("screen.reccheck.config.obs_port")));
		portField.setValue(Integer.toString(draft.obsPort));

		passwordField = addRenderableWidget(new EditBox(font, leftX, inputY + 44, 110, 20, Component.translatable("screen.reccheck.config.obs_password")));
		passwordField.setValue("");
		passwordField.setSuggestion("password");
		passwordField.setResponder(value -> passwordModified = true);

		passwordClearButton = addRenderableWidget(Button.builder(Component.translatable("screen.reccheck.button.clear"), button -> {
			passwordField.setValue("");
			draft.obsPassword = "";
			updatePasswordButtonText();
		}).bounds(leftX + 114, inputY + 44, 36, 20).build());

		autoReconnectButton = addRenderableWidget(toggleButton(rightX, toggleY, 150, "screen.reccheck.config.auto_reconnect", () -> draft.autoReconnect = !draft.autoReconnect, () -> draft.autoReconnect));
		hudAnchorButton = addRenderableWidget(cycleButton(rightX, toggleY + 22, 150, "screen.reccheck.config.hud_position", () -> draft.hudAnchor = draft.hudAnchor.next(), () -> anchorLabel(draft.hudAnchor)));
		hudScaleButton = addRenderableWidget(cycleButton(rightX, toggleY + 44, 150, "screen.reccheck.config.hud_scale", () -> draft.hudScale = nextScale(draft.hudScale), () -> Component.literal(String.format("%.2fx", draft.hudScale))));
		worldOnlyButton = addRenderableWidget(toggleButton(rightX, toggleY + 66, 150, "screen.reccheck.config.world_only", () -> draft.worldOnly = !draft.worldOnly, () -> draft.worldOnly));
		showHudButton = addRenderableWidget(toggleButton(rightX, toggleY + 88, 150, "screen.reccheck.config.show_hud", () -> draft.showHud = !draft.showHud, () -> draft.showHud));
		issueButton = addRenderableWidget(Button.builder(Component.literal("!"), ConfirmLinkScreen.confirmLink(this, URI.create(NEW_ISSUE_URL), true)).bounds(panelX + 308, 22, issueButtonSize, issueButtonSize).build());
		issueButton.setTooltip(Tooltip.create(Component.translatable("screen.reccheck.button.issue")));

		testButton = addRenderableWidget(Button.builder(Component.translatable("screen.reccheck.button.test_short"), button -> runTest()).bounds(footerLeftX, footerY, footerButtonWidth, 20).build());
		helpButton = addRenderableWidget(Button.builder(Component.translatable("screen.reccheck.button.help_short"), button -> Minecraft.getInstance().setScreen(new HelpScreen(this))).bounds(footerLeftX + (footerButtonWidth + footerButtonGap), footerY, footerButtonWidth, 20).build());
		saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.reccheck.button.save"), button -> save()).bounds(footerLeftX + (footerButtonWidth + footerButtonGap) * 2, footerY, footerButtonWidth, 20).build());
		cancelButton = addRenderableWidget(Button.builder(Component.translatable("screen.reccheck.button.cancel"), button -> onClose()).bounds(footerLeftX + (footerButtonWidth + footerButtonGap) * 3, footerY, footerButtonWidth, 20).build());

		updatePasswordButtonText();
		refreshToggleText();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int panelX = width / 2 - 170;
		graphics.fillGradient(0, 0, width, height, 0xFF132030, 0xFF0E1116);
		graphics.fill(panelX, 18, panelX + 340, 286, 0xCC121821);
		graphics.fill(panelX, 18, panelX + 340, 19, 0xFF7EB6FF);
		graphics.text(font, title, width / 2 - 168, 22, 0xFFFFFFFF, false);
		graphics.textWithWordWrap(font, Component.translatable("screen.reccheck.config.subtitle"), panelX + 12, 38, 316, 0xFFB7C4D6);
		graphics.text(font, Component.translatable("screen.reccheck.section.connection"), panelX + 12, 62, 0xFFFFFFFF, false);
		graphics.text(font, Component.translatable("screen.reccheck.section.behavior"), panelX + 178, 62, 0xFFFFFFFF, false);

		graphics.text(font, Component.translatable("screen.reccheck.config.password_status", draft.obsPassword.isEmpty() ? Component.translatable("screen.reccheck.config.password_empty") : Component.translatable("screen.reccheck.config.password_set")), panelX + 12, 148, 0xFFB7C4D6, false);
		graphics.textWithWordWrap(font, Component.translatable("screen.reccheck.config.password_hint"), panelX + 12, 160, 150, 0xFF8694A7);

		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int panelY = 224;
		graphics.fill(panelX, panelY, panelX + 340, panelY + 38, 0xCC121821);
		graphics.fill(panelX, panelY, panelX + 340, panelY + 1, 0xFF7EB6FF);
		graphics.text(font, Component.translatable(lastTestResult.headlineKey()), panelX + 8, panelY + 7, lastTestResult.passed() ? 0xFF82F0A1 : 0xFFFF8A80, false);
		graphics.text(font, Component.translatable(lastTestResult.detailKey()), panelX + 8, panelY + 19, 0xFFB7C4D6, false);
		if (!lastTestResult.hints().isEmpty()) {
			graphics.text(font, Component.translatable(lastTestResult.hints().get(0)), panelX + 172, panelY + 7, 0xFFFFD35A, false);
			if (lastTestResult.hints().size() > 1) {
				graphics.text(font, Component.translatable(lastTestResult.hints().get(1)), panelX + 172, panelY + 19, 0xFFFFD35A, false);
			}
		}
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	private void save() {
		if (!commitDraft()) {
			return;
		}
		configManager.save(draft);
		connectionManager.applyConfig(configManager.getConfig(), true);
		Minecraft.getInstance().setScreen(parent);
	}

	private boolean commitDraft() {
		String host = hostField.getValue().trim();
		String portText = portField.getValue().trim();
		if (host.isBlank()) {
			hostField.setValue("localhost");
			host = "localhost";
		}
		try {
			int port = Integer.parseInt(portText);
			if (port < 1 || port > 65535) {
				lastTestResult = ObsTestResult.failure("test.reccheck.invalid_port", "test.reccheck.invalid_port.detail", dev.yoima.reccheck.obs.ObsIssueKind.INVALID_CONFIG, List.of("test.reccheck.hint.port"));
				return false;
			}
			draft.obsHost = host;
			draft.obsPort = port;
			if (passwordModified) {
				draft.obsPassword = passwordField.getValue();
				passwordModified = false;
			}
			return true;
		} catch (NumberFormatException ex) {
			lastTestResult = ObsTestResult.failure("test.reccheck.invalid_port", "test.reccheck.invalid_port.detail", dev.yoima.reccheck.obs.ObsIssueKind.INVALID_CONFIG, List.of("test.reccheck.hint.port"));
			return false;
		}
	}

	private void runTest() {
		if (!commitDraft()) {
			return;
		}
		CompletableFuture<ObsTestResult> future = connectionManager.testConnection(draft);
		lastTestResult = ObsTestResult.failure("test.reccheck.testing", "test.reccheck.testing.detail", dev.yoima.reccheck.obs.ObsIssueKind.UNKNOWN, List.of());
		future.whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (error != null) {
				lastTestResult = ObsTestResult.failure("test.reccheck.failed", "test.reccheck.failed.detail", dev.yoima.reccheck.obs.ObsIssueKind.UNKNOWN, List.of("test.reccheck.hint.retry"));
			} else {
				lastTestResult = result;
			}
		}));
	}

	private void updatePasswordButtonText() {
		passwordClearButton.setMessage(Component.translatable(draft.obsPassword.isEmpty() ? "screen.reccheck.button.password_empty" : "screen.reccheck.button.password_set"));
	}

	private void refreshToggleText() {
		autoReconnectButton.setMessage(toggleLabel("screen.reccheck.config.auto_reconnect", draft.autoReconnect));
		showHudButton.setMessage(toggleLabel("screen.reccheck.config.show_hud", draft.showHud));
		worldOnlyButton.setMessage(toggleLabel("screen.reccheck.config.world_only", draft.worldOnly));
		hudAnchorButton.setMessage(Component.translatable("screen.reccheck.config.hud_position", anchorLabel(draft.hudAnchor)));
		hudScaleButton.setMessage(Component.translatable("screen.reccheck.config.hud_scale", Component.literal(String.format("%.2fx", draft.hudScale))));
	}

	private Button cycleButton(int x, int y, int width, String key, Runnable action, java.util.function.Supplier<Component> suffixSupplier) {
		return Button.builder(Component.translatable(key, suffixSupplier.get()), button -> {
			action.run();
			refreshToggleText();
		}).bounds(x, y, width, 20).build();
	}

	private Button toggleButton(int x, int y, int width, String key, Runnable action, java.util.function.BooleanSupplier getter) {
		return Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
			action.run();
			refreshToggleText();
		}).bounds(x, y, width, 20).build();
	}

	private static Component toggleLabel(String key, boolean value) {
		return Component.translatable(key, Component.translatable(value ? "screen.reccheck.state.on" : "screen.reccheck.state.off"));
	}

	private static Component anchorLabel(HudAnchor anchor) {
		return Component.translatable(switch (anchor) {
			case TOP_RIGHT -> "screen.reccheck.anchor.top_right";
			case TOP_LEFT -> "screen.reccheck.anchor.top_left";
			case BOTTOM_RIGHT -> "screen.reccheck.anchor.bottom_right";
			case BOTTOM_LEFT -> "screen.reccheck.anchor.bottom_left";
		});
	}

	private static double nextScale(double scale) {
		return switch ((int) Math.round(scale * 100)) {
			case 75 -> 100D;
			case 100 -> 125D;
			case 125 -> 150D;
			case 150 -> 175D;
			default -> 75D;
		} / 100D;
	}
}
