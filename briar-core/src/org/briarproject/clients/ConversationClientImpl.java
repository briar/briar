package org.briarproject.clients;

import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.messaging.ConversationManager.ConversationClient;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;

public abstract class ConversationClientImpl extends BdfIncomingMessageHook
		implements ConversationClient {

	protected ConversationClientImpl(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser) {
		super(db, clientHelper, metadataParser);
	}

	protected abstract Group getContactGroup(Contact contact);

	@Override
	public GroupCount getGroupCount(Transaction txn, ContactId contactId)
			throws DbException {
		Contact contact = db.getContact(txn, contactId);
		GroupId groupId = getContactGroup(contact).getId();
		return getGroupCount(txn, groupId);
	}

}
