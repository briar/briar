package org.briarproject.bramble.api.plugin;

public interface BackoffFactory {

	Backoff createBackoff(int minInterval, int maxInterval,
			double base);
}
