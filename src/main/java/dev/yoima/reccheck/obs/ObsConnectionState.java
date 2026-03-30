package dev.yoima.reccheck.obs;

public enum ObsConnectionState {
	DISCONNECTED,
	CONNECTING,
	AUTH_FAILED,
	CONNECTED_NOT_RECORDING,
	CONNECTED_RECORDING,
	ERROR
}
