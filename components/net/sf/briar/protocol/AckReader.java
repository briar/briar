package net.sf.briar.protocol;

import java.io.IOException;
import java.util.Collection;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class AckReader implements ObjectReader<Ack> {

	private final AckFactory ackFactory;
	private final ObjectReader<BatchId> batchIdReader;

	AckReader(AckFactory ackFactory) {
		this.ackFactory = ackFactory;
		batchIdReader = new BatchIdReader();
	}

	public Ack readObject(Reader r) throws IOException {
		// Initialise the consumer
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		// Read and digest the data
		r.addConsumer(counting);
		r.readStructId(Types.ACK);
		r.addObjectReader(Types.BATCH_ID, batchIdReader);
		Collection<BatchId> batches = r.readList(BatchId.class);
		r.removeObjectReader(Types.BATCH_ID);
		r.removeConsumer(counting);
		// Build and return the ack
		return ackFactory.createAck(batches);
	}

	private static class BatchIdReader implements ObjectReader<BatchId> {

		public BatchId readObject(Reader r) throws IOException {
			r.readStructId(Types.BATCH_ID);
			byte[] b = r.readBytes(UniqueId.LENGTH);
			if(b.length != UniqueId.LENGTH) throw new FormatException();
			return new BatchId(b);
		}
	}
}
