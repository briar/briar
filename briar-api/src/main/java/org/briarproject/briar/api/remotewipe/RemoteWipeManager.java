package org.briarproject.briar.api.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.util.Collection;
import java.util.List;

@NotNullByDefault
public interface RemoteWipeManager extends ConversationManager.ConversationClient {

	interface Observer {
		void onPanic();
	}

	/**
	 * The unique ID of the remote wipe client.
	 */
	ClientId CLIENT_ID = new ClientId("pw.darkcrystal.remotewipe");

	/**
	 * The current major version of the remote wipe client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the remote wipe client.
	 */
	int MINOR_VERSION = 0;

	void listenForPanic(Observer observer);

	void setup(Transaction txn, List<ContactId> wipers)
			throws DbException, FormatException;

	void wipe(Transaction txn, Contact contact)
			throws DbException, FormatException;

	boolean amWiper(Transaction txn, ContactId contactId);

	boolean remoteWipeIsSetup(Transaction txn);

	List<Author> getWipers(Transaction txn) throws DbException;

	@Override
	Collection<ConversationMessageHeader> getMessageHeaders(
			Transaction txn, ContactId contactId) throws DbException;

}
