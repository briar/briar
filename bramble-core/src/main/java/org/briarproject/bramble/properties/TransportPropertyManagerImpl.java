package org.briarproject.bramble.properties;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.properties.event.RemoteTransportPropertiesUpdatedEvent;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.properties.TransportPropertyConstants.GROUP_KEY_DISCOVERED;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MSG_KEY_LOCAL;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MSG_KEY_TRANSPORT_ID;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.MSG_KEY_VERSION;
import static org.briarproject.bramble.api.properties.TransportPropertyConstants.REFLECTED_PROPERTY_PREFIX;
import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_DO_NOT_SHARE;
import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;

@Immutable
@NotNullByDefault
class TransportPropertyManagerImpl implements TransportPropertyManager,
		OpenDatabaseHook, ContactHook, ClientVersioningHook,
		IncomingMessageHook {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final ClientVersioningManager clientVersioningManager;
	private final MetadataParser metadataParser;
	private final ContactGroupFactory contactGroupFactory;
	private final Clock clock;
	private final Group localGroup;

	@Inject
	TransportPropertyManagerImpl(DatabaseComponent db,
			ClientHelper clientHelper,
			ClientVersioningManager clientVersioningManager,
			MetadataParser metadataParser,
			ContactGroupFactory contactGroupFactory, Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.clientVersioningManager = clientVersioningManager;
		this.metadataParser = metadataParser;
		this.contactGroupFactory = contactGroupFactory;
		this.clock = clock;
		localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
				MAJOR_VERSION);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group to share with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		// Apply the client's visibility to the contact group
		Visibility client = clientVersioningManager.getClientVisibility(txn,
				c.getId(), CLIENT_ID, MAJOR_VERSION);
		db.setGroupVisibility(txn, c.getId(), g.getId(), client);
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
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Visibility v) throws DbException {
		// Apply the client's visibility to the contact group
		Group g = getContactGroup(c);
		db.setGroupVisibility(txn, c.getId(), g.getId(), v);
	}

	@Override
	public DeliveryAction incomingMessage(Transaction txn, Message m,
			Metadata meta) throws DbException, InvalidMessageException {
		try {
			// Find the latest update for this transport, if any
			BdfDictionary d = metadataParser.parse(meta);
			TransportId t = new TransportId(d.getString(MSG_KEY_TRANSPORT_ID));
			LatestUpdate latest = findLatest(txn, m.getGroupId(), t, false);
			if (latest != null) {
				if (d.getLong(MSG_KEY_VERSION) > latest.version) {
					// This update is newer - delete the previous update
					db.deleteMessage(txn, latest.messageId);
					db.deleteMessageMetadata(txn, latest.messageId);
				} else {
					// We've already received a newer update - delete this one
					db.deleteMessage(txn, m.getId());
					db.deleteMessageMetadata(txn, m.getId());
					return ACCEPT_DO_NOT_SHARE;
				}
			}
			txn.attach(new RemoteTransportPropertiesUpdatedEvent(t));
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
		return ACCEPT_DO_NOT_SHARE;
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
	public void addRemotePropertiesFromConnection(ContactId c, TransportId t,
			TransportProperties props) throws DbException {
		if (props.isEmpty()) return;
		try {
			db.transaction(false, txn -> {
				Contact contact = db.getContact(txn, c);
				Group g = getContactGroup(contact);
				BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(
						txn, g.getId());
				BdfDictionary discovered =
						meta.getOptionalDictionary(GROUP_KEY_DISCOVERED);
				BdfDictionary merged;
				boolean changed;
				if (discovered == null) {
					merged = new BdfDictionary(props);
					changed = true;
				} else {
					merged = new BdfDictionary(discovered);
					merged.putAll(props);
					changed = !merged.equals(discovered);
				}
				if (changed) {
					meta.put(GROUP_KEY_DISCOVERED, merged);
					clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
					updateLocalProperties(txn, contact, t);
				}
			});
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties()
			throws DbException {
		return db.transactionWithResult(true, this::getLocalProperties);
	}

	@Override
	public Map<TransportId, TransportProperties> getLocalProperties(
			Transaction txn) throws DbException {
		try {
			Map<TransportId, TransportProperties> local = new HashMap<>();
			// Find the latest local update for each transport
			Map<TransportId, LatestUpdate> latest = findLatestLocal(txn);
			// Retrieve and parse the latest local properties
			for (Entry<TransportId, LatestUpdate> e : latest.entrySet()) {
				BdfList message = clientHelper.getMessageAsList(txn,
						e.getValue().messageId);
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
			return db.transactionWithResult(true, txn -> {
				TransportProperties p = null;
				// Find the latest local update
				LatestUpdate latest = findLatest(txn, localGroup.getId(), t,
						true);
				if (latest != null) {
					// Retrieve and parse the latest local properties
					BdfList message = clientHelper.getMessageAsList(txn,
							latest.messageId);
					p = parseProperties(message);
				}
				return p == null ? new TransportProperties() : p;
			});
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Map<ContactId, TransportProperties> getRemoteProperties(
			TransportId t) throws DbException {
		return db.transactionWithResult(true, txn -> {
			Map<ContactId, TransportProperties> remote = new HashMap<>();
			for (Contact c : db.getContacts(txn))
				remote.put(c.getId(), getRemoteProperties(txn, c, t));
			return remote;
		});
	}

	private void updateLocalProperties(Transaction txn, Contact c,
			TransportId t) throws DbException {
		try {
			TransportProperties local;
			LatestUpdate latest = findLatest(txn, localGroup.getId(), t, true);
			if (latest == null) {
				local = new TransportProperties();
			} else {
				BdfList message = clientHelper.getMessageAsList(txn,
						latest.messageId);
				local = parseProperties(message);
			}
			storeLocalProperties(txn, c, t, local);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private TransportProperties getRemoteProperties(Transaction txn, Contact c,
			TransportId t) throws DbException {
		Group g = getContactGroup(c);
		try {
			// Find the latest remote update
			TransportProperties remote;
			LatestUpdate latest = findLatest(txn, g.getId(), t, false);
			if (latest == null) {
				remote = new TransportProperties();
			} else {
				// Retrieve and parse the latest remote properties
				BdfList message =
						clientHelper.getMessageAsList(txn, latest.messageId);
				remote = parseProperties(message);
			}
			// Merge in any discovered properties
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g.getId());
			BdfDictionary d = meta.getOptionalDictionary(GROUP_KEY_DISCOVERED);
			if (d == null) return remote;
			TransportProperties merged =
					clientHelper.parseAndValidateTransportProperties(d);
			// Received properties override discovered properties
			merged.putAll(remote);
			return merged;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public TransportProperties getRemoteProperties(ContactId c, TransportId t)
			throws DbException {
		return db.transactionWithResult(true, txn ->
				getRemoteProperties(txn, db.getContact(txn, c), t));
	}

	@Override
	public void mergeLocalProperties(TransportId t, TransportProperties p)
			throws DbException {
		try {
			db.transaction(false, txn -> {
				// Merge the new properties with any existing properties
				TransportProperties merged;
				boolean changed;
				LatestUpdate latest = findLatest(txn, localGroup.getId(), t,
						true);
				if (latest == null) {
					merged = new TransportProperties(p);
					Iterator<String> it = merged.values().iterator();
					while (it.hasNext()) {
						if (isNullOrEmpty(it.next())) it.remove();
					}
					changed = true;
				} else {
					BdfList message = clientHelper.getMessageAsList(txn,
							latest.messageId);
					TransportProperties old = parseProperties(message);
					merged = new TransportProperties(old);
					for (Entry<String, String> e : p.entrySet()) {
						String key = e.getKey(), value = e.getValue();
						if (isNullOrEmpty(value)) merged.remove(key);
						else merged.put(key, value);
					}
					changed = !merged.equals(old);
				}
				if (changed) {
					// Store the merged properties in the local group
					long version = latest == null ? 1 : latest.version + 1;
					storeMessage(txn, localGroup.getId(), t, merged, version,
							true, false);
					// Delete the previous update, if any
					if (latest != null) db.removeMessage(txn, latest.messageId);
					// Store the merged properties in each contact's group
					for (Contact c : db.getContacts(txn)) {
						storeLocalProperties(txn, c, t, merged);
					}
				}
			});
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void storeLocalProperties(Transaction txn, Contact c,
			TransportId t, TransportProperties p)
			throws DbException, FormatException {
		Group g = getContactGroup(c);
		LatestUpdate latest = findLatest(txn, g.getId(), t, true);
		long version = latest == null ? 1 : latest.version + 1;
		// Reflect any remote properties we've discovered
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn,
				g.getId());
		BdfDictionary discovered =
				meta.getOptionalDictionary(GROUP_KEY_DISCOVERED);
		TransportProperties combined;
		if (discovered == null) {
			combined = p;
		} else {
			combined = new TransportProperties(p);
			TransportProperties d = clientHelper
					.parseAndValidateTransportProperties(discovered);
			for (Entry<String, String> e : d.entrySet()) {
				String key = REFLECTED_PROPERTY_PREFIX + e.getKey();
				combined.put(key, e.getValue());
			}
		}
		storeMessage(txn, g.getId(), t, combined, version, true, true);
		// Delete the previous update, if any
		if (latest != null) db.removeMessage(txn, latest.messageId);
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	private void storeMessage(Transaction txn, GroupId g, TransportId t,
			TransportProperties p, long version, boolean local, boolean shared)
			throws DbException {
		try {
			BdfList body = encodeProperties(t, p, version);
			long now = clock.currentTimeMillis();
			Message m = clientHelper.createMessage(g, now, body);
			BdfDictionary meta = new BdfDictionary();
			meta.put(MSG_KEY_TRANSPORT_ID, t.getString());
			meta.put(MSG_KEY_VERSION, version);
			meta.put(MSG_KEY_LOCAL, local);
			clientHelper.addLocalMessage(txn, m, meta, shared, false);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private BdfList encodeProperties(TransportId t, TransportProperties p,
			long version) {
		return BdfList.of(t.getString(), version, p);
	}

	private Map<TransportId, LatestUpdate> findLatestLocal(Transaction txn)
			throws DbException, FormatException {
		Map<TransportId, LatestUpdate> latestUpdates = new HashMap<>();
		Map<MessageId, BdfDictionary> metadata = clientHelper
				.getMessageMetadataAsDictionary(txn, localGroup.getId());
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			TransportId t =
					new TransportId(meta.getString(MSG_KEY_TRANSPORT_ID));
			long version = meta.getLong(MSG_KEY_VERSION);
			latestUpdates.put(t, new LatestUpdate(e.getKey(), version));
		}
		return latestUpdates;
	}

	@Nullable
	private LatestUpdate findLatest(Transaction txn, GroupId g, TransportId t,
			boolean local) throws DbException, FormatException {
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			if (meta.getString(MSG_KEY_TRANSPORT_ID).equals(t.getString())
					&& meta.getBoolean(MSG_KEY_LOCAL) == local) {
				return new LatestUpdate(e.getKey(),
						meta.getLong(MSG_KEY_VERSION));
			}
		}
		return null;
	}

	private TransportProperties parseProperties(BdfList message)
			throws FormatException {
		// Transport ID, version, properties
		BdfDictionary dictionary = message.getDictionary(2);
		return clientHelper.parseAndValidateTransportProperties(dictionary);
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
