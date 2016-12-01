package org.briarproject.bramble.properties;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.AddContactHook;
import org.briarproject.bramble.api.contact.ContactManager.RemoveContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;

@Immutable
@NotNullByDefault
class TransportPropertyManagerImpl implements TransportPropertyManager,
		Client, AddContactHook, RemoveContactHook {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final ContactGroupFactory contactGroupFactory;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	TransportPropertyManagerImpl(DatabaseComponent db,
			ClientHelper clientHelper, ContactGroupFactory contactGroupFactory,
			Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.contactGroupFactory = contactGroupFactory;
		this.clock = clock;
		localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID);
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		db.addGroup(txn, localGroup);
		// Ensure we've set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		// Return if we've already set things up for this contact
		if (db.containsGroup(txn, g.getId())) return;
		// Store the group and share it with the contact
		db.addGroup(txn, g);
		db.setGroupVisibility(txn, c.getId(), g.getId(), SHARED);
		// Copy the latest local properties into the group
		Map<TransportId, TransportProperties> local = getLocalProperties(txn);
		for (Entry<TransportId, TransportProperties> e : local.entrySet()) {
			storeMessage(txn, g.getId(), e.getKey(), e.getValue(), 1,
					true, true);
		}
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public void addRemoteProperties(Transaction txn, ContactId c,
			Map<TransportId, TransportProperties> props) throws DbException {
		Group g = getContactGroup(db.getContact(txn, c));
		for (Entry<TransportId, TransportProperties> e : props.entrySet()) {
			storeMessage(txn, g.getId(), e.getKey(), e.getValue(), 0,
					false, false);
		}
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		Map<TransportId, TransportProperties> local;
		Transaction txn = db.startTransaction(true);
		try {
			local = getLocalProperties(txn);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return local;
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties(
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
				if (message == null) throw new DbException();
				local.put(e.getKey(), parseProperties(message));
			}
			return local;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public TransportProperties getLocalProperties(TransportId t)
			throws DbException {
		try {
			TransportProperties p = null;
			Transaction txn = db.startTransaction(true);
			try {
				// Find the latest local update
				LatestUpdate latest = findLatest(txn, localGroup.getId(), t,
						true);
				if (latest != null) {
					// Retrieve and parse the latest local properties
					BdfList message = clientHelper.getMessageAsList(txn,
							latest.messageId);
					if (message == null) throw new DbException();
					p = parseProperties(message);
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			return p == null ? new TransportProperties() : p;
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
			Transaction txn = db.startTransaction(true);
			try {
				for (Contact c : db.getContacts(txn)) {
					// Don't return properties for inactive contacts
					if (!c.isActive()) continue;
					Group g = getContactGroup(c);
					// Find the latest remote update
					LatestUpdate latest = findLatest(txn, g.getId(), t, false);
					if (latest != null) {
						// Retrieve and parse the latest remote properties
						BdfList message = clientHelper.getMessageAsList(txn,
								latest.messageId);
						if (message == null) throw new DbException();
						remote.put(c.getId(), parseProperties(message));
					}
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
			return remote;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		try {
			Transaction txn = db.startTransaction(false);
			try {
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
					if (message == null) throw new DbException();
					TransportProperties old = parseProperties(message);
					merged = new TransportProperties(old);
					merged.putAll(p);
					changed = !merged.equals(old);
				}
				if (changed) {
					// Store the merged properties in the local group
					long version = latest == null ? 1 : latest.version + 1;
					storeMessage(txn, localGroup.getId(), t, merged, version,
							true, false);
					// Store the merged properties in each contact's group
					for (Contact c : db.getContacts(txn)) {
						Group g = getContactGroup(c);
						latest = findLatest(txn, g.getId(), t, true);
						version = latest == null ? 1 : latest.version + 1;
						storeMessage(txn, g.getId(), t, merged, version,
								true, true);
					}
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID, c);
	}

	private void storeMessage(Transaction txn, GroupId g, TransportId t,
			TransportProperties p, long version, boolean local, boolean shared)
			throws DbException {
		try {
			BdfList body = encodeProperties(t, p, version);
			long now = clock.currentTimeMillis();
			Message m = clientHelper.createMessage(g, now, body);
			BdfDictionary meta = new BdfDictionary();
			meta.put("transportId", t.getString());
			meta.put("version", version);
			meta.put("local", local);
			clientHelper.addLocalMessage(txn, m, meta, shared);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private BdfList encodeProperties(TransportId t, TransportProperties p,
			long version) {
		return BdfList.of(t.getString(), version, p);
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

	@Nullable
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
		// Transport ID, version, properties
		BdfDictionary dictionary = message.getDictionary(2);
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
