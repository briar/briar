package org.briarproject.android.contact;

import org.briarproject.api.contact.Contact;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * This class is a singleton that defines the data that should persist, i.e.
 * still be present in memory after activity restarts. This class is not thread
 * safe.
 */
public class ConversationPersistentData {

	private volatile GroupId groupId;
	private volatile Contact contact;
	private volatile boolean connected;
	private volatile List<ConversationItem> items = new ArrayList<>();

	public void clearAll() {
		groupId = null;
		contact = null;
		connected = false;
		items.clear();
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
	}

	public Contact getContact() {
		return contact;
	}

	public void setContact(Contact contact) {
		this.contact = contact;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public void addConversationItems(Collection<ConversationItem> items) {
		this.items.addAll(items);
	}

	public List<ConversationItem> getConversationItems() {
		return Collections.unmodifiableList(items);
	}
}
