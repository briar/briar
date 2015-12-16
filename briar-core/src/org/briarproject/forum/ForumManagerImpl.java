package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageHeader;
import org.briarproject.api.sync.MessageId;

import java.util.Collection;

class ForumManagerImpl implements ForumManager {

	private final DatabaseComponent db;

	@Inject
	ForumManagerImpl(DatabaseComponent db) {
		this.db = db;
	}

	@Override
	public boolean addGroup(Group g) throws DbException {
		return db.addGroup(g);
	}

	@Override
	public void addLocalMessage(Message m) throws DbException {
		db.addLocalMessage(m);
	}

	@Override
	public Collection<Group> getAvailableGroups() throws DbException {
		return db.getAvailableGroups();
	}

	@Override
	public Group getGroup(GroupId g) throws DbException {
		return db.getGroup(g);
	}

	@Override
	public Collection<Group> getGroups() throws DbException {
		return db.getGroups();
	}

	@Override
	public byte[] getMessageBody(MessageId m) throws DbException {
		return db.getMessageBody(m);
	}

	@Override
	public Collection<MessageHeader> getMessageHeaders(GroupId g)
			throws DbException {
		return db.getMessageHeaders(g);
	}

	@Override
	public Collection<Contact> getSubscribers(GroupId g) throws DbException {
		return db.getSubscribers(g);
	}

	@Override
	public Collection<ContactId> getVisibility(GroupId g) throws DbException {
		return db.getVisibility(g);
	}

	@Override
	public void removeGroup(Group g) throws DbException {
		db.removeGroup(g);
	}

	@Override
	public void setReadFlag(MessageId m, boolean read) throws DbException {
		db.setReadFlag(m, read);
	}

	@Override
	public void setVisibility(GroupId g, Collection<ContactId> visible)
			throws DbException {
		db.setVisibility(g, visible);
	}

	@Override
	public void setVisibleToAll(GroupId g, boolean all) throws DbException {
		db.setVisibleToAll(g, all);
	}
}
