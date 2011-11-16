package net.sf.briar.transport;

import java.util.Map;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionWindow;
import net.sf.briar.api.transport.ConnectionWindowFactory;

import com.google.inject.Inject;

class ConnectionWindowFactoryImpl implements ConnectionWindowFactory {

	private final CryptoComponent crypto;

	@Inject
	ConnectionWindowFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public ConnectionWindow createConnectionWindow(TransportIndex i,
			byte[] secret) {
		return new ConnectionWindowImpl(crypto, i, secret);
	}

	public ConnectionWindow createConnectionWindow(TransportIndex i,
			Map<Long, byte[]> unseen) {
		return new ConnectionWindowImpl(crypto, i, unseen);
	}
}
