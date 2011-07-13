package net.sf.briar.api.protocol;

import java.util.Map;
import java.util.Set;

/** A bundle header up to MAX_SIZE bytes in total size. */
public interface Header {

	static final int MAX_SIZE = 1024 * 1024;

	// FIXME: Remove BundleId when refactoring is complete
	BundleId getId();

	/** Returns the acknowledgements contained in the header. */
	Set<BatchId> getAcks();

	/** Returns the subscriptions contained in the header. */
	Set<GroupId> getSubscriptions();

	/** Returns the transport details contained in the header. */
	Map<String, String> getTransports();
}
