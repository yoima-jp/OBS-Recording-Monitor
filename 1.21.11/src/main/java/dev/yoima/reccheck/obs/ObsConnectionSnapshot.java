package dev.yoima.reccheck.obs;

public record ObsConnectionSnapshot(
	ObsConnectionState state,
	ObsIssueKind issueKind,
	String headlineKey,
	String detailKey,
	boolean recording,
	boolean reconnecting,
	long updatedAt
) {
	public static ObsConnectionSnapshot disconnected(ObsIssueKind kind, String headlineKey, String detailKey, boolean reconnecting) {
		return new ObsConnectionSnapshot(ObsConnectionState.DISCONNECTED, kind, headlineKey, detailKey, false, reconnecting, System.currentTimeMillis());
	}

	public static ObsConnectionSnapshot connecting(String headlineKey, String detailKey, boolean reconnecting) {
		return new ObsConnectionSnapshot(ObsConnectionState.CONNECTING, ObsIssueKind.NONE, headlineKey, detailKey, false, reconnecting, System.currentTimeMillis());
	}

	public static ObsConnectionSnapshot recording(boolean recording) {
		return new ObsConnectionSnapshot(recording ? ObsConnectionState.CONNECTED_RECORDING : ObsConnectionState.CONNECTED_NOT_RECORDING,
			ObsIssueKind.NONE,
			recording ? "hud.reccheck.recording" : "hud.reccheck.not_recording",
			recording ? "hud.reccheck.recording.detail" : "hud.reccheck.not_recording.detail",
			recording,
			false,
			System.currentTimeMillis());
	}

	public static ObsConnectionSnapshot authFailed() {
		return new ObsConnectionSnapshot(ObsConnectionState.AUTH_FAILED, ObsIssueKind.AUTH_FAILED, "hud.reccheck.auth_failed", "hud.reccheck.auth_failed.detail", false, false, System.currentTimeMillis());
	}

	public static ObsConnectionSnapshot error(String headlineKey, String detailKey, ObsIssueKind kind) {
		return new ObsConnectionSnapshot(ObsConnectionState.ERROR, kind, headlineKey, detailKey, false, false, System.currentTimeMillis());
	}
}
