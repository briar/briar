package net.sf.briar.transport;

import java.util.Collection;

import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.api.transport.ConnectionWindowFactory;

class ConnectionWindowFactoryImpl implements ConnectionWindowFactory {

	public ConnectionWindow createConnectionWindow() {
		return new ConnectionWindowImpl();
	}

	public ConnectionWindow createConnectionWindow(Collection<Long> unseen) {
		return new ConnectionWindowImpl(unseen);
	}
}
