package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

@NotNullByDefault
public interface MailboxUpdateManager {

	/**
	 * The unique ID of the mailbox update (properties) client.
	 */
	ClientId CLIENT_ID =
			new ClientId("org.briarproject.bramble.mailbox.properties");

	/**
	 * The current major version of the mailbox update (properties) client.
	 */
	int MAJOR_VERSION = 2;

	/**
	 * The current minor version of the mailbox update (properties) client.
	 */
	int MINOR_VERSION = 0;

	/**
	 * The number of properties required for an update message with a mailbox.
	 * <p>
	 * The required properties are {@link #PROP_KEY_ONION},
	 * {@link #PROP_KEY_AUTHTOKEN}, {@link #PROP_KEY_INBOXID} and
	 * {@link #PROP_KEY_OUTBOXID}.
	 */
	int PROP_COUNT = 4;

	/**
	 * The onion address of the mailbox, excluding the .onion suffix.
	 */
	String PROP_KEY_ONION = "onion";

	/**
	 * A bearer token for accessing the mailbox (64 hex digits).
	 */
	String PROP_KEY_AUTHTOKEN = "authToken";

	/**
	 * A folder ID for downloading messages (64 hex digits).
	 */
	String PROP_KEY_INBOXID = "inboxId";

	/**
	 * A folder ID for uploading messages (64 hex digits).
	 */
	String PROP_KEY_OUTBOXID = "outboxId";

	/**
	 * Length of the {@link #PROP_KEY_ONION} property.
	 */
	int PROP_ONION_LENGTH = 56;

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
	 * Key in the client's local group for storing the clientSupports list that
	 * was last sent out.
	 */
	String GROUP_KEY_SENT_CLIENT_SUPPORTS = "sentClientSupports";

	/**
	 * Key in the client's local group for storing the serverSupports list that
	 * was last sent out, if any.
	 */
	String GROUP_KEY_SENT_SERVER_SUPPORTS = "sentServerSupports";

	/**
	 * Returns the latest {@link MailboxUpdate} sent to the given contact.
	 * <p>
	 * If we have our own mailbox then the update will be a
	 * {@link MailboxUpdateWithMailbox} containing the
	 * {@link MailboxProperties} the contact should use for communicating with
	 * our mailbox.
	 */
	MailboxUpdate getLocalUpdate(Transaction txn, ContactId c)
			throws DbException;

	/**
	 * Returns the latest {@link MailboxUpdate} received from the given
	 * contact, or null if no update has been received.
	 * <p>
	 * If the contact has a mailbox then the update will be a
	 * {@link MailboxUpdateWithMailbox} containing the
	 * {@link MailboxProperties} we should use for communicating with the
	 * contact's mailbox.
	 */
	@Nullable
	MailboxUpdate getRemoteUpdate(Transaction txn, ContactId c)
			throws DbException;
}
