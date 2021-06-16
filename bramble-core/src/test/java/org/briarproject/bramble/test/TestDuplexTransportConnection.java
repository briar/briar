package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class TestDuplexTransportConnection
		implements DuplexTransportConnection {

	private final TransportConnectionReader reader;
	private final TransportConnectionWriter writer;

	@SuppressWarnings("WeakerAccess")
	public TestDuplexTransportConnection(InputStream in, OutputStream out) {
		reader = new TestTransportConnectionReader(in);
		writer = new TestTransportConnectionWriter(out, false);
	}

	@Override
	public TransportConnectionReader getReader() {
		return reader;
	}

	@Override
	public TransportConnectionWriter getWriter() {
		return writer;
	}

	@Override
	public TransportProperties getRemoteProperties() {
		return new TransportProperties();
	}

	/**
	 * Creates and returns a pair of TestDuplexTransportConnections that are
	 * connected to each other.
	 */
	public static TestDuplexTransportConnection[] createPair()
			throws IOException {
		// Use 64k buffers to prevent deadlock
		PipedInputStream aliceIn = new PipedInputStream(1 << 16);
		PipedInputStream bobIn = new PipedInputStream(1 << 16);
		PipedOutputStream aliceOut = new PipedOutputStream(bobIn);
		PipedOutputStream bobOut = new PipedOutputStream(aliceIn);
		TestDuplexTransportConnection alice =
				new TestDuplexTransportConnection(aliceIn, aliceOut);
		TestDuplexTransportConnection bob =
				new TestDuplexTransportConnection(bobIn, bobOut);
		return new TestDuplexTransportConnection[] {alice, bob};
	}
}
