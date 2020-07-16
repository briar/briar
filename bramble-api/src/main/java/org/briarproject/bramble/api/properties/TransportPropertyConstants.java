package org.briarproject.bramble.api.properties;

public interface TransportPropertyConstants {

	/**
	 * The maximum number of properties per transport.
	 */
	int MAX_PROPERTIES_PER_TRANSPORT = 100;

	/**
	 * The maximum length of a property's key or value in UTF-8 bytes.
	 */
	int MAX_PROPERTY_LENGTH = 100;

	/**
	 * Message metadata key for the transport ID of a local or remote update,
	 * as a BDF string.
	 */
	String MSG_KEY_TRANSPORT_ID = "transportId";

	/**
	 * Message metadata key for the version number of a local or remote update,
	 * as a BDF long.
	 */
	String MSG_KEY_VERSION = "version";

	/**
	 * Message metadata key for whether an update is local or remote, as a BDF
	 * boolean.
	 */
	String MSG_KEY_LOCAL = "local";

	/**
	 * Group metadata key for any discovered transport properties of the
	 * contact, as a BDF dictionary.
	 */
	String GROUP_KEY_DISCOVERED = "discovered";

	/**
	 * Group metadata key for the contact's ID as a BDF long.
	 */
	String GROUP_KEY_CONTACT_ID = "contactId";

	/**
	 * Group metadata key for the local group, indicating that contact IDs have
	 * been stored in the group metadata of contact groups.
	 */
	String GROUP_KEY_CONTACT_IDS_STORED = "contactIdsStored";
}
