package net.sf.briar.api.protocol;

import java.util.Map;

import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;

/** A packet updating the sender's transport properties. */
public interface TransportUpdate {

	/** The maximum length of a property's key or value in UTF-8 bytes. */
	static final int MAX_KEY_OR_VALUE_LENGTH = 100;

	/** The maximum number of properties per plugin. */
	static final int MAX_PROPERTIES_PER_PLUGIN = 100;

	/** The maximum number of plugins per update. */
	static final int MAX_PLUGINS_PER_UPDATE = 50;

	/** Returns the transport properties contained in the update. */
	Map<TransportId, TransportProperties> getTransports();

	/**
	 * Returns the update's timestamp. Updates that are older than the newest
	 * update received from the same contact must be ignored.
	 */
	long getTimestamp();
}
