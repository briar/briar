package net.sf.briar.protocol;

import java.io.IOException;
import java.util.List;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UnverifiedBatch;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.DigestingConsumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

class BatchReader implements ObjectReader<UnverifiedBatch> {

	private final MessageDigest messageDigest;
	private final ObjectReader<UnverifiedMessage> messageReader;
	private final UnverifiedBatchFactory batchFactory;

	BatchReader(CryptoComponent crypto,
			ObjectReader<UnverifiedMessage> messageReader,
			UnverifiedBatchFactory batchFactory) {
		messageDigest = crypto.getMessageDigest();
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public UnverifiedBatch readObject(Reader r) throws IOException {
		// Initialise the consumers
		Consumer counting =
			new CountingConsumer(ProtocolConstants.MAX_PACKET_LENGTH);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(counting);
		r.addConsumer(digesting);
		r.readStructId(Types.BATCH);
		r.addObjectReader(Types.MESSAGE, messageReader);
		List<UnverifiedMessage> messages = r.readList(UnverifiedMessage.class);
		r.removeObjectReader(Types.MESSAGE);
		r.removeConsumer(digesting);
		r.removeConsumer(counting);
		// Build and return the batch
		BatchId id = new BatchId(messageDigest.digest());
		return batchFactory.createUnverifiedBatch(id, messages);
	}
}
