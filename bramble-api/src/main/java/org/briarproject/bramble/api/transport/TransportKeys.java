package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Keys for communicating with a given contact or pending contact over a given
 * transport.
 */
@Immutable
@NotNullByDefault
public class TransportKeys {

	private final TransportId transportId;
	private final IncomingKeys inPrev, inCurr, inNext;
	private final OutgoingKeys outCurr;
	@Nullable
	private final SecretKey rootKey;
	private final boolean alice;

	/**
	 * Constructor for rotation mode.
	 */
	public TransportKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr) {
		this(transportId, inPrev, inCurr, inNext, outCurr, null, false);
	}

	/**
	 * Constructor for handshake mode.
	 */
	public TransportKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr,
			@Nullable SecretKey rootKey, boolean alice) {
		if (inPrev.getTimePeriod() != outCurr.getTimePeriod() - 1)
			throw new IllegalArgumentException();
		if (inCurr.getTimePeriod() != outCurr.getTimePeriod())
			throw new IllegalArgumentException();
		if (inNext.getTimePeriod() != outCurr.getTimePeriod() + 1)
			throw new IllegalArgumentException();
		this.transportId = transportId;
		this.inPrev = inPrev;
		this.inCurr = inCurr;
		this.inNext = inNext;
		this.outCurr = outCurr;
		this.rootKey = rootKey;
		this.alice = alice;
	}

	public TransportId getTransportId() {
		return transportId;
	}

	public IncomingKeys getPreviousIncomingKeys() {
		return inPrev;
	}

	public IncomingKeys getCurrentIncomingKeys() {
		return inCurr;
	}

	public IncomingKeys getNextIncomingKeys() {
		return inNext;
	}

	public OutgoingKeys getCurrentOutgoingKeys() {
		return outCurr;
	}

	public long getTimePeriod() {
		return outCurr.getTimePeriod();
	}

	/**
	 * Returns true if these keys are for use in handshake mode or false if
	 * they're for use in rotation mode.
	 */
	public boolean isHandshakeMode() {
		return rootKey != null;
	}

	/**
	 * If these keys are for use in handshake mode, returns the root key.
	 *
	 * @throws UnsupportedOperationException If these keys are for use in
	 * rotation mode
	 */
	public SecretKey getRootKey() {
		if (rootKey == null) throw new UnsupportedOperationException();
		return rootKey;
	}

	/**
	 * If these keys are for use in handshake mode, returns true if the local
	 * party is Alice.
	 *
	 * @throws UnsupportedOperationException If these keys are for use in
	 * rotation mode
	 */
	public boolean isAlice() {
		if (rootKey == null) throw new UnsupportedOperationException();
		return alice;
	}
}
