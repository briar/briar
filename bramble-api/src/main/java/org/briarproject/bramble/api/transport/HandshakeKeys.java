package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

/**
 * Keys for handshaking with a given contact or pending contact over a given
 * transport. Unlike {@link TransportKeys} these keys do not provide forward
 * secrecy.
 */
@Immutable
@NotNullByDefault
public class HandshakeKeys extends AbstractTransportKeys {

	private final SecretKey rootKey;
	private final boolean alice;

	public HandshakeKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr,
			SecretKey rootKey, boolean alice) {
		super(transportId, inPrev, inCurr, inNext, outCurr);
		this.rootKey = rootKey;
		this.alice = alice;
	}

	public SecretKey getRootKey() {
		return rootKey;
	}

	public boolean isAlice() {
		return alice;
	}
}
