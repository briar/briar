package org.briarproject.clients;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.MessageTracker;
import org.briarproject.api.clients.MessageTracker.GroupCount;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.ConversationManager.ConversationClient;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

@NotNullByDefault
public abstract class ConversationClientImpl extends BdfIncomingMessageHook
		implements ConversationClient {

	protected final MessageTracker messageTracker;

	protected ConversationClientImpl(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser,
			MessageTracker messageTracker) {
		super(db, clientHelper, metadataParser);
		this.messageTracker = messageTracker;
	}

	@Override
	public GroupCount getGroupCount(Transaction txn, ContactId contactId)
			throws DbException {
		Contact contact = db.getContact(txn, contactId);
		GroupId groupId = getContactGroup(contact).getId();
		return messageTracker.getGroupCount(txn, groupId);
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		messageTracker.setReadFlag(g, m, read);
	}
}
