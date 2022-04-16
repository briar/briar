package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.PollingIntervalDecreasedEvent;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
class BackoffImpl implements Backoff {

	private final EventBus eventBus;
	private final TransportId transportId;
	private final int minInterval, maxInterval;
	private final double base;
	private final AtomicInteger backoff;

	BackoffImpl(EventBus eventBus, TransportId transportId,
			int minInterval, int maxInterval, double base) {
		this.eventBus = eventBus;
		this.transportId = transportId;
		this.minInterval = minInterval;
		this.maxInterval = maxInterval;
		this.base = base;
		backoff = new AtomicInteger(0);
	}

	@Override
	public int getPollingInterval() {
		double multiplier = Math.pow(base, backoff.get());
		// Large or infinite values will be rounded to Integer.MAX_VALUE
		int interval = (int) (minInterval * multiplier);
		return Math.min(interval, maxInterval);
	}

	@Override
	public void increment() {
		backoff.incrementAndGet();
	}

	@Override
	public void reset() {
		int old = backoff.getAndSet(0);
		if (old > 0) {
			eventBus.broadcast(new PollingIntervalDecreasedEvent(transportId));
		}
	}
}
