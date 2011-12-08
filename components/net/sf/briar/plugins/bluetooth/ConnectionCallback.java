package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.microedition.io.StreamConnection;

class ConnectionCallback {

	private static final Logger LOG =
		Logger.getLogger(ConnectionCallback.class.getName());

	private final String uuid;
	private final long timeout;
	private final long end;

	private StreamConnection connection = null; // Locking: this

	ConnectionCallback(String uuid, long timeout) {
		this.uuid = uuid;
		this.timeout = timeout;
		end = System.currentTimeMillis() + timeout;
	}

	String getUuid() {
		return uuid;
	}

	long getTimeout() {
		return timeout;
	}

	synchronized StreamConnection waitForConnection()
	throws InterruptedException {
		long now = System.currentTimeMillis();
		while(connection == null && now < end) {
			wait(end - now);
			now = System.currentTimeMillis();
		}
		return connection;
	}

	synchronized void addConnection(StreamConnection s) {
		if(connection == null) {
			connection = s;
			notifyAll();
		} else {
			// Redundant connection
			try {
				s.close();
			} catch(IOException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}
}
