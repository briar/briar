package org.briarproject.api.plugins.duplex;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.Plugin;

/** An interface for transport plugins that support duplex communication. */
public interface DuplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a connection to the given contact using
	 * the current transport and configuration properties. Returns null if a
	 * connection could not be created.
	 */
	DuplexTransportConnection createConnection(ContactId c);

	/** Returns true if the plugin supports exchanging invitations. */
	boolean supportsInvitations();

	/**
	 * Attempts to create and return an invitation connection to the remote
	 * peer. Returns null if no connection can be established within the given
	 * time.
	 */
	DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice);
}
