package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BluetoothConnectionLimiterImplTest extends BrambleMockTestCase {

	private final DuplexTransportConnection conn =
			context.mock(DuplexTransportConnection.class);
	private final TransportConnectionReader reader =
			context.mock(TransportConnectionReader.class);
	private final TransportConnectionWriter writer =
			context.mock(TransportConnectionWriter.class);

	@Test
	public void testLimiterAllowsOneOutgoingConnection() {
		BluetoothConnectionLimiter limiter =
				new BluetoothConnectionLimiterImpl();
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn));
		assertFalse(limiter.canOpenContactConnection());
	}

	@Test
	public void testLimiterAllowsSecondIncomingConnection() throws Exception {
		BluetoothConnectionLimiter limiter =
				new BluetoothConnectionLimiterImpl();
		assertTrue(limiter.canOpenContactConnection());
		assertTrue(limiter.contactConnectionOpened(conn));
		assertFalse(limiter.canOpenContactConnection());
		// The limiter allows a second incoming connection
		assertTrue(limiter.contactConnectionOpened(conn));
		// The limiter closes any further incoming connections
		expectCloseConnection();
		assertFalse(limiter.contactConnectionOpened(conn));
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
