package net.sf.briar.plugins.email;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import javax.microedition.io.StreamConnection;

import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;

class GmailTransportConnectionWriter implements SimplexTransportWriter {

	private static final Logger LOG =
			Logger.getLogger(GmailTransportConnectionWriter.class.getName());
	private final StreamConnection stream;
	private final long capacity = 25 * 1000 * 1000;

	public GmailTransportConnectionWriter(StreamConnection stream) {
		this.stream = stream;
	}

	public long getCapacity() {
		return capacity;
	}

	public OutputStream getOutputStream() throws IOException {
		return stream.openOutputStream();
	}

	public boolean shouldFlush() {
		return false;
	}

	public void dispose(boolean exception) {
		try {
			stream.close();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}
}
