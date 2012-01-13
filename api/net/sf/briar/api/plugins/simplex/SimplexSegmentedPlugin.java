package net.sf.briar.api.plugins.simplex;

import net.sf.briar.api.ContactId;

/**
 * An interface for transport plugins that support simplex segmented
 * communication.
 */
public interface SimplexSegmentedPlugin {

	/**
	 * Attempts to create and return a reader for the given contact using the
	 * current transport and configuration properties. Returns null if a reader
	 * could not be created.
	 */
	SimplexSegmentedTransportReader createReader(ContactId c);

	/**
	 * Attempts to create and return a writer for the given contact using the
	 * current transport and configuration properties. Returns null if a writer
	 * could not be created.
	 */
	SimplexSegmentedTransportWriter createWriter(ContactId c);

	/**
	 * Starts the invitation process from the inviter's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	SimplexSegmentedTransportWriter sendInvitation(int code, long timeout);

	/**
	 * Starts the invitation process from the invitee's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	SimplexSegmentedTransportReader acceptInvitation(int code, long timeout);

	/**
	 * Continues the invitation process from the invitee's side. Returns null
	 * if no connection can be established within the given timeout.
	 */
	SimplexSegmentedTransportWriter sendInvitationResponse(int code,
			long timeout);

	/**
	 * Continues the invitation process from the inviter's side. Returns null
	 * if no connection can be established within the given timeout.
	 */
	SimplexSegmentedTransportReader acceptInvitationResponse(int code,
			long timeout);
}
