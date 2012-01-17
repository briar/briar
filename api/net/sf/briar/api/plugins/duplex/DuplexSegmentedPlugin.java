package net.sf.briar.api.plugins.duplex;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.Plugin;

/**
 * An interface for transport plugins that support duplex segmented
 * communication.
 */
public interface DuplexSegmentedPlugin extends Plugin {

	/**
	 * Attempts to create and return a connection to the given contact using
	 * the current transport and configuration properties. Returns null if a
	 * connection could not be created.
	 */
	DuplexSegmentedTransportConnection createConnection(ContactId c);

	/**
	 * Starts the invitation process from the inviter's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	DuplexSegmentedTransportConnection sendInvitation(int code, long timeout);

	/**
	 * Starts the invitation process from the invitee's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	DuplexSegmentedTransportConnection acceptInvitation(int code, long timeout);
}
