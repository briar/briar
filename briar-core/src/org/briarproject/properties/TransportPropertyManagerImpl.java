package org.briarproject.properties;

import com.google.inject.Inject;

import org.briarproject.api.DeviceId;
import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
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
import org.briarproject.api.db.NoSuchGroupException;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.properties.TransportProperties;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

class TransportPropertyManagerImpl implements TransportPropertyManager,
		AddContactHook, RemoveContactHook {

	static final ClientId CLIENT_ID = new ClientId(StringUtils.fromHexString(
			"673ea091673561e28f70122f6a8ea8f4"
					+ "97c3624b86fa07f785bb15f09fb87b4b"));

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final PrivateGroupFactory privateGroupFactory;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	TransportPropertyManagerImpl(DatabaseComponent db,
			ClientHelper clientHelper, GroupFactory groupFactory,
			PrivateGroupFactory privateGroupFactory, Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.privateGroupFactory = privateGroupFactory;
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
					BdfList message = clientHelper.getMessageAsList(txn,
							latest.messageId);
					p = parseProperties(message);
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
						BdfList message = clientHelper.getMessageAsList(txn,
								latest.messageId);
						remote.put(c.getId(), parseProperties(message));
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
					BdfList message = clientHelper.getMessageAsList(txn,
							latest.messageId);
					TransportProperties old = parseProperties(message);
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
				BdfList message = clientHelper.getMessageAsList(txn,
						e.getValue().messageId);
				local.put(e.getKey(), parseProperties(message));
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
			BdfList body = encodeProperties(dev, t, p, version);
			long now = clock.currentTimeMillis();
			Message m = clientHelper.createMessage(g, now, body);
			BdfDictionary meta = new BdfDictionary();
			meta.put("transportId", t.getString());
			meta.put("version", version);
			meta.put("local", local);
			clientHelper.addLocalMessage(txn, m, CLIENT_ID, meta, shared);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private BdfList encodeProperties(DeviceId dev, TransportId t,
			TransportProperties p, long version) {
		return BdfList.of(dev, t.getString(), version, p);
	}

	private Map<TransportId, LatestUpdate> findLatest(Transaction txn,
			GroupId g, boolean local) throws DbException, FormatException {
		Map<TransportId, LatestUpdate> latestUpdates =
				new HashMap<TransportId, LatestUpdate>();
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			if (meta.getBoolean("local") == local) {
				TransportId t = new TransportId(meta.getString("transportId"));
				long version = meta.getLong("version");
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
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			if (meta.getString("transportId").equals(t.getString())
					&& meta.getBoolean("local") == local) {
				long version = meta.getLong("version");
				if (latest == null || version > latest.version)
					latest = new LatestUpdate(e.getKey(), version);
			}
		}
		return latest;
	}

	private TransportProperties parseProperties(BdfList message)
			throws FormatException {
		// Device ID, transport ID, version, properties
		BdfDictionary dictionary = message.getDictionary(3);
		TransportProperties p = new TransportProperties();
		for (String key : dictionary.keySet())
			p.put(key, dictionary.getString(key));
		return p;
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
