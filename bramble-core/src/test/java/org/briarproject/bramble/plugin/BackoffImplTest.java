package org.briarproject.bramble.plugin;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackoffImplTest extends BrambleTestCase {

	private static final int MIN_INTERVAL = 60 * 1000;
	private static final int MAX_INTERVAL = 60 * 60 * 1000;
	private static final double BASE = 1.2;

	@Test
	public void testPollingIntervalStartsAtMinimum() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE);
		assertEquals(MIN_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testIncrementIncreasesPollingInterval() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		assertTrue(b.getPollingInterval() > MIN_INTERVAL);
	}

	@Test
	public void testResetResetsPollingInterval() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		b.increment();
		b.reset();
		assertEquals(MIN_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testBaseAffectsBackoffSpeed() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE);
		b.increment();
		int interval = b.getPollingInterval();
		BackoffImpl b1 = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE * 2);
		b1.increment();
		int interval1 = b1.getPollingInterval();
		assertTrue(interval < interval1);
	}

	@Test
	public void testIntervalDoesNotExceedMaxInterval() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL, BASE);
		for (int i = 0; i < 100; i++) b.increment();
		assertEquals(MAX_INTERVAL, b.getPollingInterval());
	}

	@Test
	public void testIntervalDoesNotExceedMaxIntervalWithInfiniteMultiplier() {
		BackoffImpl b = new BackoffImpl(MIN_INTERVAL, MAX_INTERVAL,
				Double.POSITIVE_INFINITY);
		b.increment();
		assertEquals(MAX_INTERVAL, b.getPollingInterval());
	}
}

