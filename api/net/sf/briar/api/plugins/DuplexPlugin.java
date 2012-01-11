package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;

/**  An interface for transport plugins that support duplex communication. */
public interface DuplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a connection to the given contact using
	 * the current transport and configuration properties. Returns null if a
	 * connection could not be created.
	 */
	DuplexTransportConnection createConnection(ContactId c);

	/**
	 * Starts the invitation process from the inviter's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	DuplexTransportConnection sendInvitation(int code, long timeout);

	/**
	 * Starts the invitation process from the invitee's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	DuplexTransportConnection acceptInvitation(int code, long timeout);
}
