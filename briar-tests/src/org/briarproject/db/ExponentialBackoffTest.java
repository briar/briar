package org.briarproject.db;

import org.briarproject.BriarTestCase;

import org.junit.Test;

public class ExponentialBackoffTest extends BriarTestCase {

	private static final int ONE_HOUR = 60 * 60 * 1000;

	@Test
	public void testFirstIntervalEqualsRoundTripTime() {
		long first = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 0);
		assertEquals(2 * ONE_HOUR, first);
	}

	@Test
	public void testIntervalsIncreaseExponentially() {
		long first = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 0);
		long second = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 1);
		long third = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 2);
		long fourth = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 3);
		assertEquals(third, fourth / 2);
		assertEquals(second, third / 2);
		assertEquals(first, second / 2);
	}

	@Test
	public void testIntervalIsAddedToCurrentTime() {
		long now = System.currentTimeMillis();
		long fromZero = ExponentialBackoff.calculateExpiry(0, ONE_HOUR, 0);
		long fromNow = ExponentialBackoff.calculateExpiry(now, ONE_HOUR, 0);
		assertEquals(now, fromNow - fromZero);
	}

	@Test
	public void testRoundTripTimeOverflow() {
		long maxLatency = Long.MAX_VALUE / 2 + 1; // RTT will overflow
		long expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 0);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
	}

	@Test
	public void testTransmissionCountOverflow() {
		long maxLatency = (Long.MAX_VALUE - 1) / 2; // RTT will not overflow
		long expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 0);
		assertEquals(Long.MAX_VALUE - 1, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 1);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
		expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 2);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
	}

	@Test
	public void testCurrentTimeOverflow() {
		long maxLatency = (Long.MAX_VALUE - 1) / 2; // RTT will not overflow
		long expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 0);
		assertEquals(Long.MAX_VALUE - 1, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(1, maxLatency, 0);
		assertEquals(Long.MAX_VALUE, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(2, maxLatency, 0);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
	}
}
