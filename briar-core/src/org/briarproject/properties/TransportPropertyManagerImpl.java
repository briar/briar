package org.briarproject.properties;

import com.google.inject.Inject;

import org.briarproject.api.DeviceId;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
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
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
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
	private final PrivateGroupFactory privateGroupFactory;
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
	TransportPropertyManagerImpl(DatabaseComponent db,
			GroupFactory groupFactory, PrivateGroupFactory privateGroupFactory,
			MessageFactory messageFactory, BdfReaderFactory bdfReaderFactory,
			BdfWriterFactory bdfWriterFactory, MetadataEncoder metadataEncoder,
			MetadataParser metadataParser, Clock clock) {
		this.db = db;
		this.privateGroupFactory = privateGroupFactory;
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
			DeviceId dev = db.getDeviceId();
			Map<TransportId, TransportProperties> local = getLocalProperties();
			for (Entry<TransportId, TransportProperties> e : local.entrySet())
				storeMessage(g.getId(), dev, e.getKey(), e.getValue(), 0);
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
		return privateGroupFactory.createPrivateGroup(CLIENT_ID, c);
	}

	private void storeMessage(GroupId g, DeviceId dev, TransportId t,
			TransportProperties p, long version) throws DbException,
			IOException {
		byte[] body = encodeProperties(dev, t, p, version);
		long now = clock.currentTimeMillis();
		Message m = messageFactory.createMessage(g, now, body);
		BdfDictionary d = new BdfDictionary();
		d.put("transportId", t.getString());
		d.put("version", version);
		d.put("local", true);
		db.addLocalMessage(m, CLIENT_ID, metadataEncoder.encode(d), true);
	}

	private byte[] encodeProperties(DeviceId dev, TransportId t,
			TransportProperties p, long version) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeRaw(dev.getBytes());
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
			Map<TransportId, LatestUpdate> latest =
					findLatest(localGroup.getId(), true);
			// Retrieve and decode the latest local properties
			Map<TransportId, TransportProperties> local =
					new HashMap<TransportId, TransportProperties>();
			for (Entry<TransportId, LatestUpdate> e : latest.entrySet()) {
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

	private Map<TransportId, LatestUpdate> findLatest(GroupId g, boolean local)
			throws DbException, FormatException {
		// TODO: Use metadata queries
		Map<TransportId, LatestUpdate> latestUpdates =
				new HashMap<TransportId, LatestUpdate>();
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(g);
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			BdfDictionary d = metadataParser.parse(e.getValue());
			if (d.getBoolean("local") != local) continue;
			TransportId t = new TransportId(d.getString("transportId"));
			long version = d.getInteger("version");
			LatestUpdate latest = latestUpdates.get(t);
			if (latest == null || version > latest.version)
				latestUpdates.put(t, new LatestUpdate(e.getKey(), version));
		}
		return latestUpdates;
	}

	private TransportProperties decodeProperties(byte[] raw)
			throws IOException {
		TransportProperties p = new TransportProperties();
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		r.readListStart();
		r.skipRaw(); // Device ID
		r.skipString(); // Transport ID
		r.skipInteger(); // Version
		r.readDictionaryStart();
		while (!r.hasDictionaryEnd()) {
			String key = r.readString(MAX_PROPERTY_LENGTH);
			String value = r.readString(MAX_PROPERTY_LENGTH);
			p.put(key, value);
		}
		return p;
	}

	@Override
	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		lock.readLock().lock();
		try {
			// Find the latest local version
			LatestUpdate latest = findLatest(localGroup.getId(), true).get(t);
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
				LatestUpdate latest = findLatest(g.getId(), false).get(t);
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
			TransportProperties merged;
			LatestUpdate latest = findLatest(localGroup.getId(), true).get(t);
			if (latest == null) {
				merged = p;
			} else {
				byte[] raw = db.getRawMessage(latest.messageId);
				TransportProperties old = decodeProperties(raw);
				merged = new TransportProperties(old);
				merged.putAll(p);
				if (merged.equals(old)) return; // Unchanged
			}
			// Store the merged properties in the local group
			DeviceId dev = db.getDeviceId();
			long version = latest == null ? 0 : latest.version + 1;
			storeMessage(localGroup.getId(), dev, t, merged, version);
			// Store the merged properties in each contact's group
			for (Contact c : db.getContacts()) {
				Group g = getContactGroup(c);
				latest = findLatest(g.getId(), true).get(t);
				version = latest == null ? 0 : latest.version + 1;
				storeMessage(g.getId(), dev, t, merged, version);
			}
		} catch (IOException e) {
			throw new DbException(e);
		} finally {
			lock.writeLock().unlock();
		}
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
