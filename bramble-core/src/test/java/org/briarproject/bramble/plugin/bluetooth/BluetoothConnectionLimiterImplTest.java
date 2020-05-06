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
	private final DuplexTransportConnection conn =
			context.mock(DuplexTransportConnection.class);
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
	public void testLimiterAllowsOneOutgoingConnection() {
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());

		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterAllowsSecondIncomingConnection() throws Exception {
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());

		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// The limiter allows a second incoming connection
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		// The limiter does not allow a third incoming connection
		expectGetCurrentTime(now);
		expectCloseConnection();
		assertFalse(limiter.contactConnectionOpened(conn));
	}

	@Test
	public void testLimiterAllowsSecondOutgoingConnectionWhenFirstIsStable() {
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());

		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		// The first connection is not yet stable
		expectGetCurrentTime(now + STABILITY_PERIOD_MS - 1);
		assertFalse(limiter.canOpenContactConnection());

		// The first connection is stable, so the limit is raised
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertTrue(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterAllowsThirdIncomingConnectionWhenFirstTwoAreStable()
			throws Exception {
		expectGetCurrentTime(now);
		assertTrue(limiter.canOpenContactConnection());

		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		expectGetCurrentTime(now);
		assertFalse(limiter.canOpenContactConnection());

		// The limiter allows a second incoming connection
		expectGetCurrentTime(now);
		assertTrue(limiter.contactConnectionOpened(conn));

		// The limiter does not allow a third incoming connection
		expectGetCurrentTime(now + STABILITY_PERIOD_MS - 1);
		expectCloseConnection();
		assertFalse(limiter.contactConnectionOpened(conn));

		// The first two connections are stable, so the limit is raised
		expectGetCurrentTime(now + STABILITY_PERIOD_MS);
		assertTrue(limiter.contactConnectionOpened(conn));
	}

	private void expectGetCurrentTime(long now) {
		context.checking(new Expectations() {{
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
		}});
	}

	private void expectCloseConnection() throws Exception {
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
