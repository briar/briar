package org.briarproject.bramble.api.plugin;

import org.briarproject.bramble.api.event.EventBus;

public interface BackoffFactory {

	Backoff createBackoff(EventBus eventBus, TransportId transportId,
			int minInterval, int maxInterval, double base);
}
