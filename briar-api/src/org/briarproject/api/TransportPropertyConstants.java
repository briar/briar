package org.briarproject.api;

public interface TransportPropertyConstants {

	/**
	 * The maximum length of a string that uniquely identifies a transport
	 * plugin.
	 */
	int MAX_TRANSPORT_ID_LENGTH = 10;

	/** The maximum number of properties per transport. */
	int MAX_PROPERTIES_PER_TRANSPORT = 100;

	/** The maximum length of a property's key or value in UTF-8 bytes. */
	int MAX_PROPERTY_LENGTH = 100;
}
