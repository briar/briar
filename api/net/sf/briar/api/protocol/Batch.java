package net.sf.briar.api.protocol;

/** A batch of messages up to CAPACITY bytes in total size. */
public interface Batch {

	public static final long CAPACITY = 1024L * 1024L;

	/** Returns the batch's unique identifier. */
	BatchId getId();

	/** Returns the size of the batch in bytes. */
	long getSize();

	/** Returns the messages contained in the batch. */
	Iterable<Message> getMessages();
}