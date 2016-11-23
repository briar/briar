package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

public class KeyAgreementResult {

	private final SecretKey masterKey;
	private final DuplexTransportConnection connection;
	private final TransportId transportId;
	private final boolean alice;

	public KeyAgreementResult(SecretKey masterKey,
			DuplexTransportConnection connection, TransportId transportId,
			boolean alice) {
		this.masterKey = masterKey;
		this.connection = connection;
		this.transportId = transportId;
		this.alice = alice;
	}

	public SecretKey getMasterKey() {
		return masterKey;
	}

	public DuplexTransportConnection getConnection() {
		return connection;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public boolean wasAlice() {
		return alice;
	}
}
