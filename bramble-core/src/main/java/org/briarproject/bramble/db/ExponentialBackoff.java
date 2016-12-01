package org.briarproject.bramble.db;

class ExponentialBackoff {

	/**
	 * Returns the expiry time of a packet transmitted at time <tt>now</tt>
	 * over a transport with maximum latency <tt>maxLatency</tt>, where the
	 * packet has previously been transmitted <tt>txCount</tt> times. All times
	 * are in milliseconds. The expiry time is
	 * <tt>now + maxLatency * 2 ^ (txCount + 1)</tt>, so the interval between
	 * transmissions increases exponentially. If the expiry time would
	 * be greater than Long.MAX_VALUE, Long.MAX_VALUE is returned.
	 */
	static long calculateExpiry(long now, int maxLatency, int txCount) {
		if (now < 0) throw new IllegalArgumentException();
		if (maxLatency <= 0) throw new IllegalArgumentException();
		if (txCount < 0) throw new IllegalArgumentException();
		// The maximum round-trip time is twice the maximum latency
		long roundTrip = maxLatency * 2L;
		// The interval between transmissions is roundTrip * 2 ^ txCount
		for (int i = 0; i < txCount; i++) {
			roundTrip <<= 1;
			if (roundTrip < 0) return Long.MAX_VALUE;
		}
		// The expiry time is the current time plus the interval
		long expiry = now + roundTrip;
		return expiry < 0 ? Long.MAX_VALUE : expiry;
	}
}
