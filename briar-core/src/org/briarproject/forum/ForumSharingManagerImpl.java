package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.ValidationManager.ValidationHook;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ForumSharingManagerImpl implements ForumSharingManager, AddContactHook,
		RemoveContactHook, ValidationHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"cd11a5d04dccd9e2931d6fc3df456313"
					+ "63bb3e9d9d0e9405fccdb051f41f5449"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private final DatabaseComponent db;
	private final ForumManager forumManager;
	private final ClientHelper clientHelper;
	private final GroupFactory groupFactory;
	private final PrivateGroupFactory privateGroupFactory;
	private final SecureRandom random;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	ForumSharingManagerImpl(DatabaseComponent db, ForumManager forumManager,
			ClientHelper clientHelper, GroupFactory groupFactory,
			PrivateGroupFactory privateGroupFactory, SecureRandom random,
			Clock clock) {
		this.db = db;
		this.forumManager = forumManager;
		this.clientHelper = clientHelper;
		this.groupFactory = groupFactory;
		this.privateGroupFactory = privateGroupFactory;
		this.random = random;
		this.clock = clock;
		localGroup = groupFactory.createGroup(CLIENT_ID,
				LOCAL_GROUP_DESCRIPTOR);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(c);
			// Store the group and share it with the contact
			db.addGroup(txn, g);
			db.setVisibleToContact(txn, c.getId(), g.getId(), true);
			// Attach the contact ID to the group
			BdfDictionary meta = new BdfDictionary();
			meta.put("contactId", c.getId().getInt());
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
			// Share any forums that are shared with all contacts
			List<Forum> shared = getForumsSharedWithAllContacts(txn);
			storeMessage(txn, g.getId(), shared, 0);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void validatingMessage(Transaction txn, Message m, ClientId c,
			Metadata meta) throws DbException {
		if (c.equals(CLIENT_ID)) {
			try {
				ContactId contactId = getContactId(txn, m.getGroupId());
				setForumVisibility(txn, contactId, getVisibleForums(txn, m));
			} catch (FormatException e) {
				throw new DbException(e);
			}
		}
	}

	@Override
	public ClientId getClientId() {
		return CLIENT_ID;
	}

	@Override
	public Forum createForum(String name) {
		int length = StringUtils.toUtf8(name).length;
		if (length == 0) throw new IllegalArgumentException();
		if (length > MAX_FORUM_NAME_LENGTH)
			throw new IllegalArgumentException();
		byte[] salt = new byte[FORUM_SALT_LENGTH];
		random.nextBytes(salt);
		return createForum(name, salt);
	}

	@Override
	public void addForum(Forum f) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			db.addGroup(txn, f.getGroup());
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void removeForum(Forum f) throws DbException {
		try {
			// Update the list shared with each contact
			Transaction txn = db.startTransaction();
			try {
				for (Contact c : db.getContacts(txn))
					removeFromList(txn, getContactGroup(c).getId(), f);
				db.removeGroup(txn, f.getGroup());
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Forum> getAvailableForums() throws DbException {
		try {
			Set<Forum> available = new HashSet<Forum>();
			Transaction txn = db.startTransaction();
			try {
				// Get any forums we subscribe to
				Set<Group> subscribed = new HashSet<Group>(db.getGroups(txn,
						forumManager.getClientId()));
				// Get all forums shared by contacts
				for (Contact c : db.getContacts(txn)) {
					Group g = getContactGroup(c);
					// Find the latest update version
					LatestUpdate latest = findLatest(txn, g.getId(), false);
					if (latest != null) {
						// Retrieve and parse the latest update
						BdfList message = clientHelper.getMessageAsList(txn,
								latest.messageId);
						for (Forum f : parseForumList(message)) {
							if (!subscribed.contains(f.getGroup()))
								available.add(f);
						}
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableSet(available);
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<Contact> getSharedBy(GroupId g) throws DbException {
		try {
			List<Contact> subscribers = new ArrayList<Contact>();
			Transaction txn = db.startTransaction();
			try {
				for (Contact c : db.getContacts(txn)) {
					if (listContains(txn, getContactGroup(c).getId(), g, false))
						subscribers.add(c);
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableList(subscribers);
		} catch (IOException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<ContactId> getSharedWith(GroupId g) throws DbException {
		try {
			List<ContactId> shared = new ArrayList<ContactId>();
			Transaction txn = db.startTransaction();
			try {
				for (Contact c : db.getContacts(txn)) {
					if (listContains(txn, getContactGroup(c).getId(), g, true))
						shared.add(c.getId());
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableList(shared);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void setSharedWith(GroupId g, Collection<ContactId> shared)
			throws DbException {
		try {
			Transaction txn = db.startTransaction();
			try {
				// Retrieve the forum
				Forum f = parseForum(db.getGroup(txn, g));
				// Remove the forum from the list shared with all contacts
				removeFromList(txn, localGroup.getId(), f);
				// Update the list shared with each contact
				shared = new HashSet<ContactId>(shared);
				for (Contact c : db.getContacts(txn)) {
					Group cg = getContactGroup(c);
					if (shared.contains(c.getId())) {
						if (addToList(txn, cg.getId(), f)) {
							if (listContains(txn, cg.getId(), g, false))
								db.setVisibleToContact(txn, c.getId(), g, true);
						}
					} else {
						removeFromList(txn, cg.getId(), f);
						db.setVisibleToContact(txn, c.getId(), g, false);
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void setSharedWithAll(GroupId g) throws DbException {
		try {
			Transaction txn = db.startTransaction();
			try {
				// Retrieve the forum
				Forum f = parseForum(db.getGroup(txn, g));
				// Add the forum to the list shared with all contacts
				addToList(txn, localGroup.getId(), f);
				// Add the forum to the list shared with each contact
				for (Contact c : db.getContacts(txn)) {
					Group cg = getContactGroup(c);
					if (addToList(txn, cg.getId(), f)) {
						if (listContains(txn, cg.getId(), g, false))
							db.setVisibleToContact(txn, c.getId(), g, true);
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Group getContactGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	private List<Forum> getForumsSharedWithAllContacts(Transaction txn)
			throws DbException, FormatException {
		// Ensure the local group exists
		db.addGroup(txn, localGroup);
		// Find the latest update in the local group
		LatestUpdate latest = findLatest(txn, localGroup.getId(), true);
		if (latest == null) return Collections.emptyList();
		// Retrieve and parse the latest update
		BdfList message = clientHelper.getMessageAsList(txn, latest.messageId);
		return parseForumList(message);
	}

	private LatestUpdate findLatest(Transaction txn, GroupId g, boolean local)
			throws DbException, FormatException {
		LatestUpdate latest = null;
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			if (meta.getBoolean("local") != local) continue;
			long version = meta.getLong("version");
			if (latest == null || version > latest.version)
				latest = new LatestUpdate(e.getKey(), version);
		}
		return latest;
	}

	private List<Forum> parseForumList(BdfList message) throws FormatException {
		// Version, forum list
		BdfList forumList = message.getList(1);
		List<Forum> forums = new ArrayList<Forum>(forumList.size());
		for (int i = 0; i < forumList.size(); i++) {
			// Name, salt
			BdfList forum = forumList.getList(i);
			forums.add(createForum(forum.getString(0), forum.getRaw(1)));
		}
		return forums;
	}

	private void storeMessage(Transaction txn, GroupId g, List<Forum> forums,
			long version) throws DbException {
		try {
			BdfList body = encodeForumList(forums, version);
			long now = clock.currentTimeMillis();
			Message m = clientHelper.createMessage(g, now, body);
			BdfDictionary meta = new BdfDictionary();
			meta.put("version", version);
			meta.put("local", true);
			clientHelper.addLocalMessage(txn, m, CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private BdfList encodeForumList(List<Forum> forums, long version) {
		BdfList forumList = new BdfList();
		for (Forum f : forums)
			forumList.add(BdfList.of(f.getName(), f.getSalt()));
		return BdfList.of(version, forumList);
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				contactGroupId);
		return new ContactId(meta.getLong("contactId").intValue());
	}

	private Set<GroupId> getVisibleForums(Transaction txn,
			Message remoteUpdate) throws DbException, FormatException {
		// Get the latest local update
		LatestUpdate local = findLatest(txn, remoteUpdate.getGroupId(), true);
		// If there's no local update, no forums are visible
		if (local == null) return Collections.emptySet();
		// Intersect the sets of shared forums
		BdfList localMessage = clientHelper.getMessageAsList(txn,
				local.messageId);
		Set<Forum> shared = new HashSet<Forum>(parseForumList(localMessage));
		byte[] raw = remoteUpdate.getRaw();
		BdfList remoteMessage = clientHelper.toList(raw, MESSAGE_HEADER_LENGTH,
				raw.length - MESSAGE_HEADER_LENGTH);
		shared.retainAll(parseForumList(remoteMessage));
		// Forums in the intersection should be visible
		Set<GroupId> visible = new HashSet<GroupId>(shared.size());
		for (Forum f : shared) visible.add(f.getId());
		return visible;
	}

	private void setForumVisibility(Transaction txn, ContactId c,
			Set<GroupId> visible) throws DbException {
		for (Group g : db.getGroups(txn, forumManager.getClientId())) {
			boolean isVisible = db.isVisibleToContact(txn, c, g.getId());
			boolean shouldBeVisible = visible.contains(g.getId());
			if (isVisible && !shouldBeVisible)
				db.setVisibleToContact(txn, c, g.getId(), false);
			else if (!isVisible && shouldBeVisible)
				db.setVisibleToContact(txn, c, g.getId(), true);
		}
	}

	private Forum createForum(String name, byte[] salt) {
		try {
			BdfList forum = BdfList.of(name, salt);
			byte[] descriptor = clientHelper.toByteArray(forum);
			Group g = groupFactory.createGroup(forumManager.getClientId(),
					descriptor);
			return new Forum(g, name, salt);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private Forum parseForum(Group g) throws FormatException {
		byte[] descriptor = g.getDescriptor();
		// Name, salt
		BdfList forum = clientHelper.toList(descriptor, 0, descriptor.length);
		return new Forum(g, forum.getString(0), forum.getRaw(1));
	}

	private boolean listContains(Transaction txn, GroupId g, GroupId forum,
			boolean local) throws DbException, FormatException {
		LatestUpdate latest = findLatest(txn, g, local);
		if (latest == null) return false;
		BdfList message = clientHelper.getMessageAsList(txn, latest.messageId);
		List<Forum> list = parseForumList(message);
		for (Forum f : list) if (f.getId().equals(forum)) return true;
		return false;
	}

	private boolean addToList(Transaction txn, GroupId g, Forum f)
			throws DbException, FormatException {
		LatestUpdate latest = findLatest(txn, g, true);
		if (latest == null) {
			storeMessage(txn, g, Collections.singletonList(f), 0);
			return true;
		}
		BdfList message = clientHelper.getMessageAsList(txn, latest.messageId);
		List<Forum> list = parseForumList(message);
		if (list.contains(f)) return false;
		list.add(f);
		storeMessage(txn, g, list, latest.version + 1);
		return true;
	}

	private void removeFromList(Transaction txn, GroupId g, Forum f)
			throws DbException, FormatException {
		LatestUpdate latest = findLatest(txn, g, true);
		if (latest == null) return;
		BdfList message = clientHelper.getMessageAsList(txn, latest.messageId);
		List<Forum> list = parseForumList(message);
		if (list.remove(f)) storeMessage(txn, g, list, latest.version + 1);
	}

	private static class LatestUpdate {

		private final MessageId messageId;
		private final long version;

		private LatestUpdate(MessageId messageId, long version) {
			this.messageId = messageId;
			this.version = version;
		}
	}
}
