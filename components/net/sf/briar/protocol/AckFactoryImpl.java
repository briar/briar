package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;

class AckFactoryImpl implements AckFactory {

	public Ack createAck(Collection<BatchId> acked) {
		return new AckImpl(acked);
	}
}
