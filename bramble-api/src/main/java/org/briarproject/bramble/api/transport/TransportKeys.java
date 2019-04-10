package org.briarproject.bramble.api.transport;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;

import javax.annotation.concurrent.Immutable;

/**
 * Keys for communicating with a given contact over a given transport. Unlike
 * {@link StaticTransportKeys}, these keys provide forward secrecy.
 */
@Immutable
@NotNullByDefault
public class TransportKeys extends AbstractTransportKeys {

	public TransportKeys(TransportId transportId, IncomingKeys inPrev,
			IncomingKeys inCurr, IncomingKeys inNext, OutgoingKeys outCurr) {
		super(transportId, inPrev, inCurr, inNext, outCurr);
	}
}
