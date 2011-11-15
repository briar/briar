package net.sf.briar.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionContextFactory;

class ConnectionContextFactoryImpl implements ConnectionContextFactory {

	public ConnectionContext createConnectionContext(ContactId c,
			TransportIndex i, long connection) {
		return new ConnectionContextImpl(c, i, connection);
	}
}
