package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.sync.event.CloseSyncConnectionsEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.SettableClock;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.briarproject.bramble.plugin.bluetooth.BluetoothConnectionLimiter.MIN_ATTEMPT_INTERVAL_MS;
import static org.briarproject.bramble.plugin.bluetooth.BluetoothConnectionLimiter.STABILITY_PERIOD_MS;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BluetoothConnectionLimiterImplTest extends BrambleMockTestCase {

	private final EventBus eventBus = context.mock(EventBus.class);

	private final DuplexTransportConnection conn1 =
			context.mock(DuplexTransportConnection.class, "conn1");
	private final DuplexTransportConnection conn2 =
			context.mock(DuplexTransportConnection.class, "conn2");
	private final DuplexTransportConnection conn3 =
			context.mock(DuplexTransportConnection.class, "conn3");

	private final long now = System.currentTimeMillis();

	private AtomicLong time;
	private BluetoothConnectionLimiter limiter;

	@Before
	public void setUp() {
		time = new AtomicLong(now);
		Clock clock = new SettableClock(time);
		limiter = new BluetoothConnectionLimiterImpl(eventBus, clock);
	}

	@Test
	public void testLimiterDoesNotAllowContactConnectionsDuringKeyAgreement() {
		assertTrue(limiter.canOpenContactConnection());

		expectCloseSyncConnectionsEvent();
		limiter.keyAgreementStarted();

		assertFalse(limiter.canOpenContactConnection());

		limiter.keyAgreementEnded();

		assertTrue(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterAllowsAttemptToRaiseLimitAtStartup() {
		// First outgoing connection is allowed - we're below the limit of 1
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn1, false));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit to 2
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn2, false));

		// Third outgoing connection is not allowed - we're above the limit of 1
		assertFalse(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterAllowsThirdConnectionAfterFirstTwoAreClosed() {
		// First outgoing connection is allowed - we're below the limit of 1
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn1, false));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit to 2
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn2, false));

		// Third outgoing connection is not allowed - we're above the limit of 1
		assertFalse(limiter.canOpenContactConnection());

		// Close the first connection
		limiter.connectionClosed(conn1, false);

		// Third outgoing connection is not allowed - we're at the limit of 1
		assertFalse(limiter.canOpenContactConnection());

		// Close the second connection
		limiter.connectionClosed(conn2, false);

		// Third outgoing connection is allowed - we're below the limit of 1
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn3, false));
	}

	@Test
	public void testLimiterRaisesLimitWhenConnectionsAreStable() {
		// First outgoing connection is allowed - we're below the limit of 1
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn1, false));

		// Second outgoing connection is allowed - it's time to try raising
		// the limit to 2
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn2, false));

		// Third outgoing connection is not allowed - we're above the limit of 1
		assertFalse(limiter.canOpenContactConnection());

		// Time passes
		time.set(now + STABILITY_PERIOD_MS);

		// Third outgoing connection is still not allowed - first two are now
		// stable so limit is raised to 2, but we're already at the new limit
		assertFalse(limiter.canOpenContactConnection());

		// Time passes
		time.set(now + MIN_ATTEMPT_INTERVAL_MS);

		// Third outgoing connection is allowed - it's time to try raising
		// the limit to 3
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn3, false));

		// Fourth outgoing connection is not allowed - we're above the limit
		// of 2
		assertFalse(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterIncreasesIntervalWhenConnectionFailsAboveLimit() {
		// First outgoing connection is allowed - we're below the limit of 1
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn1, false));

		// Time passes
		time.set(now + 1);

		// Second outgoing connection is allowed - it's time to try raising
		// the limit to 2
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn2, false));

		// Time passes - the first connection is stable, the second isn't
		time.set(now + STABILITY_PERIOD_MS);

		// First connection fails. The second connection isn't stable yet, so
		// the limiter considers this a failed attempt and doubles the interval
		// between attempts
		limiter.connectionClosed(conn1, true);

		// Third outgoing connection is not allowed - we're still at the limit
		// of 1
		assertFalse(limiter.canOpenContactConnection());

		// Time passes - nearly time for the second attempt
		time.set(now + MIN_ATTEMPT_INTERVAL_MS * 2);

		// Third outgoing connection is not allowed - we're still at the limit
		// of 1
		assertFalse(limiter.canOpenContactConnection());

		// Time passes - now it's time for the second attempt
		time.set(now + 1 + MIN_ATTEMPT_INTERVAL_MS * 2);

		// Third outgoing connection is allowed - it's time to try raising the
		// limit to 2 again
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn3, false));
	}

	private void expectCloseSyncConnectionsEvent() {
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(
					CloseSyncConnectionsEvent.class)));
		}});
	}
}
