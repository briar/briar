package net.sf.briar.protocol;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.Batch;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.Message;
import net.sf.briar.api.protocol.Tags;
import net.sf.briar.api.serial.Consumer;
import net.sf.briar.api.serial.ObjectReader;
import net.sf.briar.api.serial.Reader;

import com.google.inject.Inject;

class BatchReader implements ObjectReader<Batch> {

	private final MessageDigest messageDigest;
	private final ObjectReader<Message> messageReader;
	private final BatchFactory batchFactory;

	@Inject
	BatchReader(CryptoComponent crypto, ObjectReader<Message> messageReader,
			BatchFactory batchFactory) {
		messageDigest = crypto.getMessageDigest();
		this.messageReader = messageReader;
		this.batchFactory = batchFactory;
	}

	public Batch readObject(Reader r) throws IOException {
		// Initialise the consumers
		Consumer counting = new CountingConsumer(Batch.MAX_SIZE);
		DigestingConsumer digesting = new DigestingConsumer(messageDigest);
		messageDigest.reset();
		// Read and digest the data
		r.addConsumer(counting);
		r.addConsumer(digesting);
		r.readUserDefinedTag(Tags.BATCH);
		r.addObjectReader(Tags.MESSAGE, messageReader);
		List<Message> messages = r.readList(Message.class);
		r.removeObjectReader(Tags.MESSAGE);
		r.removeConsumer(digesting);
		r.removeConsumer(counting);
		// Build and return the batch
		BatchId id = new BatchId(messageDigest.digest());
		return batchFactory.createBatch(id, messages);
	}
}
