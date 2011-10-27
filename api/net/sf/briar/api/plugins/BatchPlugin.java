package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.BatchTransportReader;
import net.sf.briar.api.transport.BatchTransportWriter;

/**
 * An interface for transport plugins that do not support bidirectional,
 * reliable, ordered, timely delivery of data.
 */
public interface BatchPlugin extends Plugin {

	/**
	 * Attempts to create and return a BatchTransportReader for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a reader could not be created.
	 */
	BatchTransportReader createReader(ContactId c);

	/**
	 * Attempts to create and return a BatchTransportWriter for the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a writer could not be created.
	 */
	BatchTransportWriter createWriter(ContactId c);

	/**
	 * Starts the invitation process from the inviter's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	BatchTransportWriter sendInvitation(int code, long timeout);

	/**
	 * Starts the invitation process from the invitee's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	BatchTransportReader acceptInvitation(int code, long timeout);

	/**
	 * Continues the invitation process from the invitee's side. Returns null
	 * if no connection can be established within the given timeout.
	 */
	BatchTransportWriter sendInvitationResponse(int code, long timeout);

	/**
	 * Continues the invitation process from the inviter's side. Returns null
	 * if no connection can be established within the given timeout.
	 */
	BatchTransportReader acceptInvitationResponse(int code, long timeout);
}
