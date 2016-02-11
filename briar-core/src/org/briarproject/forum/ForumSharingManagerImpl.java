package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager.AddContactHook;
import org.briarproject.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.data.MetadataParser;
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
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.sync.PrivateGroupFactory;
import org.briarproject.api.sync.ValidationManager.ValidationHook;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
	private final GroupFactory groupFactory;
	private final PrivateGroupFactory privateGroupFactory;
	private final MessageFactory messageFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;
	private final SecureRandom random;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	ForumSharingManagerImpl(DatabaseComponent db,
			ForumManager forumManager, GroupFactory groupFactory,
			PrivateGroupFactory privateGroupFactory,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser, SecureRandom random, Clock clock) {
		this.db = db;
		this.forumManager = forumManager;
		this.groupFactory = groupFactory;
		this.privateGroupFactory = privateGroupFactory;
		this.messageFactory = messageFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
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
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getId().getInt());
			db.mergeGroupMetadata(txn, g.getId(), metadataEncoder.encode(d));
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
						byte[] raw = db.getRawMessage(txn, latest.messageId);
						for (Forum f : parseForumList(raw)) {
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
		return parseForumList(db.getRawMessage(txn, latest.messageId));
	}

	private LatestUpdate findLatest(Transaction txn, GroupId g, boolean local)
			throws DbException, FormatException {
		LatestUpdate latest = null;
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(txn, g);
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			BdfDictionary d = metadataParser.parse(e.getValue());
			if (d.getBoolean("local") != local) continue;
			long version = d.getInteger("version");
			if (latest == null || version > latest.version)
				latest = new LatestUpdate(e.getKey(), version);
		}
		return latest;
	}

	private List<Forum> parseForumList(byte[] raw) throws FormatException {
		List<Forum> forums = new ArrayList<Forum>();
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			r.skipInteger(); // Version
			r.readListStart();
			while (!r.hasListEnd()) {
				r.readListStart();
				String name = r.readString(MAX_FORUM_NAME_LENGTH);
				byte[] salt = r.readRaw(FORUM_SALT_LENGTH);
				r.readListEnd();
				forums.add(createForum(name, salt));
			}
			r.readListEnd();
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return forums;
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	private void storeMessage(Transaction txn, GroupId g, List<Forum> forums,
			long version) throws DbException {
		try {
			byte[] body = encodeForumList(forums, version);
			long now = clock.currentTimeMillis();
			Message m = messageFactory.createMessage(g, now, body);
			BdfDictionary d = new BdfDictionary();
			d.put("version", version);
			d.put("local", true);
			Metadata meta = metadataEncoder.encode(d);
			db.addLocalMessage(txn, m, CLIENT_ID, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] encodeForumList(List<Forum> forums, long version) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeInteger(version);
			w.writeListStart();
			for (Forum f : forums) {
				w.writeListStart();
				w.writeString(f.getName());
				w.writeRaw(f.getSalt());
				w.writeListEnd();
			}
			w.writeListEnd();
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	private ContactId getContactId(Transaction txn, GroupId contactGroupId)
			throws DbException, FormatException {
		Metadata meta = db.getGroupMetadata(txn, contactGroupId);
		BdfDictionary d = metadataParser.parse(meta);
		return new ContactId(d.getInteger("contactId").intValue());
	}

	private Set<GroupId> getVisibleForums(Transaction txn,
			Message remoteUpdate) throws DbException, FormatException {
		// Get the latest local update
		LatestUpdate local = findLatest(txn, remoteUpdate.getGroupId(), true);
		// If there's no local update, no forums are visible
		if (local == null) return Collections.emptySet();
		// Intersect the sets of shared forums
		byte[] localRaw = db.getRawMessage(txn, local.messageId);
		Set<Forum> shared = new HashSet<Forum>(parseForumList(localRaw));
		shared.retainAll(parseForumList(remoteUpdate.getRaw()));
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
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeString(name);
			w.writeRaw(salt);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		Group g = groupFactory.createGroup(forumManager.getClientId(),
				out.toByteArray());
		return new Forum(g, name, salt);
	}

	private Forum parseForum(Group g) throws FormatException {
		ByteArrayInputStream in = new ByteArrayInputStream(g.getDescriptor());
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			String name = r.readString(MAX_FORUM_NAME_LENGTH);
			byte[] salt = r.readRaw(FORUM_SALT_LENGTH);
			r.readListEnd();
			if (!r.eof()) throw new FormatException();
			return new Forum(g, name, salt);
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
		}
	}

	private boolean listContains(Transaction txn, GroupId g, GroupId forum,
			boolean local) throws DbException, FormatException {
		LatestUpdate latest = findLatest(txn, g, local);
		if (latest == null) return false;
		byte[] raw = db.getRawMessage(txn, latest.messageId);
		List<Forum> list = parseForumList(raw);
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
		byte[] raw = db.getRawMessage(txn, latest.messageId);
		List<Forum> list = parseForumList(raw);
		if (list.contains(f)) return false;
		list.add(f);
		storeMessage(txn, g, list, latest.version + 1);
		return true;
	}

	private void removeFromList(Transaction txn, GroupId g, Forum f)
			throws DbException, FormatException {
		LatestUpdate latest = findLatest(txn, g, true);
		if (latest == null) return;
		byte[] raw = db.getRawMessage(txn, latest.messageId);
		List<Forum> list = parseForumList(raw);
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
