package net.sf.briar.clock;

/** Default clock implementation. */
public class SystemClock implements Clock {

	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}
}
