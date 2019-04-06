package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

/**
 * Keys for communicating with a given contact over a given transport.
 */
@Immutable
@NotNullByDefault
public class TransportKeys {

	private final TransportId transportId;
	private final IncomingKeys inPrev, inCurr, inNext;
	private final OutgoingKeys outCurr;

	public TransportKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr) {
		if (inPrev.getRotationPeriod() != outCurr.getRotationPeriod() - 1)
			throw new IllegalArgumentException();
		if (inCurr.getRotationPeriod() != outCurr.getRotationPeriod())
			throw new IllegalArgumentException();
		if (inNext.getRotationPeriod() != outCurr.getRotationPeriod() + 1)
			throw new IllegalArgumentException();
		this.transportId = transportId;
		this.inPrev = inPrev;
		this.inCurr = inCurr;
		this.inNext = inNext;
		this.outCurr = outCurr;
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

	public long getRotationPeriod() {
		return outCurr.getRotationPeriod();
	}
}
