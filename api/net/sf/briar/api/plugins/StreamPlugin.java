package net.sf.briar.api.plugins;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.StreamTransportConnection;

/**
 * An interface for transport plugins that support bidirectional, reliable,
 * ordered, timely delivery of data.
 */
public interface StreamPlugin extends Plugin {

	/**
	 * Attempts to create and return a StreamTransportConnection to the given
	 * contact using the current transport and configuration properties.
	 * Returns null if a connection could not be created.
	 */
	StreamTransportConnection createConnection(ContactId c);

	/**
	 * Starts the invitation process from the inviter's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	StreamTransportConnection sendInvitation(int code, long timeout);

	/**
	 * Starts the invitation process from the invitee's side. Returns null if
	 * no connection can be established within the given timeout.
	 */
	StreamTransportConnection acceptInvitation(int code, long timeout);
}
