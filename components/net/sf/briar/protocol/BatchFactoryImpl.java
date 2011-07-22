package net.sf.briar.protocol;

import java.util.Collection;

import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;

class BatchFactoryImpl implements BatchFactory {

	public Batch createBatch(BatchId id, Collection<Message> messages) {
		return new BatchImpl(id, messages);
	}
}
