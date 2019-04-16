package org.briarproject.briar.client;

import org.briarproject.bramble.api.client.BdfIncomingMessageHook;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationManager.ConversationClient;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public abstract class ConversationClientImpl extends BdfIncomingMessageHook
		implements ConversationClient {

	protected final MessageTracker messageTracker;
	protected final IdentityManager identityManager;

	protected ConversationClientImpl(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser,
			MessageTracker messageTracker, IdentityManager identityManager) {
		super(db, clientHelper, metadataParser);
		this.messageTracker = messageTracker;
		this.identityManager = identityManager;
	}

	/**
	 * Initializes the group count with zero messages,
	 * but uses the current time as latest message time for sorting.
	 */
	protected void initializeGroupCount(Transaction txn, GroupId g)
			throws DbException {
		messageTracker.initializeGroupCount(txn, g);
	}

	protected AuthorId getLocalAuthorId(Transaction txn) throws DbException {
		return identityManager.getLocalAuthor(txn).getId();
	}

	@Override
	public GroupCount getGroupCount(Transaction txn, ContactId contactId)
			throws DbException {
		Contact contact = db.getContact(txn, contactId);
		AuthorId local = getLocalAuthorId(txn);
		GroupId groupId = getContactGroup(contact, local).getId();
		return messageTracker.getGroupCount(txn, groupId);
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		messageTracker.setReadFlag(g, m, read);
	}
}
