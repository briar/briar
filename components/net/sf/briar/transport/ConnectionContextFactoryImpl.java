package net.sf.briar.transport;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionContext;
import net.sf.briar.api.transport.ConnectionContextFactory;

import com.google.inject.Inject;

class ConnectionContextFactoryImpl implements ConnectionContextFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionContextFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionContext createConnectionContext(ContactId c,
			TransportIndex i, long connection, byte[] secret) {
		return new ConnectionContextImpl(c, i, connection, secret);
	}

	public ConnectionContext createNextConnectionContext(ContactId c,
			TransportIndex i, long connection, byte[] previousSecret) {
		byte[] secret = crypto.deriveNextSecret(previousSecret, i.getInt(),
				connection);
		return new ConnectionContextImpl(c, i, connection, secret);
	}
}
