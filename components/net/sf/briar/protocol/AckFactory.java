package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;

interface AckFactory {

	Ack createAck(Collection<BatchId> batches);
}
