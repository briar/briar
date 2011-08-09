package net.sf.briar.api.transport;

import net.sf.briar.api.ContactId;

/**
 * Maintains a transport plugin's connection reordering window and decides
 * whether incoming connections should be accepted or rejected.
 */
public interface ConnectionRecogniser {

	/**
	 * Returns the ID of the contact who created the tag if the connection
	 * should be accepted, or null if the connection should be rejected.
	 */
	ContactId acceptConnection(byte[] tag);
}
