package org.briarproject.properties;

import org.briarproject.api.DeviceId;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.clients.PrivateGroupFactory;
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
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.Transaction;
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

import javax.inject.Inject;

import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;
import static org.briarproject.api.sync.SyncConstants.MESSAGE_HEADER_LENGTH;

class TransportPropertyManagerImpl implements TransportPropertyManager,
		AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"673ea091673561e28f70122f6a8ea8f4"
					+ "97c3624b86fa07f785bb15f09fb87b4b"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private final DatabaseComponent db;
	private final PrivateGroupFactory privateGroupFactory;
	private final MessageFactory messageFactory;
	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;
	private final MetadataEncoder metadataEncoder;
	private final MetadataParser metadataParser;
	private final Clock clock;
	private final Group localGroup;

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
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		// Store the group and share it with the contact
		db.addGroup(txn, g);
		db.setVisibleToContact(txn, c.getId(), g.getId(), true);
		// Copy the latest local properties into the group
		DeviceId dev = db.getDeviceId(txn);
		Map<TransportId, TransportProperties> local = getLocalProperties(txn);
		for (Entry<TransportId, TransportProperties> e : local.entrySet()) {
			storeMessage(txn, g.getId(), dev, e.getKey(), e.getValue(), 1,
					true, true);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void addRemoteProperties(ContactId c, DeviceId dev,
			Map<TransportId, TransportProperties> props) throws DbException {
		Transaction txn = db.startTransaction();
		try {
			Group g = getContactGroup(db.getContact(txn, c));
			for (Entry<TransportId, TransportProperties> e : props.entrySet()) {
				storeMessage(txn, g.getId(), dev, e.getKey(), e.getValue(), 0,
						false, false);
			}
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		Map<TransportId, TransportProperties> local;
		Transaction txn = db.startTransaction();
		try {
			local = getLocalProperties(txn);
			txn.setComplete();
		} finally {
			db.endTransaction(txn);
		}
		return Collections.unmodifiableMap(local);
	}

	@Override
	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		try {
			TransportProperties p = null;
			Transaction txn = db.startTransaction();
			try {
				// Find the latest local update
				LatestUpdate latest = findLatest(txn, localGroup.getId(), t,
						true);
				if (latest != null) {
					// Retrieve and parse the latest local properties
					byte[] raw = db.getRawMessage(txn, latest.messageId);
					p = parseProperties(raw);
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return p;
		} catch (NoSuchGroupException e) {
			// Local group doesn't exist - there are no local properties
			return null;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		try {
			Map<ContactId, TransportProperties> remote =
					new HashMap<ContactId, TransportProperties>();
			Transaction txn = db.startTransaction();
			try {
				for (Contact c : db.getContacts(txn)) {
					Group g = getContactGroup(c);
					// Find the latest remote update
					LatestUpdate latest = findLatest(txn, g.getId(), t, false);
					if (latest != null) {
						// Retrieve and parse the latest remote properties
						byte[] raw = db.getRawMessage(txn, latest.messageId);
						remote.put(c.getId(), parseProperties(raw));
					}
				}
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}
			return Collections.unmodifiableMap(remote);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		try {
			Transaction txn = db.startTransaction();
			try {
				// Create the local group if necessary
				db.addGroup(txn, localGroup);
				// Merge the new properties with any existing properties
				TransportProperties merged;
				boolean changed;
				LatestUpdate latest = findLatest(txn, localGroup.getId(), t,
						true);
				if (latest == null) {
					merged = p;
					changed = true;
				} else {
					byte[] raw = db.getRawMessage(txn, latest.messageId);
					TransportProperties old = parseProperties(raw);
					merged = new TransportProperties(old);
					merged.putAll(p);
					changed = !merged.equals(old);
				}
				if (changed) {
					// Store the merged properties in the local group
					DeviceId dev = db.getDeviceId(txn);
					long version = latest == null ? 1 : latest.version + 1;
					storeMessage(txn, localGroup.getId(), dev, t, merged,
							version, true, false);
					// Store the merged properties in each contact's group
					for (Contact c : db.getContacts(txn)) {
						Group g = getContactGroup(c);
						latest = findLatest(txn, g.getId(), t, true);
						version = latest == null ? 1 : latest.version + 1;
						storeMessage(txn, g.getId(), dev, t, merged, version,
								true, true);
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

	private Map<TransportId, TransportProperties> getLocalProperties(
			Transaction txn) throws DbException {
		try {
			Map<TransportId, TransportProperties> local =
					new HashMap<TransportId, TransportProperties>();
			// Find the latest local update for each transport
			Map<TransportId, LatestUpdate> latest = findLatest(txn,
					localGroup.getId(), true);
			// Retrieve and parse the latest local properties
			for (Entry<TransportId, LatestUpdate> e : latest.entrySet()) {
				byte[] raw = db.getRawMessage(txn, e.getValue().messageId);
				local.put(e.getKey(), parseProperties(raw));
			}
			return local;
		} catch (NoSuchGroupException e) {
			// Local group doesn't exist - there are no local properties
			return Collections.emptyMap();
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void storeMessage(Transaction txn, GroupId g, DeviceId dev,
			TransportId t, TransportProperties p, long version, boolean local,
			boolean shared) throws DbException {
		try {
			byte[] body = encodeProperties(dev, t, p, version);
			long now = clock.currentTimeMillis();
			Message m = messageFactory.createMessage(g, now, body);
			BdfDictionary d = new BdfDictionary();
			d.put("transportId", t.getString());
			d.put("version", version);
			d.put("local", local);
			Metadata meta = metadataEncoder.encode(d);
			db.addLocalMessage(txn, m, CLIENT_ID, meta, shared);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private byte[] encodeProperties(DeviceId dev, TransportId t,
			TransportProperties p, long version) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			w.writeRaw(dev.getBytes());
			w.writeString(t.getString());
			w.writeLong(version);
			w.writeDictionary(p);
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}

	private Map<TransportId, LatestUpdate> findLatest(Transaction txn,
			GroupId g, boolean local) throws DbException, FormatException {
		Map<TransportId, LatestUpdate> latestUpdates =
				new HashMap<TransportId, LatestUpdate>();
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(txn, g);
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			BdfDictionary d = metadataParser.parse(e.getValue());
			if (d.getBoolean("local") == local) {
				TransportId t = new TransportId(d.getString("transportId"));
				long version = d.getLong("version");
				LatestUpdate latest = latestUpdates.get(t);
				if (latest == null || version > latest.version)
					latestUpdates.put(t, new LatestUpdate(e.getKey(), version));
			}
		}
		return latestUpdates;
	}

	private LatestUpdate findLatest(Transaction txn, GroupId g, TransportId t,
			boolean local) throws DbException, FormatException {
		LatestUpdate latest = null;
		Map<MessageId, Metadata> metadata = db.getMessageMetadata(txn, g);
		for (Entry<MessageId, Metadata> e : metadata.entrySet()) {
			BdfDictionary d = metadataParser.parse(e.getValue());
			if (d.getString("transportId").equals(t.getString())
					&& d.getBoolean("local") == local) {
				long version = d.getLong("version");
				if (latest == null || version > latest.version)
					latest = new LatestUpdate(e.getKey(), version);
			}
		}
		return latest;
	}

	private TransportProperties parseProperties(byte[] raw)
			throws FormatException {
		TransportProperties p = new TransportProperties();
		ByteArrayInputStream in = new ByteArrayInputStream(raw,
				MESSAGE_HEADER_LENGTH, raw.length - MESSAGE_HEADER_LENGTH);
		BdfReader r = bdfReaderFactory.createReader(in);
		try {
			r.readListStart();
			r.skipRaw(); // Device ID
			r.skipString(); // Transport ID
			r.skipLong(); // Version
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
		} catch (FormatException e) {
			throw e;
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayInputStream
			throw new RuntimeException(e);
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
