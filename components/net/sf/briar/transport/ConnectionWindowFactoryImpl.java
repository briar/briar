package net.sf.briar.transport;

import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.api.transport.ConnectionWindowFactory;

class ConnectionWindowFactoryImpl implements ConnectionWindowFactory {

	public ConnectionWindow createConnectionWindow(long centre, int bitmap) {
		return new ConnectionWindowImpl(centre, bitmap);
	}
}
