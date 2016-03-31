package org.briarproject.api.keyagreement;

import org.briarproject.api.TransportId;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

public class KeyAgreementConnection {
	private final DuplexTransportConnection conn;
	private final TransportId id;

	public KeyAgreementConnection(DuplexTransportConnection conn,
			TransportId id) {
		this.conn = conn;
		this.id = id;
	}

	public DuplexTransportConnection getConnection() {
		return conn;
	}

	public TransportId getTransportId() {
		return id;
	}
}
