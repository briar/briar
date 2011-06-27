package net.sf.briar.api.protocol;

/** A bundle of acknowledgements, subscriptions, and batches of messages. */
public interface Bundle {

	/** Prepares the bundle for transmission and generates its identifier. */
	public void seal();

	/**
	 * Returns the bundle's unique identifier. Cannot be called before seal().
	 */
	BundleId getId();

	/** Returns the bundle's capacity in bytes. */
	long getCapacity();

	/** Returns the bundle's size in bytes. */
	long getSize();

	/** Returns the acknowledgements contained in the bundle. */
	Iterable<BatchId> getAcks();

	/** Adds an acknowledgement to the bundle. Cannot be called after seal(). */
	void addAck(BatchId b);

	/** Returns the subscriptions contained in the bundle. */
	Iterable<GroupId> getSubscriptions();

	/** Adds a subscription to the bundle. Cannot be called after seal(). */
	void addSubscription(GroupId g);

	/** Returns the batches of messages contained in the bundle. */
	Iterable<Batch> getBatches();

	/**
	 * Adds a batch of messages to the bundle. Cannot be called after seal().
	 */
	void addBatch(Batch b);
}
