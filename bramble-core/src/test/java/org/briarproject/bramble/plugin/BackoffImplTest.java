package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.PollingIntervalDecreasedEvent;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackoffImplTest extends BrambleMockTestCase {

	private final EventBus eventBus = context.mock(EventBus.class);

	private final TransportId transportId = getTransportId();
	private static final int MIN_INTERVAL = 60 * 1000;
	private static final int MAX_INTERVAL = 60 * 60 * 1000;
	private static final double BASE = 1.2;

	@Test
	public void testPollingIntervalStartsAtMinimum() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, BASE);
		assertEquals(MIN_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testIncrementMethodIncreasesPollingInterval() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		assertTrue(b.getPollingInterval() > MIN_INTERVAL);
	}

	@Test
	public void testResetMethodResetsPollingInterval() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		assertTrue(b.getPollingInterval() > MIN_INTERVAL);

		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(
					PollingIntervalDecreasedEvent.class)));
		}});

		b.reset();
		assertEquals(MIN_INTERVAL, b.getPollingInterval());
		context.assertIsSatisfied();

		// Resetting again should not broadcast another event
		b.reset();
		assertEquals(MIN_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testBaseAffectsBackoffSpeed() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		int interval = b.getPollingInterval();
		BackoffImpl b1 = new BackoffImpl(eventBus, transportId, MIN_INTERVAL,
				MAX_INTERVAL, BASE * 2);
		b1.increment();
		int interval1 = b1.getPollingInterval();
		assertTrue(interval < interval1);
	}

	@Test
	public void testIntervalDoesNotExceedMaxInterval() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, BASE);
		for (int i = 0; i < 100; i++) b.increment();
		assertEquals(MAX_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testIntervalDoesNotExceedMaxIntervalWithInfiniteMultiplier() {
		BackoffImpl b = new BackoffImpl(eventBus, transportId,
				MIN_INTERVAL, MAX_INTERVAL, Double.POSITIVE_INFINITY);
		b.increment();
		assertEquals(MAX_INTERVAL, b.getPollingInterval());
	}
}

