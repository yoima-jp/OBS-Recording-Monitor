package dev.yoima.reccheck.obs;

import java.util.List;

public record ObsTestResult(
	boolean passed,
	String headlineKey,
	String detailKey,
	ObsIssueKind issueKind,
	List<String> hints
) {
	public static ObsTestResult success() {
		return new ObsTestResult(true, "test.reccheck.success", "test.reccheck.success.detail", ObsIssueKind.NONE, List.of());
	}

	public static ObsTestResult failure(String headlineKey, String detailKey, ObsIssueKind issueKind, List<String> hints) {
		return new ObsTestResult(false, headlineKey, detailKey, issueKind, hints);
	}
}
