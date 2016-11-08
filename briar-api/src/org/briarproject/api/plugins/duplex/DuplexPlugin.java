package org.briarproject.api.plugins.duplex;

import org.briarproject.api.contact.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.keyagreement.KeyAgreementListener;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.plugins.Plugin;

import javax.annotation.Nullable;

/**
 * An interface for transport plugins that support duplex communication.
 */
@NotNullByDefault
public interface DuplexPlugin extends Plugin {

	/**
	 * Attempts to create and return a connection to the given contact using
	 * the current transport and configuration properties. Returns null if a
	 * connection cannot be created.
	 */
	@Nullable
	DuplexTransportConnection createConnection(ContactId c);

	/**
	 * Returns true if the plugin supports exchanging invitations.
	 */
	boolean supportsInvitations();

	/**
	 * Attempts to create and return an invitation connection to the remote
	 * peer. Returns null if no connection can be established within the given
	 * time.
	 */
	@Nullable
	DuplexTransportConnection createInvitationConnection(PseudoRandom r,
			long timeout, boolean alice);

	/**
	 * Returns true if the plugin supports short-range key agreement.
	 */
	boolean supportsKeyAgreement();

	/**
	 * Attempts to create and return a listener that can be used to perform key
	 * agreement. Returns null if a listener cannot be created.
	 */
	@Nullable
	KeyAgreementListener createKeyAgreementListener(byte[] localCommitment);

	/**
	 * Attempts to connect to the remote peer specified in the given descriptor.
	 * Returns null if no connection can be established within the given time.
	 */
	@Nullable
	DuplexTransportConnection createKeyAgreementConnection(
			byte[] remoteCommitment, BdfList descriptor, long timeout);
}
