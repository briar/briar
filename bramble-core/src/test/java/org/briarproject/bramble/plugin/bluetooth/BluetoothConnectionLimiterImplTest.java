package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import static org.briarproject.bramble.plugin.bluetooth.BluetoothConnectionLimiter.STABILITY_PERIOD_MS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BluetoothConnectionLimiterImplTest extends BrambleMockTestCase {

	private final EventBus eventBus = context.mock(EventBus.class);
	private final Clock clock = context.mock(Clock.class);

	private final DuplexTransportConnection conn1 =
			context.mock(DuplexTransportConnection.class, "conn1");
	private final DuplexTransportConnection conn2 =
			context.mock(DuplexTransportConnection.class, "conn2");
	private final DuplexTransportConnection conn3 =
			context.mock(DuplexTransportConnection.class, "conn3");
	private final TransportConnectionReader reader =
			context.mock(TransportConnectionReader.class);
	private final TransportConnectionWriter writer =
			context.mock(TransportConnectionWriter.class);

	private final long now = System.currentTimeMillis();

	private BluetoothConnectionLimiter limiter;

	@Before
	public void setUp() {
		limiter = new BluetoothConnectionLimiterImpl(eventBus, clock);
	}

	@Test
	public void testLimiterAllowsAttemptToRaiseLimitAtStartup()
			throws Exception {
		// First outgoing connection is allowed - we're below the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn1));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn2));

		// Third outgoing connection is not allowed - we're above the limit
		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// Third incoming connection is not allowed - we're above the limit
		expectGetCurrentTime(now);
		expectCloseConnection(conn3);
		assertFalse(limiter.contactConnectionOpened(conn3));
	}

	@Test
	public void testLimiterAllowsThirdConnectionAfterFirstTwoAreClosed() {
		// First outgoing connection is allowed - we're below the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn1));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn2));

		// Third outgoing connection is not allowed - we're above the limit
		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// Close the first connection
		limiter.connectionClosed(conn1, false);

		// Third outgoing connection is not allowed - we're at the limit
		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// Close the second connection
		limiter.connectionClosed(conn2, false);

		// Third outgoing connection is allowed - we're below the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn3));
	}

	@Test
	public void testLimiterRaisesLimitWhenConnectionsAreStable() {
		// First outgoing connection is allowed - we're below the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn1));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn2));

		// Third outgoing connection is not allowed - we're above the limit
		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// Third outgoing connection is still not allowed - first two are
		// stable but we're still at the limit
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertFalse(limiter.canOpenContactConnection());

		// Close the first connection
		limiter.connectionClosed(conn1, false);

		// Third outgoing connection is allowed - we're below the new limit
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertTrue(limiter.canOpenContactConnection());
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertTrue(limiter.contactConnectionOpened(conn3));

		// Fourth outgoing connection is not allowed
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertFalse(limiter.canOpenContactConnection());
	}

	private void expectGetCurrentTime(long now) {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
		}});
	}

	private void expectCloseConnection(DuplexTransportConnection conn)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(conn).getReader();
			will(returnValue(reader));
			oneOf(reader).dispose(false, false);
			oneOf(conn).getWriter();
			will(returnValue(writer));
			oneOf(writer).dispose(false);
		}});
	}
}
