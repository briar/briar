package net.sf.briar.api.protocol;

/** A batch of messages up to MAX_SIZE bytes in total size. */
public interface Batch {

	public static final int MAX_SIZE = 1024 * 1024;

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the messages contained in the batch. */
	Iterable<Message> getMessages();
}