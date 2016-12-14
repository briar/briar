package org.briarproject.bramble.db;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExponentialBackoffTest extends BrambleTestCase {

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
	public void testTransmissionCountOverflow() {
		int maxLatency = Integer.MAX_VALUE; // RTT will not overflow
		long expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 0);
		assertEquals(Integer.MAX_VALUE * 2L, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 31);
		assertEquals(Integer.MAX_VALUE * (2L << 31), expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 32);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
		expiry = ExponentialBackoff.calculateExpiry(0, maxLatency, 33);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
	}

	@Test
	public void testCurrentTimeOverflow() {
		int maxLatency = Integer.MAX_VALUE; // RTT will not overflow
		long now = Long.MAX_VALUE - (Integer.MAX_VALUE * (2L << 31));
		long expiry = ExponentialBackoff.calculateExpiry(now, maxLatency, 0);
		assertEquals(now + Integer.MAX_VALUE * 2L, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(now - 1, maxLatency, 31);
		assertEquals(Long.MAX_VALUE - 1, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(now, maxLatency, 31);
		assertEquals(Long.MAX_VALUE, expiry); // No overflow
		expiry = ExponentialBackoff.calculateExpiry(now + 1, maxLatency, 32);
		assertEquals(Long.MAX_VALUE, expiry); // Overflow caught
	}
}
