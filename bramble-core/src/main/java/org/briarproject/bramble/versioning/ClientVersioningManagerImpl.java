package org.briarproject.bramble.versioning;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager.ContactHook;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Client;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.ValidationManager.IncomingMessageHook;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientMajorVersion;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_LOCAL;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_UPDATE_VERSION;

@NotNullByDefault
class ClientVersioningManagerImpl implements ClientVersioningManager, Client,
		Service, ContactHook, IncomingMessageHook {

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final ContactGroupFactory contactGroupFactory;
	private final Clock clock;
	private final Group localGroup;

	private final List<ClientVersion> clients = new CopyOnWriteArrayList<>();
	private final Map<ClientMajorVersion, ClientVersioningHook> hooks =
			new ConcurrentHashMap<>();

	@Inject
	ClientVersioningManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			ContactGroupFactory contactGroupFactory, Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.contactGroupFactory = contactGroupFactory;
		this.clock = clock;
		localGroup = contactGroupFactory.createLocalGroup(CLIENT_ID,
				MAJOR_VERSION);
	}

	@Override
	public void registerClient(ClientId clientId, int majorVersion,
			int minorVersion, ClientVersioningHook hook) {
		ClientMajorVersion cv = new ClientMajorVersion(clientId, majorVersion);
		clients.add(new ClientVersion(cv, minorVersion));
		hooks.put(cv, hook);
	}

	@Override
	public Visibility getClientVisibility(Transaction txn, ContactId contactId,
			ClientId clientId, int majorVersion) throws DbException {
		try {
			Contact contact = db.getContact(txn, contactId);
			Group g = getContactGroup(contact);
			// Contact may be in the process of being added or removed, so
			// contact group may not exist
			if (!db.containsGroup(txn, g.getId())) return INVISIBLE;
			LatestUpdates latest = findLatestUpdates(txn, g.getId());
			if (latest.local == null) throw new DbException();
			if (latest.remote == null) return INVISIBLE;
			Update localUpdate = loadUpdate(txn, latest.local.messageId);
			Update remoteUpdate = loadUpdate(txn, latest.remote.messageId);
			Map<ClientMajorVersion, Visibility> visibilities =
					getVisibilities(localUpdate.states, remoteUpdate.states);
			ClientMajorVersion cv =
					new ClientMajorVersion(clientId, majorVersion);
			Visibility v = visibilities.get(cv);
			return v == null ? INVISIBLE : v;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void createLocalState(Transaction txn) throws DbException {
		if (db.containsGroup(txn, localGroup.getId())) return;
		db.addGroup(txn, localGroup);
		// Set things up for any pre-existing contacts
		for (Contact c : db.getContacts(txn)) addingContact(txn, c);
	}

	@Override
	public void startService() throws ServiceException {
		List<ClientVersion> versions = new ArrayList<>(clients);
		Collections.sort(versions);
		try {
			Transaction txn = db.startTransaction(false);
			try {
				if (updateClientVersions(txn, versions)) {
					for (Contact c : db.getContacts(txn))
						clientVersionsUpdated(txn, c, versions);
				}
				db.commitTransaction(txn);
			} finally {
				db.endTransaction(txn);
			}
		} catch (DbException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() throws ServiceException {
	}

	@Override
	public void addingContact(Transaction txn, Contact c) throws DbException {
		// Create a group and share it with the contact
		Group g = getContactGroup(c);
		db.addGroup(txn, g);
		db.setGroupVisibility(txn, c.getId(), g.getId(), SHARED);
		// Attach the contact ID to the group
		BdfDictionary meta = new BdfDictionary();
		meta.put(GROUP_KEY_CONTACT_ID, c.getId().getInt());
		try {
			clientHelper.mergeGroupMetadata(txn, g.getId(), meta);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
		// Create and store the first local update
		List<ClientVersion> versions = new ArrayList<>(clients);
		Collections.sort(versions);
		storeFirstUpdate(txn, g.getId(), versions);
	}

	@Override
	public void removingContact(Transaction txn, Contact c) throws DbException {
		db.removeGroup(txn, getContactGroup(c));
	}

	@Override
	public boolean incomingMessage(Transaction txn, Message m, Metadata meta)
			throws DbException, InvalidMessageException {
		try {
			// Parse the new remote update
			Update newRemoteUpdate = parseUpdate(clientHelper.toList(m));
			List<ClientState> newRemoteStates = newRemoteUpdate.states;
			long newRemoteUpdateVersion = newRemoteUpdate.updateVersion;
			// Find the latest local and remote updates, if any
			LatestUpdates latest = findLatestUpdates(txn, m.getGroupId());
			// If this update is obsolete, delete it and return
			if (latest.remote != null
					&& latest.remote.updateVersion > newRemoteUpdateVersion) {
				db.deleteMessage(txn, m.getId());
				db.deleteMessageMetadata(txn, m.getId());
				return false;
			}
			// Load and parse the latest local update
			if (latest.local == null) throw new DbException();
			Update oldLocalUpdate = loadUpdate(txn, latest.local.messageId);
			List<ClientState> oldLocalStates = oldLocalUpdate.states;
			long oldLocalUpdateVersion = oldLocalUpdate.updateVersion;
			// Load and parse the previous remote update, if any
			List<ClientState> oldRemoteStates;
			if (latest.remote == null) {
				oldRemoteStates = emptyList();
			} else {
				oldRemoteStates =
						loadUpdate(txn, latest.remote.messageId).states;
				// Delete the previous remote update
				db.deleteMessage(txn, latest.remote.messageId);
				db.deleteMessageMetadata(txn, latest.remote.messageId);
			}
			// Update the local states from the remote states if necessary
			List<ClientState> newLocalStates = updateStatesFromRemoteStates(
					oldLocalStates, newRemoteStates);
			if (!oldLocalStates.equals(newLocalStates)) {
				// Delete the latest local update
				db.deleteMessage(txn, latest.local.messageId);
				db.deleteMessageMetadata(txn, latest.local.messageId);
				// Store a new local update
				storeUpdate(txn, m.getGroupId(), newLocalStates,
						oldLocalUpdateVersion + 1);
			}
			// Calculate the old and new client visibilities
			Map<ClientMajorVersion, Visibility> before =
					getVisibilities(oldLocalStates, oldRemoteStates);
			Map<ClientMajorVersion, Visibility> after =
					getVisibilities(newLocalStates, newRemoteStates);
			// Call hooks for any visibilities that have changed
			if (!before.equals(after)) {
				Contact c = getContact(txn, m.getGroupId());
				callVisibilityHooks(txn, c, before, after);
			}
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
		return false;
	}

	private void storeClientVersions(Transaction txn,
			List<ClientVersion> versions) throws DbException {
		long now = clock.currentTimeMillis();
		BdfList body = encodeClientVersions(versions);
		try {
			Message m = clientHelper.createMessage(localGroup.getId(), now,
					body);
			db.addLocalMessage(txn, m, new Metadata(), false);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	private BdfList encodeClientVersions(List<ClientVersion> versions) {
		BdfList encoded = new BdfList();
		for (ClientVersion cv : versions) encoded.add(encodeClientVersion(cv));
		return encoded;
	}

	private BdfList encodeClientVersion(ClientVersion cv) {
		return BdfList.of(cv.majorVersion.getClientId().getString(),
				cv.majorVersion.getMajorVersion(), cv.minorVersion);
	}

	/**
	 * Stores the local client versions and returns true if an update needs to
	 * be sent to contacts.
	 */
	private boolean updateClientVersions(Transaction txn,
			List<ClientVersion> newVersions) throws DbException {
		Collection<MessageId> ids = db.getMessageIds(txn, localGroup.getId());
		if (ids.isEmpty()) {
			storeClientVersions(txn, newVersions);
			return true;
		}
		if (ids.size() != 1) throw new DbException();
		MessageId m = ids.iterator().next();
		List<ClientVersion> oldVersions = loadClientVersions(txn, m);
		if (oldVersions.equals(newVersions)) return false;
		db.removeMessage(txn, m);
		storeClientVersions(txn, newVersions);
		return true;
	}

	private List<ClientVersion> loadClientVersions(Transaction txn,
			MessageId m) throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(txn, m);
			if (body == null) throw new DbException();
			return parseClientVersions(body);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private List<ClientVersion> parseClientVersions(BdfList body)
			throws FormatException {
		int size = body.size();
		List<ClientVersion> parsed = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			BdfList cv = body.getList(i);
			ClientId clientId = new ClientId(cv.getString(0));
			int majorVersion = cv.getLong(1).intValue();
			int minorVersion = cv.getLong(2).intValue();
			parsed.add(new ClientVersion(clientId, majorVersion,
					minorVersion));
		}
		return parsed;
	}

	private void clientVersionsUpdated(Transaction txn, Contact c,
			List<ClientVersion> versions) throws DbException {
		try {
			// Find the latest local and remote updates
			Group g = getContactGroup(c);
			LatestUpdates latest = findLatestUpdates(txn, g.getId());
			// Load and parse the latest local update
			if (latest.local == null) throw new DbException();
			Update oldLocalUpdate = loadUpdate(txn, latest.local.messageId);
			List<ClientState> oldLocalStates = oldLocalUpdate.states;
			long oldLocalUpdateVersion = oldLocalUpdate.updateVersion;
			// Load and parse the latest remote update, if any
			List<ClientState> remoteStates;
			if (latest.remote == null) remoteStates = emptyList();
			else remoteStates = loadUpdate(txn, latest.remote.messageId).states;
			// Update the local states if necessary
			List<ClientState> newLocalStates =
					updateStatesFromLocalVersions(oldLocalStates, versions);
			newLocalStates = updateStatesFromRemoteStates(newLocalStates,
					remoteStates);
			if (!oldLocalStates.equals(newLocalStates)) {
				// Delete the latest local update
				db.deleteMessage(txn, latest.local.messageId);
				db.deleteMessageMetadata(txn, latest.local.messageId);
				// Store a new local update
				storeUpdate(txn, g.getId(), newLocalStates,
						oldLocalUpdateVersion + 1);
			}
			// Calculate the old and new client visibilities
			Map<ClientMajorVersion, Visibility> before =
					getVisibilities(oldLocalStates, remoteStates);
			Map<ClientMajorVersion, Visibility> after =
					getVisibilities(newLocalStates, remoteStates);
			// Call hooks for any visibilities that have changed
			callVisibilityHooks(txn, c, before, after);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Group getContactGroup(Contact c) {
		return contactGroupFactory.createContactGroup(CLIENT_ID,
				MAJOR_VERSION, c);
	}

	private LatestUpdates findLatestUpdates(Transaction txn, GroupId g)
			throws DbException, FormatException {
		Map<MessageId, BdfDictionary> metadata =
				clientHelper.getMessageMetadataAsDictionary(txn, g);
		LatestUpdate local = null, remote = null;
		for (Entry<MessageId, BdfDictionary> e : metadata.entrySet()) {
			BdfDictionary meta = e.getValue();
			long updateVersion = meta.getLong(MSG_KEY_UPDATE_VERSION);
			if (meta.getBoolean(MSG_KEY_LOCAL))
				local = new LatestUpdate(e.getKey(), updateVersion);
			else remote = new LatestUpdate(e.getKey(), updateVersion);
		}
		return new LatestUpdates(local, remote);
	}

	private Update loadUpdate(Transaction txn, MessageId m) throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(txn, m);
			if (body == null) throw new DbException();
			return parseUpdate(body);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private Update parseUpdate(BdfList body) throws FormatException {
		List<ClientState> states = parseClientStates(body);
		long updateVersion = parseUpdateVersion(body);
		return new Update(states, updateVersion);
	}

	private List<ClientState> parseClientStates(BdfList body)
			throws FormatException {
		// Client states, update version
		BdfList states = body.getList(0);
		int size = states.size();
		List<ClientState> parsed = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			parsed.add(parseClientState(states.getList(i)));
		return parsed;
	}

	private ClientState parseClientState(BdfList clientState)
			throws FormatException {
		// Client ID, major version, minor version, active
		ClientId clientId = new ClientId(clientState.getString(0));
		int majorVersion = clientState.getLong(1).intValue();
		int minorVersion = clientState.getLong(2).intValue();
		boolean active = clientState.getBoolean(3);
		return new ClientState(clientId, majorVersion, minorVersion, active);
	}

	private long parseUpdateVersion(BdfList body) throws FormatException {
		// Client states, update version
		return body.getLong(1);
	}

	private List<ClientState> updateStatesFromLocalVersions(
			List<ClientState> oldStates, List<ClientVersion> newVersions) {
		Map<ClientMajorVersion, ClientState> oldMap = new HashMap<>();
		for (ClientState cs : oldStates) oldMap.put(cs.majorVersion, cs);
		List<ClientState> newStates = new ArrayList<>(newVersions.size());
		for (ClientVersion newVersion : newVersions) {
			ClientState oldState = oldMap.get(newVersion.majorVersion);
			boolean active = oldState != null && oldState.active;
			newStates.add(new ClientState(newVersion.majorVersion,
					newVersion.minorVersion, active));
		}
		return newStates;
	}

	private void storeUpdate(Transaction txn, GroupId g,
			List<ClientState> states, long updateVersion) throws DbException {
		try {
			BdfList body = encodeUpdate(states, updateVersion);
			long now = clock.currentTimeMillis();
			Message m = clientHelper.createMessage(g, now, body);
			BdfDictionary meta = new BdfDictionary();
			meta.put(MSG_KEY_UPDATE_VERSION, updateVersion);
			meta.put(MSG_KEY_LOCAL, true);
			clientHelper.addLocalMessage(txn, m, meta, true);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	private BdfList encodeUpdate(List<ClientState> states, long updateVersion) {
		BdfList encoded = new BdfList();
		for (ClientState cs : states) encoded.add(encodeClientState(cs));
		return BdfList.of(encoded, updateVersion);
	}

	private BdfList encodeClientState(ClientState cs) {
		return BdfList.of(cs.majorVersion.getClientId().getString(),
				cs.majorVersion.getMajorVersion(), cs.minorVersion, cs.active);
	}

	private Map<ClientMajorVersion, Visibility> getVisibilities(
			List<ClientState> localStates, List<ClientState> remoteStates) {
		Map<ClientMajorVersion, ClientState> remoteMap = new HashMap<>();
		for (ClientState cs : remoteStates) remoteMap.put(cs.majorVersion, cs);
		Map<ClientMajorVersion, Visibility> visibilities = new HashMap<>();
		for (ClientState local : localStates) {
			ClientState remote = remoteMap.get(local.majorVersion);
			if (remote == null) visibilities.put(local.majorVersion, INVISIBLE);
			else if (remote.active)
				visibilities.put(local.majorVersion, SHARED);
			else visibilities.put(local.majorVersion, VISIBLE);
		}
		return visibilities;
	}

	private void callVisibilityHooks(Transaction txn, Contact c,
			Map<ClientMajorVersion, Visibility> before,
			Map<ClientMajorVersion, Visibility> after) throws DbException {
		Set<ClientMajorVersion> keys = new TreeSet<>();
		keys.addAll(before.keySet());
		keys.addAll(after.keySet());
		for (ClientMajorVersion cv : keys) {
			Visibility vBefore = before.get(cv), vAfter = after.get(cv);
			if (vAfter == null) {
				callVisibilityHook(txn, cv, c, INVISIBLE);
			} else if (vBefore == null || !vBefore.equals(vAfter)) {
				callVisibilityHook(txn, cv, c, vAfter);
			}
		}
	}

	private void callVisibilityHook(Transaction txn, ClientMajorVersion cv,
			Contact c, Visibility v) throws DbException {
		ClientVersioningHook hook = hooks.get(cv);
		if (hook != null) hook.onClientVisibilityChanging(txn, c, v);
	}

	private void storeFirstUpdate(Transaction txn, GroupId g,
			List<ClientVersion> versions) throws DbException {
		List<ClientState> states = new ArrayList<>(versions.size());
		for (ClientVersion cv : versions) {
			states.add(new ClientState(cv.majorVersion, cv.minorVersion,
					false));
		}
		storeUpdate(txn, g, states, 1);
	}

	private Contact getContact(Transaction txn, GroupId g) throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			int id = meta.getLong(GROUP_KEY_CONTACT_ID).intValue();
			return db.getContact(txn, new ContactId(id));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private List<ClientState> updateStatesFromRemoteStates(
			List<ClientState> oldLocalStates, List<ClientState> remoteStates) {
		Set<ClientMajorVersion> remoteSet = new HashSet<>();
		for (ClientState cs : remoteStates) remoteSet.add(cs.majorVersion);
		List<ClientState> newLocalStates =
				new ArrayList<>(oldLocalStates.size());
		for (ClientState oldState : oldLocalStates) {
			boolean active = remoteSet.contains(oldState.majorVersion);
			newLocalStates.add(new ClientState(oldState.majorVersion,
					oldState.minorVersion, active));
		}
		return newLocalStates;
	}

	private static class Update {

		private final List<ClientState> states;
		private final long updateVersion;

		private Update(List<ClientState> states, long updateVersion) {
			this.states = states;
			this.updateVersion = updateVersion;
		}
	}

	private static class LatestUpdate {

		private final MessageId messageId;
		private final long updateVersion;

		private LatestUpdate(MessageId messageId, long updateVersion) {
			this.messageId = messageId;
			this.updateVersion = updateVersion;
		}
	}

	private static class LatestUpdates {

		@Nullable
		private final LatestUpdate local, remote;

		private LatestUpdates(@Nullable LatestUpdate local,
				@Nullable LatestUpdate remote) {
			this.local = local;
			this.remote = remote;
		}
	}

	private static class ClientVersion implements Comparable<ClientVersion> {

		private final ClientMajorVersion majorVersion;
		private final int minorVersion;

		private ClientVersion(ClientMajorVersion majorVersion,
				int minorVersion) {
			this.majorVersion = majorVersion;
			this.minorVersion = minorVersion;
		}

		private ClientVersion(ClientId clientId, int majorVersion,
				int minorVersion) {
			this(new ClientMajorVersion(clientId, majorVersion), minorVersion);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ClientVersion) {
				ClientVersion cv = (ClientVersion) o;
				return majorVersion.equals(cv.majorVersion)
						&& minorVersion == cv.minorVersion;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return majorVersion.hashCode();
		}

		@Override
		public int compareTo(ClientVersion cv) {
			int compare = majorVersion.compareTo(cv.majorVersion);
			if (compare != 0) return compare;
			return minorVersion - cv.minorVersion;
		}
	}

	private static class ClientState {

		private final ClientMajorVersion majorVersion;
		private final int minorVersion;
		private final boolean active;

		private ClientState(ClientMajorVersion majorVersion, int minorVersion,
				boolean active) {
			this.majorVersion = majorVersion;
			this.minorVersion = minorVersion;
			this.active = active;
		}

		private ClientState(ClientId clientId, int majorVersion,
				int minorVersion, boolean active) {
			this(new ClientMajorVersion(clientId, majorVersion), minorVersion,
					active);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ClientState) {
				ClientState cs = (ClientState) o;
				return majorVersion.equals(cs.majorVersion)
						&& minorVersion == cs.minorVersion
						&& active == cs.active;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return majorVersion.hashCode();
		}
	}
}
