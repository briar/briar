package net.sf.briar.api.clock;

/** Default clock implementation. */
public class SystemClock implements Clock {

	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
