package net.sf.briar.transport;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.transport.ConnectionRecogniser;
import net.sf.briar.api.transport.ConnectionRecogniserFactory;

import com.google.inject.Inject;

class ConnectionRecogniserFactoryImpl implements ConnectionRecogniserFactory {

	private final CryptoComponent crypto;
	private final DatabaseComponent db;

	@Inject
	ConnectionRecogniserFactoryImpl(CryptoComponent crypto,
			DatabaseComponent db) {
		this.crypto = crypto;
		this.db = db;
	}

	public ConnectionRecogniser createConnectionRecogniser(int transportId) {
		return new ConnectionRecogniserImpl(transportId, crypto, db);
	}
}
