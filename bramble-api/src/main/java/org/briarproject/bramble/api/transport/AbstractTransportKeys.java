package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

/**
 * Abstract superclass for {@link TransportKeys} and
 * {@link StaticTransportKeys}.
 */
@Immutable
@NotNullByDefault
public abstract class AbstractTransportKeys {

	private final TransportId transportId;
	private final IncomingKeys inPrev, inCurr, inNext;
	private final OutgoingKeys outCurr;

	AbstractTransportKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr) {
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
}
