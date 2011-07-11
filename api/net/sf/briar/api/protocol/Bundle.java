package net.sf.briar.api.protocol;

import java.util.Map;

/** A bundle of acknowledgements, subscriptions, and batches of messages. */
public interface Bundle {

	/** Returns the bundle's unique identifier. */
	BundleId getId();

	/** Returns the bundle's capacity in bytes. */
	long getCapacity();

	/** Returns the bundle's size in bytes. */
	long getSize();

	/** Returns the acknowledgements contained in the bundle. */
	Iterable<BatchId> getAcks();

	/** Returns the subscriptions contained in the bundle. */
	Iterable<GroupId> getSubscriptions();

	/** Returns the transport details contained in the bundle. */
	Map<String, String> getTransports();

	/** Returns the batches of messages contained in the bundle. */
	Iterable<Batch> getBatches();
}
