package org.briarproject.privategroup.invitation;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.Client;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataParser;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.messaging.ConversationManager;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.invitation.GroupInvitationItem;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sharing.InvitationMessage;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.clients.ConversationClientImpl;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.invitation.GroupInvitationConstants.CONTACT_ID;

public class GroupInvitationManagerImpl extends ConversationClientImpl
		implements GroupInvitationManager, Client,
		ContactManager.AddContactHook, ContactManager.RemoveContactHook,
		ConversationManager.ConversationClient {

	private static final ClientId CLIENT_ID =
			new ClientId(StringUtils.fromHexString(
					"B55231ABFC4A10666CD93D649B1D7F4F"
							+ "016E65B87BB4C04F4E35613713DBCD13"));

	private final ContactGroupFactory contactGroupFactory;
	private final Group localGroup;

	@Inject
	protected GroupInvitationManagerImpl(DatabaseComponent db,
			ClientHelper clientHelper, MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory) {
		super(db, clientHelper, metadataParser);
		this.contactGroupFactory = contactGroupFactory;
		localGroup = contactGroupFactory.createLocalGroup(getClientId());
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		db.addGroup(txn, localGroup);
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			// Return if we've already set things up for this contact
			if (db.containsGroup(txn, g.getId())) return;
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary meta = new BdfDictionary();
			meta.put(CONTACT_ID, c.getId().getInt());
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		// remove the contact group (all messages will be removed with it)
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	protected Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(getClientId(), c);
	}

	@Override
	protected boolean incomingMessage(Transaction txn, Message m, BdfList body,
			BdfDictionary meta) throws DbException, FormatException {
		return false;
	}

	@Override
	public void sendInvitation(GroupId groupId, ContactId contactId,
			String message) throws DbException {

	}

	@Override
	public void respondToInvitation(PrivateGroup g, Contact c, boolean accept)
			throws DbException {

	}

	@Override
	public void respondToInvitation(SessionId id, boolean accept)
			throws DbException {

	}

	@Override
	public Collection<InvitationMessage> getInvitationMessages(
			ContactId contactId) throws DbException {
		Collection<InvitationMessage> invitations =
				new ArrayList<InvitationMessage>();

		return invitations;
	}

	@Override
	public Collection<GroupInvitationItem> getInvitations() throws DbException {
		Collection<GroupInvitationItem> invitations =
				new ArrayList<GroupInvitationItem>();

		return invitations;
	}

}
