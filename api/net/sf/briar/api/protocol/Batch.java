package net.sf.briar.api.protocol;

/** A batch of messages up to MAX_SIZE bytes in total size. */
public interface Batch {

	public static final int MAX_SIZE = 1024 * 1024;

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the size of the serialised batch in bytes. */
	long getSize();

	/** Returns the messages contained in the batch. */
	Iterable<Message> getMessages();

	/** Returns the sender's signature over the contents of the batch. */
	byte[] getSignature();
}