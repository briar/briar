package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class BackoffFactoryImpl implements BackoffFactory {

	@Override
	public Backoff createBackoff(EventBus eventBus, TransportId transportId,
			int minInterval, int maxInterval, double base) {
		return new BackoffImpl(eventBus, transportId, minInterval, maxInterval,
				base);
	}
}
