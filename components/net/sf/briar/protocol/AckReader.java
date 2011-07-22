package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

public class AckReader implements ObjectReader<Ack> {

	private final AckFactory ackFactory;

	AckReader(AckFactory ackFactory) {
		this.ackFactory = ackFactory;
	}

	public Ack readObject(Reader r) throws IOException {
		// Initialise the consumer
		CountingConsumer counting = new CountingConsumer(Ack.MAX_SIZE);
		// Read and digest the data
		r.addConsumer(counting);
		r.readUserDefinedTag(Tags.ACK);
		r.addObjectReader(Tags.BATCH_ID, new BatchIdReader());
		Collection<BatchId> batches = r.readList(BatchId.class);
		r.removeObjectReader(Tags.BATCH_ID);
		r.removeConsumer(counting);
		// Build and return the ack
		return ackFactory.createAck(batches);
	}
}
