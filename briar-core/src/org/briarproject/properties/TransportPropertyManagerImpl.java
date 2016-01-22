package org.briarproject.properties;

import com.google.inject.Inject;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.UniqueId;
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
import org.briarproject.api.db.NoSuchSubscriptionException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class TransportPropertyManagerImpl implements TransportPropertyManager,
		AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"673ea091673561e28f70122f6a8ea8f4"
					+ "97c3624b86fa07f785bb15f09fb87b4b"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private static final Logger LOG =
			Logger.getLogger(TransportPropertyManagerImpl.class.getName());

	private final DatabaseComponent db;
	private final GroupFactory groupFactory;
	private final MessageFactory messageFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;
	private final Clock clock;
	private final Group localGroup;

	/** Ensures isolation between database reads and writes. */
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	@Inject
	TransportPropertyManagerImpl(DatabaseComponent db, GroupFactory groupFactory,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser, Clock clock) {
		this.db = db;
		this.groupFactory = groupFactory;
		this.messageFactory = messageFactory;
		this.bdfReaderFactory = bdfReaderFactory;
		this.bdfWriterFactory = bdfWriterFactory;
		this.metadataEncoder = metadataEncoder;
		this.metadataParser = metadataParser;
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
			// Subscribe to the group and share it with the contact
			db.addGroup(g);
			db.addContactGroup(c, g);
			db.setVisibility(g.getId(), Collections.singletonList(c));
			// Copy the latest local properties into the group
			Map<TransportId, TransportProperties> local = getLocalProperties();
			for (Entry<TransportId, TransportProperties> e : local.entrySet())
				storeMessage(g.getId(), e.getKey(), e.getValue(), 0);
		} catch (DbException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private Group getContactGroup(Contact c) {
		AuthorId local = c.getLocalAuthorId();
		AuthorId remote = c.getAuthor().getId();
		byte[] descriptor = encodeGroupDescriptor(local, remote);
		return groupFactory.createGroup(CLIENT_ID, descriptor);
	}

	private byte[] encodeGroupDescriptor(AuthorId local, AuthorId remote) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			if (UniqueId.IdComparator.INSTANCE.compare(local, remote) < 0) {
				w.writeRaw(local.getBytes());
				w.writeRaw(remote.getBytes());
			} else {
				w.writeRaw(remote.getBytes());
				w.writeRaw(local.getBytes());
			}
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	private void storeMessage(GroupId g, TransportId t, TransportProperties p,
			long version) throws DbException, IOException {
		byte[] body = encodeProperties(t, p, version);
		long now = clock.currentTimeMillis();
		Message m = messageFactory.createMessage(g, now, body);
		BdfDictionary d = new BdfDictionary();
		d.put("transportId", t.getString());
		d.put("version", version);
		d.put("local", true);
		db.addLocalMessage(m, CLIENT_ID, metadataEncoder.encode(d));
	}

	private byte[] encodeProperties(TransportId t, TransportProperties p,
			long version) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			// TODO: Device ID
			w.writeListStart();
			w.writeString(t.getString());
			w.writeInteger(version);
			w.writeDictionary(p);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
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
	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		lock.readLock().lock();
		try {
			// Find the latest local version for each transport
			Map<TransportId, Latest> latest =
					findLatest(localGroup.getId(), true);
			// Retrieve and decode the latest local properties
			Map<TransportId, TransportProperties> local =
					new HashMap<TransportId, TransportProperties>();
			for (Entry<TransportId, Latest> e : latest.entrySet()) {
				byte[] raw = db.getRawMessage(e.getValue().messageId);
				local.put(e.getKey(), decodeProperties(raw));
			}
			return Collections.unmodifiableMap(local);
		} catch (NoSuchSubscriptionException e) {
			// Local group doesn't exist - there are no local properties
			return Collections.emptyMap();
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	private Map<TransportId, Latest> findLatest(GroupId g, boolean local)
			throws DbException, FormatException {
		// TODO: Use metadata queries
		Map<TransportId, Latest> latest = new HashMap<TransportId, Latest>();
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(g);
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			BdfDictionary mm = metadataParser.parse(e.getValue());
			if (mm.getBoolean("local") != local) continue;
			TransportId t = new TransportId(mm.getString("transportId"));
			long version = mm.getInteger("version");
			Latest l = latest.get(t);
			if (l == null || version > l.version)
				latest.put(t, new Latest(e.getKey(), version));
		}
		return latest;
	}

	private TransportProperties decodeProperties(byte[] raw)
			throws IOException {
		TransportProperties p = new TransportProperties();
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		// TODO: Device ID
		r.readListStart();
		r.skipString(); // Transport ID
		r.skipInteger(); // Version
		r.readDictionaryStart();
		while (!r.hasDictionaryEnd()) {
			String key = r.readString(MAX_PROPERTY_LENGTH);
			String value = r.readString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		r.readDictionaryEnd();
		r.readListEnd();
		if (!r.eof()) throw new FormatException();
		return p;
	}

	@Override
	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		lock.readLock().lock();
		try {
			// Find the latest local version
			Latest latest = findLatest(localGroup.getId(), true).get(t);
			if (latest == null) return null;
			// Retrieve and decode the latest local properties
			return decodeProperties(db.getRawMessage(latest.messageId));
		} catch (NoSuchSubscriptionException e) {
			// Local group doesn't exist - there are no local properties
			return null;
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		lock.readLock().lock();
		try {
			Map<ContactId, TransportProperties> remote =
					new HashMap<ContactId, TransportProperties>();
			for (Contact c : db.getContacts())  {
				Group g = getContactGroup(c);
				// Find the latest remote version
				Latest latest = findLatest(g.getId(), false).get(t);
				if (latest != null) {
					// Retrieve and decode the latest remote properties
					byte[] raw = db.getRawMessage(latest.messageId);
					remote.put(c.getId(), decodeProperties(raw));
				}
			}
			return Collections.unmodifiableMap(remote);
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.readLock().unlock();
		}
	}

	@Override
	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		lock.writeLock().lock();
		try {
			// Create the local group if necessary
			db.addGroup(localGroup);
			// Merge the new properties with any existing properties
			Latest latest = findLatest(localGroup.getId(), true).get(t);
			if (latest != null) {
				byte[] raw = db.getRawMessage(latest.messageId);
				TransportProperties old = decodeProperties(raw);
				if (old.equals(p)) return; // Unchanged
				old.putAll(p);
				p = old;
			}
			// Store the merged properties in the local group
			long version = latest == null ? 0 : latest.version + 1;
			storeMessage(localGroup.getId(), t, p, version);
			// Store the merged properties in each contact's group
			for (Contact c : db.getContacts()) {
				Group g = getContactGroup(c);
				latest = findLatest(g.getId(), true).get(t);
				version = latest == null ? 0 : latest.version + 1;
				storeMessage(g.getId(), t, p, version);
			}
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.writeLock().unlock();
		}
	}

	private static class Latest {

		private final MessageId messageId;
		private final long version;

		private Latest(MessageId messageId, long version) {
			this.messageId = messageId;
			this.version = version;
		}
	}
}
