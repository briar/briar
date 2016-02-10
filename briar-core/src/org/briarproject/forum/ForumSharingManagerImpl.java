package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
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
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.MessageValidatedEvent;
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
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class ForumSharingManagerImpl implements ForumSharingManager, AddContactHook,
		RemoveContactHook, EventListener {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"cd11a5d04dccd9e2931d6fc3df456313"
					+ "63bb3e9d9d0e9405fccdb051f41f5449"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private static final Logger LOG =
			Logger.getLogger(ForumSharingManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final Executor dbExecutor;
	private final ContactManager contactManager;
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

	/** Ensures isolation between database reads and writes. */
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	@Inject
	ForumSharingManagerImpl(DatabaseComponent db,
			@DatabaseExecutor Executor dbExecutor,
			ContactManager contactManager, ForumManager forumManager,
			GroupFactory groupFactory, PrivateGroupFactory privateGroupFactory,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser, SecureRandom random, Clock clock) {
		this.db = db;
		this.dbExecutor = dbExecutor;
		this.contactManager = contactManager;
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
	public void addingContact(ContactId c) {
		lock.writeLock().lock();
		try {
			// Create a group to share with the contact
			Group g = getContactGroup(db.getContact(c));
			// Store the group and share it with the contact
			db.addGroup(g);
			db.setVisibility(g.getId(), Collections.singletonList(c));
			// Attach the contact ID to the group
			BdfDictionary d = new BdfDictionary();
			d.put("contactId", c.getInt());
			db.mergeGroupMetadata(g.getId(), metadataEncoder.encode(d));
			// Share any forums that are shared with all contacts
			List<Forum> shared = getForumsSharedWithAllContacts();
			storeMessage(g.getId(), shared, 0);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (FormatException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void removingContact(ContactId c) {
		lock.writeLock().lock();
		try {
			db.removeGroup(getContactGroup(db.getContact(c)));
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof MessageValidatedEvent) {
			MessageValidatedEvent m = (MessageValidatedEvent) e;
			ClientId c = m.getClientId();
			if (m.isValid() && !m.isLocal() && c.equals(CLIENT_ID))
				remoteForumsUpdated(m.getMessage().getGroupId());
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
		lock.writeLock().lock();
		try {
			db.addGroup(f.getGroup());
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void removeForum(Forum f) throws DbException {
		lock.writeLock().lock();
		try {
			// Update the list of forums shared with each contact
			for (Contact c : contactManager.getContacts()) {
				Group contactGroup = getContactGroup(c);
				removeFromList(contactGroup.getId(), f);
			}
			db.removeGroup(f.getGroup());
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public Collection<Forum> getAvailableForums() throws DbException {
		lock.readLock().lock();
		try {
			// Get any forums we subscribe to
			Set<Group> subscribed = new HashSet<Group>(db.getGroups(
					forumManager.getClientId()));
			// Get all forums shared by contacts
			Set<Forum> available = new HashSet<Forum>();
			for (Contact c : contactManager.getContacts()) {
				Group g = getContactGroup(c);
				// Find the latest update version
				LatestUpdate latest = findLatest(g.getId(), false);
				if (latest != null) {
					// Retrieve and parse the latest update
					byte[] raw = db.getRawMessage(latest.messageId);
					for (Forum f : parseForumList(raw)) {
						if (!subscribed.contains(f.getGroup()))
							available.add(f);
					}
				}
			}
			return Collections.unmodifiableSet(available);
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public Collection<Contact> getSharedBy(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			List<Contact> subscribers = new ArrayList<Contact>();
			for (Contact c : contactManager.getContacts()) {
				Group contactGroup = getContactGroup(c);
				if (listContains(contactGroup.getId(), g, false))
					subscribers.add(c);
			}
			return Collections.unmodifiableList(subscribers);
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public Collection<ContactId> getSharedWith(GroupId g) throws DbException {
		lock.readLock().lock();
		try {
			List<ContactId> shared = new ArrayList<ContactId>();
			for (Contact c : contactManager.getContacts()) {
				Group contactGroup = getContactGroup(c);
				if (listContains(contactGroup.getId(), g, true))
					shared.add(c.getId());
			}
			return Collections.unmodifiableList(shared);
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void setSharedWith(GroupId g, Collection<ContactId> shared)
			throws DbException {
		lock.writeLock().lock();
		try {
			// Retrieve the forum
			Forum f = parseForum(db.getGroup(g));
			// Remove the forum from the list of forums shared with all contacts
			removeFromList(localGroup.getId(), f);
			// Update the list of forums shared with each contact
			shared = new HashSet<ContactId>(shared);
			for (Contact c : contactManager.getContacts()) {
				Group contactGroup = getContactGroup(c);
				if (shared.contains(c.getId())) {
					if (addToList(contactGroup.getId(), f)) {
						// If the contact is sharing the forum, make it visible
						if (listContains(contactGroup.getId(), g, false))
							db.setVisibleToContact(c.getId(), g, true);
					}
				} else {
					removeFromList(contactGroup.getId(), f);
					db.setVisibleToContact(c.getId(), g, false);
				}
			}
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Override
	public void setSharedWithAll(GroupId g) throws DbException {
		lock.writeLock().lock();
		try {
			// Retrieve the forum
			Forum f = parseForum(db.getGroup(g));
			// Add the forum to the list of forums shared with all contacts
			addToList(localGroup.getId(), f);
			// Add the forum to the list of forums shared with each contact
			for (Contact c : contactManager.getContacts()) {
				Group contactGroup = getContactGroup(c);
				if (addToList(contactGroup.getId(), f)) {
					// If the contact is sharing the forum, make it visible
					if (listContains(contactGroup.getId(), g, false))
						db.setVisibleToContact(getContactId(g), g, true);
				}
			}
		} catch (FormatException e) {
			throw new DbException(e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private Group getContactGroup(Contact c) {
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	// Locking: lock.writeLock
	private List<Forum> getForumsSharedWithAllContacts() throws DbException,
			FormatException {
		// Ensure the local group exists
		db.addGroup(localGroup);
		// Find the latest update in the local group
		LatestUpdate latest = findLatest(localGroup.getId(), true);
		if (latest == null) return Collections.emptyList();
		// Retrieve and parse the latest update
		return parseForumList(db.getRawMessage(latest.messageId));
	}

	// Locking: lock.readLock
	private LatestUpdate findLatest(GroupId g, boolean local)
			throws DbException, FormatException {
		LatestUpdate latest = null;
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(g);
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

	// Locking: lock.writeLock
	private void storeMessage(GroupId g, List<Forum> forums, long version)
			throws DbException, FormatException {
		byte[] body = encodeForumList(forums, version);
		long now = clock.currentTimeMillis();
		Message m = messageFactory.createMessage(g, now, body);
		BdfDictionary d = new BdfDictionary();
		d.put("version", version);
		d.put("local", true);
		db.addLocalMessage(m, CLIENT_ID, metadataEncoder.encode(d), true);
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

	private void remoteForumsUpdated(final GroupId g) {
		dbExecutor.execute(new Runnable() {
			public void run() {
				lock.writeLock().lock();
				try {
					setForumVisibility(getContactId(g), getVisibleForums(g));
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} catch (FormatException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				} finally {
					lock.writeLock().unlock();
				}
			}
		});
	}

	// Locking: lock.readLock
	private ContactId getContactId(GroupId contactGroupId) throws DbException,
			FormatException {
		Metadata meta = db.getGroupMetadata(contactGroupId);
		BdfDictionary d = metadataParser.parse(meta);
		int id = d.getInteger("contactId").intValue();
		return new ContactId(id);
	}

	// Locking: lock.readLock
	private Set<GroupId> getVisibleForums(GroupId contactGroupId)
			throws DbException, FormatException {
		// Get the latest local and remote updates
		LatestUpdate local = findLatest(contactGroupId, true);
		LatestUpdate remote = findLatest(contactGroupId, false);
		// If there's no local and/or remote update, no forums are visible
		if (local == null || remote == null) return Collections.emptySet();
		// Intersect the sets of shared forums
		byte[] localRaw = db.getRawMessage(local.messageId);
		Set<Forum> shared = new HashSet<Forum>(parseForumList(localRaw));
		byte[] remoteRaw = db.getRawMessage(remote.messageId);
		shared.retainAll(parseForumList(remoteRaw));
		// Forums in the intersection should be visible
		Set<GroupId> visible = new HashSet<GroupId>(shared.size());
		for (Forum f : shared) visible.add(f.getId());
		return visible;
	}

	// Locking: lock.writeLock
	private void setForumVisibility(ContactId c, Set<GroupId> visible)
			throws DbException {
		for (Group g : db.getGroups(forumManager.getClientId())) {
			boolean isVisible = db.isVisibleToContact(c, g.getId());
			boolean shouldBeVisible = visible.contains(g.getId());
			if (isVisible && !shouldBeVisible)
				db.setVisibleToContact(c, g.getId(), false);
			else if (!isVisible && shouldBeVisible)
				db.setVisibleToContact(c, g.getId(), true);
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

	// Locking: lock.readLock
	private boolean listContains(GroupId g, GroupId forum, boolean local)
			throws DbException, FormatException {
		LatestUpdate latest = findLatest(g, local);
		if (latest == null) return false;
		List<Forum> list = parseForumList(db.getRawMessage(latest.messageId));
		for (Forum f : list) if (f.getId().equals(forum)) return true;
		return false;
	}

	// Locking: lock.writeLock
	private boolean addToList(GroupId g, Forum f) throws DbException,
			FormatException {
		LatestUpdate latest = findLatest(g, true);
		if (latest == null) {
			storeMessage(g, Collections.singletonList(f), 0);
			return true;
		}
		List<Forum> list = parseForumList(db.getRawMessage(latest.messageId));
		if (list.contains(f)) return false;
		list.add(f);
		storeMessage(g, list, latest.version + 1);
		return true;
	}

	// Locking: lock.writeLock
	private void removeFromList(GroupId g, Forum f) throws DbException,
			FormatException {
		LatestUpdate latest = findLatest(g, true);
		if (latest == null) return;
		List<Forum> list = parseForumList(db.getRawMessage(latest.messageId));
		if (list.remove(f)) storeMessage(g, list, latest.version + 1);
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
