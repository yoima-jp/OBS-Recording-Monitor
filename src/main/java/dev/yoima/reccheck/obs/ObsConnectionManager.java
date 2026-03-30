package dev.yoima.reccheck.obs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.yoima.reccheck.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ObsConnectionManager {
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "REC-Check-OBS");
			thread.setDaemon(true);
			return thread;
		}
	});
	private final AtomicReference<ModConfig> config = new AtomicReference<>(ModConfig.defaults());
	private final AtomicReference<ObsConnectionSnapshot> snapshot = new AtomicReference<>(ObsConnectionSnapshot.connecting("hud.reccheck.connecting", "hud.reccheck.connecting.detail.initial", false));
	private final AtomicLong generation = new AtomicLong();
	private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
	private final ConcurrentHashMap<String, CompletableFuture<ObsTestResult>> pendingRequests = new ConcurrentHashMap<>();
	private volatile WebSocket currentSocket;
	private volatile CompletableFuture<ObsTestResult> pendingCommand;
	private volatile long lastWarningSoundState = -1L;

	public void start() {
		reconnectScheduled.set(false);
		scheduleConnect(0L, false, false);
	}

	public void shutdown() {
		WebSocket socket = currentSocket;
		if (socket != null) {
			socket.abort();
		}
		executor.shutdownNow();
	}

	public void applyConfig(ModConfig newConfig, boolean reconnect) {
		config.set(Objects.requireNonNull(newConfig).copy());
		if (reconnect) {
			reconnectNow();
		}
	}

	public ObsConnectionSnapshot snapshot() {
		return snapshot.get();
	}

	public CompletableFuture<ObsTestResult> testConnection(ModConfig draft) {
		CompletableFuture<ObsTestResult> future = new CompletableFuture<>();
		executor.execute(() -> runConnectionTest(draft.copy(), future));
		return future;
	}

	public void requestStartRecording(Minecraft client) {
		if (!config.get().showStartRecordHint) {
			return;
		}
		WebSocket socket = currentSocket;
		if (socket == null || snapshot.get().state() != ObsConnectionState.CONNECTED_NOT_RECORDING) {
			return;
		}
		sendRequest("StartRecord", new JsonObject(), result -> {
			if (!result.passed()) {
				showClientMessage(client, Component.translatable("command.reccheck.start_record.failed"));
			}
		});
	}

	private void reconnectNow() {
		generation.incrementAndGet();
		reconnectScheduled.set(false);
		WebSocket socket = currentSocket;
		if (socket != null) {
			socket.abort();
		}
		scheduleConnect(0L, false, true);
	}

	private void scheduleConnect(long delayMillis, boolean reconnecting, boolean manual) {
		if (reconnecting && !reconnectScheduled.compareAndSet(false, true)) {
			return;
		}
		long connectGeneration = generation.get();
		executor.schedule(() -> {
			reconnectScheduled.set(false);
			connect(connectGeneration, reconnecting, manual);
		}, delayMillis, TimeUnit.MILLISECONDS);
	}

	private void connect(long connectGeneration, boolean reconnecting, boolean manual) {
		ModConfig cfg = config.get();
		if (!isValidConfig(cfg)) {
			updateSnapshot(ObsConnectionSnapshot.error("hud.reccheck.invalid_config", "hud.reccheck.invalid_config.detail", ObsIssueKind.INVALID_CONFIG));
			return;
		}

		updateSnapshot(ObsConnectionSnapshot.connecting(reconnecting ? "hud.reccheck.reconnecting" : "hud.reccheck.connecting", reconnecting ? "hud.reccheck.connecting.detail.retry" : "hud.reccheck.connecting.detail.initial", reconnecting));

		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			WebSocket.Listener listener = new ObsListener(connectGeneration, cfg);
			WebSocket socket = client.newWebSocketBuilder()
				.subprotocols("obswebsocket.json")
				.buildAsync(buildUri(cfg), listener)
				.join();
			if (connectGeneration != generation.get()) {
				socket.abort();
				return;
			}
			currentSocket = socket;
			socket.request(1);
		} catch (Exception ex) {
			handleConnectFailure(connectGeneration, reconnecting, ex);
			if (cfg.autoReconnect) {
				scheduleConnect(5000L, true, false);
			}
		}
	}

	private void handleConnectFailure(long connectGeneration, boolean reconnecting, Exception ex) {
		if (connectGeneration != generation.get()) {
			return;
		}
		ObsIssueKind kind = classifyConnectFailure(ex);
		String headline = kind == ObsIssueKind.AUTH_FAILED ? "hud.reccheck.auth_failed" : "hud.reccheck.disconnected";
		String detail = switch (kind) {
			case OBS_NOT_RUNNING, OBS_UNREACHABLE, WRONG_PORT_OR_HOST -> "hud.reccheck.disconnected.detail.obs";
			case WEBSOCKET_DISABLED -> "hud.reccheck.disconnected.detail.websocket";
			case AUTH_FAILED -> "hud.reccheck.auth_failed.detail";
			case INVALID_CONFIG -> "hud.reccheck.invalid_config.detail";
			default -> "hud.reccheck.disconnected.detail.generic";
		};
		updateSnapshot(kind == ObsIssueKind.AUTH_FAILED
			? ObsConnectionSnapshot.authFailed()
			: ObsConnectionSnapshot.disconnected(kind, headline, detail, reconnecting));
	}

	private void runConnectionTest(ModConfig draft, CompletableFuture<ObsTestResult> future) {
		if (!isValidConfig(draft)) {
			future.complete(ObsTestResult.failure("test.reccheck.invalid_config", "test.reccheck.invalid_config.detail", ObsIssueKind.INVALID_CONFIG, List.of("test.reccheck.hint.host", "test.reccheck.hint.port")));
			return;
		}
		try {
			TestSession session = new TestSession(draft, future);
			session.connect();
		} catch (Exception ex) {
			future.complete(classifyTestFailure(ex));
		}
	}

	private void updateSnapshot(ObsConnectionSnapshot next) {
		ObsConnectionSnapshot previous = snapshot.getAndSet(next);
		if (next.state() == ObsConnectionState.CONNECTED_RECORDING || next.state() == ObsConnectionState.CONNECTED_NOT_RECORDING) {
			reconnectScheduled.set(false);
		}
		if (shouldPlayWarningSound(previous, next)) {
			playWarningSound();
		}
	}

	private boolean shouldPlayWarningSound(ObsConnectionSnapshot previous, ObsConnectionSnapshot next) {
		if (!config.get().notificationSound) {
			return false;
		}
		if (previous == null || previous.state() == next.state()) {
			return false;
		}
		return switch (next.state()) {
			case CONNECTED_NOT_RECORDING, DISCONNECTED, AUTH_FAILED, ERROR -> true;
			default -> false;
		};
	}

	private void playWarningSound() {
		long now = System.currentTimeMillis();
		if (now - lastWarningSoundState < 1500L) {
			return;
		}
		lastWarningSoundState = now;
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F)));
	}

	private void sendRequest(String requestType, JsonObject requestData, java.util.function.Consumer<ObsTestResult> consumer) {
		WebSocket socket = currentSocket;
		if (socket == null) {
			consumer.accept(ObsTestResult.failure("test.reccheck.disconnected", "test.reccheck.disconnected.detail", ObsIssueKind.OBS_UNREACHABLE, List.of("test.reccheck.hint.obs_running")));
			return;
		}
		JsonObject request = new JsonObject();
		request.addProperty("op", 6);
		JsonObject body = new JsonObject();
		body.addProperty("requestType", requestType);
		body.addProperty("requestId", UUID.randomUUID().toString());
		body.add("requestData", requestData);
		request.add("d", body);
		CompletableFuture<ObsTestResult> command = new CompletableFuture<>();
		pendingCommand = command;
		socket.sendText(request.toString(), true);
		command.whenComplete((result, error) -> {
			if (error == null && result != null) {
				consumer.accept(result);
			}
		});
	}

	private void onIdentifyCompleted(WebSocket socket, ModConfig cfg) {
		JsonObject request = new JsonObject();
		request.addProperty("op", 6);
		JsonObject body = new JsonObject();
		body.addProperty("requestType", "GetRecordStatus");
		body.addProperty("requestId", UUID.randomUUID().toString());
		request.add("d", body);
		socket.sendText(request.toString(), true);
	}

	private void handleMessage(long connectGeneration, WebSocket socket, String message) {
		if (connectGeneration != generation.get()) {
			return;
		}
		if (message == null || message.isBlank()) {
			return;
		}
		JsonObject root = JsonParser.parseString(message).getAsJsonObject();
		int op = root.get("op").getAsInt();
		JsonObject data = root.getAsJsonObject("d");
		switch (op) {
			case 0 -> handleHello(socket, data);
		case 2 -> {
			onIdentifyCompleted(socket, config.get());
		}
			case 5 -> handleEvent(data);
			case 7 -> handleRequestResponse(data);
			default -> updateSnapshot(ObsConnectionSnapshot.error("hud.reccheck.error", "hud.reccheck.error.detail.protocol", ObsIssueKind.PROTOCOL_ERROR));
		}
	}

	private void handleHello(WebSocket socket, JsonObject data) {
		ModConfig cfg = config.get();
		try {
			JsonObject identify = new JsonObject();
			identify.addProperty("op", 1);
			JsonObject identifyData = new JsonObject();
			identifyData.addProperty("rpcVersion", data.get("rpcVersion").getAsInt());
			identifyData.addProperty("eventSubscriptions", 1 | 2 | 4 | 8 | 16 | 32 | 64 | 128 | 256 | 512 | 1024 | 4096);
			if (data.has("authentication")) {
				JsonObject auth = data.getAsJsonObject("authentication");
				identifyData.addProperty("authentication", buildAuth(auth.get("salt").getAsString(), auth.get("challenge").getAsString(), cfg.obsPassword));
			}
			identify.add("d", identifyData);
			socket.sendText(identify.toString(), true);
		} catch (Exception ex) {
			updateSnapshot(ObsConnectionSnapshot.error("hud.reccheck.error", "hud.reccheck.error.detail.protocol", ObsIssueKind.PROTOCOL_ERROR));
			socket.abort();
		}
	}

	private void handleEvent(JsonObject data) {
		String eventType = data.get("eventType").getAsString();
		if (!"RecordStateChanged".equals(eventType)) {
			return;
		}
		JsonObject eventData = data.getAsJsonObject("eventData");
		boolean outputActive = eventData.get("outputActive").getAsBoolean();
		updateSnapshot(ObsConnectionSnapshot.recording(outputActive));
	}

	private void handleRequestResponse(JsonObject data) {
		JsonObject requestStatus = data.getAsJsonObject("requestStatus");
		boolean success = requestStatus.get("result").getAsBoolean();
		String requestType = data.get("requestType").getAsString();
		String requestId = data.has("requestId") ? data.get("requestId").getAsString() : "";
		CompletableFuture<ObsTestResult> pending = requestId.isBlank() ? null : pendingRequests.remove(requestId);
		if (!success) {
			ObsIssueKind kind = ObsIssueKind.UNKNOWN;
			String detail = requestStatus.has("comment") ? requestStatus.get("comment").getAsString() : "";
			if (pending != null) {
				pending.complete(ObsTestResult.failure("test.reccheck.failed", detail.isBlank() ? "test.reccheck.failed.detail" : detail, kind, hintsFor(kind)));
			} else {
				updateSnapshot(ObsConnectionSnapshot.error("hud.reccheck.error", "hud.reccheck.error.detail.request", kind));
			}
			return;
		}
		if ("GetRecordStatus".equals(requestType)) {
			JsonObject responseData = data.has("responseData") ? data.getAsJsonObject("responseData") : new JsonObject();
			boolean outputActive = responseData.has("outputActive") && responseData.get("outputActive").getAsBoolean();
			updateSnapshot(ObsConnectionSnapshot.recording(outputActive));
			if (pending != null) {
				pending.complete(ObsTestResult.success());
			}
			return;
		}
		if ("StartRecord".equals(requestType) && pending != null) {
			pending.complete(ObsTestResult.success());
		}
	}

	private static List<String> hintsFor(ObsIssueKind kind) {
		return switch (kind) {
			case AUTH_FAILED -> List.of("test.reccheck.hint.password");
			case OBS_NOT_RUNNING, OBS_UNREACHABLE, WRONG_PORT_OR_HOST -> List.of("test.reccheck.hint.obs_running", "test.reccheck.hint.port", "test.reccheck.hint.localhost");
			case WEBSOCKET_DISABLED -> List.of("test.reccheck.hint.websocket");
			default -> List.of("test.reccheck.hint.retry");
		};
	}

	private void failPendingRequests(ObsTestResult failure) {
		pendingRequests.forEach((id, future) -> future.complete(failure));
		pendingRequests.clear();
	}

	private void handleConnectFailure(long connectGeneration, boolean reconnecting, Throwable throwable) {
		ObsIssueKind kind = classifyConnectFailure(throwable);
		String headline = kind == ObsIssueKind.AUTH_FAILED ? "hud.reccheck.auth_failed" : "hud.reccheck.disconnected";
		String detail = switch (kind) {
			case OBS_NOT_RUNNING, OBS_UNREACHABLE, WRONG_PORT_OR_HOST -> "hud.reccheck.disconnected.detail.obs";
			case WEBSOCKET_DISABLED -> "hud.reccheck.disconnected.detail.websocket";
			case AUTH_FAILED -> "hud.reccheck.auth_failed.detail";
			case INVALID_CONFIG -> "hud.reccheck.invalid_config.detail";
			default -> "hud.reccheck.disconnected.detail.generic";
		};
		updateSnapshot(kind == ObsIssueKind.AUTH_FAILED
			? ObsConnectionSnapshot.authFailed()
			: ObsConnectionSnapshot.disconnected(kind, headline, detail, reconnecting));
	}

	private ObsIssueKind classifyConnectFailure(Throwable throwable) {
		Throwable cause = throwable;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof UnknownHostException) {
			return ObsIssueKind.WRONG_PORT_OR_HOST;
		}
		if (cause instanceof ConnectException) {
			return ObsIssueKind.OBS_NOT_RUNNING;
		}
		String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
		if (message.contains("auth") || message.contains("authentication")) {
			return ObsIssueKind.AUTH_FAILED;
		}
		if (message.contains("refused") || message.contains("timeout")) {
			return ObsIssueKind.OBS_UNREACHABLE;
		}
		return ObsIssueKind.UNKNOWN;
	}

	private ObsTestResult classifyTestFailure(Throwable throwable) {
		ObsIssueKind kind = classifyConnectFailure(throwable);
		return switch (kind) {
			case AUTH_FAILED -> ObsTestResult.failure("test.reccheck.auth_failed", "test.reccheck.auth_failed.detail", kind, hintsFor(kind));
			case OBS_NOT_RUNNING, OBS_UNREACHABLE, WRONG_PORT_OR_HOST -> ObsTestResult.failure("test.reccheck.disconnected", "test.reccheck.disconnected.detail", kind, hintsFor(kind));
			case INVALID_CONFIG -> ObsTestResult.failure("test.reccheck.invalid_config", "test.reccheck.invalid_config.detail", kind, hintsFor(kind));
			default -> ObsTestResult.failure("test.reccheck.failed", "test.reccheck.failed.detail", kind, hintsFor(kind));
		};
	}

	private boolean isValidConfig(ModConfig cfg) {
		return cfg != null && cfg.obsPort >= 1 && cfg.obsPort <= 65535 && cfg.obsHost != null && !cfg.obsHost.isBlank();
	}

	private URI buildUri(ModConfig cfg) {
		String host = cfg.obsHost.contains(":") && !cfg.obsHost.startsWith("[") ? "[" + cfg.obsHost + "]" : cfg.obsHost;
		return URI.create("ws://" + host + ":" + cfg.obsPort);
	}

	private static String buildAuth(String salt, String challenge, String password) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		String secret = Base64.getEncoder().encodeToString(digest.digest((password + salt).getBytes(StandardCharsets.UTF_8)));
		return Base64.getEncoder().encodeToString(digest.digest((secret + challenge).getBytes(StandardCharsets.UTF_8)));
	}

	private static void showClientMessage(Minecraft client, Component message) {
		client.execute(() -> {
			if (client.player != null) {
				client.player.displayClientMessage(message, false);
			}
		});
	}

	private final class ObsListener implements WebSocket.Listener {
		private final long connectGeneration;
		private final ModConfig cfg;
		private final StringBuilder buffer = new StringBuilder();

		private ObsListener(long connectGeneration, ModConfig cfg) {
			this.connectGeneration = connectGeneration;
			this.cfg = cfg;
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			currentSocket = webSocket;
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				handleMessage(connectGeneration, webSocket, buffer.toString());
				buffer.setLength(0);
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			if (connectGeneration == generation.get()) {
				currentSocket = null;
				reconnectScheduled.set(false);
				if (statusCode == 4009) {
					updateSnapshot(ObsConnectionSnapshot.authFailed());
				} else {
					handleConnectFailure(connectGeneration, true, new IOException("OBS closed websocket: " + statusCode + " " + reason));
					if (cfg.autoReconnect) {
						scheduleConnect(5000L, true, false);
					}
				}
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			if (connectGeneration != generation.get()) {
				return;
			}
			currentSocket = null;
			reconnectScheduled.set(false);
			handleConnectFailure(connectGeneration, true, error);
			if (cfg.autoReconnect) {
				scheduleConnect(5000L, true, false);
			}
		}
	}

	private final class TestSession implements WebSocket.Listener {
		private final ModConfig draft;
		private final CompletableFuture<ObsTestResult> future;
		private final StringBuilder buffer = new StringBuilder();
		private WebSocket socket;
		private final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);

		private TestSession(ModConfig draft, CompletableFuture<ObsTestResult> future) {
			this.draft = draft;
			this.future = future;
		}

		void connect() {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			executor.schedule(() -> {
				if (done.compareAndSet(false, true) && !future.isDone()) {
					future.complete(ObsTestResult.failure("test.reccheck.failed", "test.reccheck.failed.detail", ObsIssueKind.UNKNOWN, List.of("test.reccheck.hint.retry")));
					if (socket != null) {
						socket.abort();
					}
				}
			}, 6, TimeUnit.SECONDS);
			client.newWebSocketBuilder().subprotocols("obswebsocket.json").buildAsync(buildUri(draft), this).join();
		}

		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			buffer.append(data);
			if (last) {
				try {
					JsonObject root = JsonParser.parseString(buffer.toString()).getAsJsonObject();
					int op = root.get("op").getAsInt();
					JsonObject msg = root.getAsJsonObject("d");
					switch (op) {
						case 0 -> handleTestHello(webSocket, msg);
						case 2 -> sendTestGetRecordStatus(webSocket);
						case 7 -> handleTestResponse(msg);
					}
				} catch (Exception ex) {
					future.complete(classifyTestFailure(ex));
				}
				buffer.setLength(0);
			}
			webSocket.request(1);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			if (done.compareAndSet(false, true) && !future.isDone()) {
				future.complete(classifyTestFailure(new IOException("OBS closed websocket: " + statusCode + " " + reason)));
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			if (done.compareAndSet(false, true) && !future.isDone()) {
				future.complete(classifyTestFailure(error));
			}
		}

		private void handleTestHello(WebSocket webSocket, JsonObject data) {
			try {
				JsonObject identify = new JsonObject();
				identify.addProperty("op", 1);
				JsonObject identifyData = new JsonObject();
				identifyData.addProperty("rpcVersion", data.get("rpcVersion").getAsInt());
				identifyData.addProperty("eventSubscriptions", 0);
				if (data.has("authentication")) {
					JsonObject auth = data.getAsJsonObject("authentication");
					identifyData.addProperty("authentication", buildAuth(auth.get("salt").getAsString(), auth.get("challenge").getAsString(), draft.obsPassword));
				}
				identify.add("d", identifyData);
				webSocket.sendText(identify.toString(), true);
			} catch (Exception ex) {
				future.complete(classifyTestFailure(ex));
			}
		}

		private void sendTestGetRecordStatus(WebSocket webSocket) {
			JsonObject request = new JsonObject();
			request.addProperty("op", 6);
			JsonObject body = new JsonObject();
			body.addProperty("requestType", "GetRecordStatus");
			body.addProperty("requestId", UUID.randomUUID().toString());
			request.add("d", body);
			webSocket.sendText(request.toString(), true);
		}

		private void handleTestResponse(JsonObject data) {
			JsonObject status = data.getAsJsonObject("requestStatus");
			if (!status.get("result").getAsBoolean()) {
				if (done.compareAndSet(false, true)) {
					future.complete(classifyTestFailure(new IOException(status.has("comment") ? status.get("comment").getAsString() : "request failed")));
				}
				return;
			}
			if (done.compareAndSet(false, true)) {
				future.complete(ObsTestResult.success());
				if (socket != null) {
					socket.abort();
				}
			}
		}
	}
}
