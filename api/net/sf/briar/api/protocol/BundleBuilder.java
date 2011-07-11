package net.sf.briar.api.protocol;

public interface BundleBuilder {

	/** Returns the bundle's capacity in bytes. */
	long getCapacity();

	/** Adds an acknowledgement to the bundle. */
	void addAck(BatchId b);

	/** Adds a subscription to the bundle. */
	void addSubscription(GroupId g);

	/** Adds a transport detail to the bundle. */
	void addTransport(String key, String value);

	/** Adds a batch of messages to the bundle. */
	void addBatch(Batch b);

	/** Builds and returns the bundle. */
	Bundle build();
}
