package org.briarproject.bramble.versioning;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.api.sync.Group.Visibility.INVISIBLE;
import static org.briarproject.bramble.api.sync.Group.Visibility.SHARED;
import static org.briarproject.bramble.api.sync.Group.Visibility.VISIBLE;
import static org.briarproject.bramble.api.versioning.ClientVersioningManager.CLIENT_ID;
import static org.briarproject.bramble.api.versioning.ClientVersioningManager.MAJOR_VERSION;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getClientId;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.bramble.test.TestUtils.getLocalAuthor;
import static org.briarproject.bramble.test.TestUtils.getMessage;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_LOCAL;
import static org.briarproject.bramble.versioning.ClientVersioningConstants.MSG_KEY_UPDATE_VERSION;
import static org.junit.Assert.assertFalse;

public class ClientVersioningManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);
	private final Clock clock = context.mock(Clock.class);
	private final ClientVersioningHook hook =
			context.mock(ClientVersioningHook.class);

	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Contact contact = new Contact(new ContactId(123),
			getAuthor(), getLocalAuthor().getId(), true, true);
	private final ClientId clientId = getClientId();
	private final long now = System.currentTimeMillis();
	private final Transaction txn = new Transaction(null, false);

	private ClientVersioningManagerImpl createInstance() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createLocalGroup(CLIENT_ID,
					MAJOR_VERSION);
			will(returnValue(localGroup));
		}});
		return new ClientVersioningManagerImpl(db, clientHelper,
				contactGroupFactory, clock);
	}

	@Test
	public void testCreatesGroupsAtStartup() throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
		}});
		expectAddingContact();

		ClientVersioningManagerImpl c = createInstance();
		c.createLocalState(txn);
	}

	@Test
	public void testDoesNotCreateGroupsAtStartupIfAlreadyCreated()
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.createLocalState(txn);
	}

	@Test
	public void testCreatesContactGroupWhenAddingContact() throws Exception {
		expectAddingContact();

		ClientVersioningManagerImpl c = createInstance();
		c.addingContact(txn, contact);
	}

	private void expectAddingContact() throws Exception {
		BdfDictionary groupMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()));
		long now = System.currentTimeMillis();
		BdfList localUpdateBody = BdfList.of(new BdfList(), 1L);
		Message localUpdate = getMessage(contactGroup.getId());
		BdfDictionary localUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));

		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(db).setGroupVisibility(txn, contact.getId(),
					contactGroup.getId(), SHARED);
			oneOf(clientHelper).mergeGroupMetadata(txn, contactGroup.getId(),
					groupMeta);
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(contactGroup.getId(), now,
					localUpdateBody);
			will(returnValue(localUpdate));
			oneOf(clientHelper).addLocalMessage(txn, localUpdate,
					localUpdateMeta, true);
		}});
	}

	@Test
	public void testRemovesGroupWhenRemovingContact() throws Exception {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			oneOf(db).removeGroup(txn, contactGroup);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.removingContact(txn, contact);
	}

	@Test
	public void testStoresClientVersionsAtFirstStartup() throws Exception {
		BdfList localVersionsBody =
				BdfList.of(BdfList.of(clientId.getString(), 123, 234));
		Message localVersions = getMessage(localGroup.getId());
		MessageId localUpdateId = new MessageId(getRandomId());
		BdfDictionary localUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		BdfList localUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, false)), 1L);

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// No client versions have been stored yet
			oneOf(db).getMessageIds(txn, localGroup.getId());
			will(returnValue(emptyList()));
			// Store the client versions
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(localGroup.getId(), now,
					localVersionsBody);
			will(returnValue(localVersions));
			oneOf(db).addLocalMessage(txn, localVersions, new Metadata(),
					false);
			// Inform contacts that client versions have changed
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			// Find the latest local and remote updates (no remote update)
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(singletonMap(localUpdateId, localUpdateMeta)));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, localUpdateId);
			will(returnValue(localUpdateBody));
			// Latest local update is up-to-date, no visibilities have changed
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		c.startService();
	}

	@Test
	public void testComparesClientVersionsAtSubsequentStartup()
			throws Exception {
		MessageId localVersionsId = new MessageId(getRandomId());
		BdfList localVersionsBody =
				BdfList.of(BdfList.of(clientId.getString(), 123, 234));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Load the old client versions
			oneOf(db).getMessageIds(txn, localGroup.getId());
			will(returnValue(singletonList(localVersionsId)));
			oneOf(clientHelper).getMessageAsList(txn, localVersionsId);
			will(returnValue(localVersionsBody));
			// Client versions are up-to-date
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		c.startService();
	}

	@Test
	public void testStoresClientVersionsAtSubsequentStartupIfChanged()
			throws Exception {
		// The client had minor version 234 in the old client versions
		BdfList oldLocalVersionsBody =
				BdfList.of(BdfList.of(clientId.getString(), 123, 234));
		// The client has minor version 345 in the new client versions
		BdfList newLocalVersionsBody =
				BdfList.of(BdfList.of(clientId.getString(), 123, 345));
		// The client had minor version 234 in the old local update
		BdfList oldLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, false)), 1L);
		// The client has minor version 345 in the new local update
		BdfList newLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 345, false)), 2L);

		MessageId oldLocalVersionsId = new MessageId(getRandomId());
		Message newLocalVersions = getMessage(localGroup.getId());
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		Message newLocalUpdate = getMessage(contactGroup.getId());
		BdfDictionary newLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 2L),
				new BdfEntry(MSG_KEY_LOCAL, true));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Load the old client versions
			oneOf(db).getMessageIds(txn, localGroup.getId());
			will(returnValue(singletonList(oldLocalVersionsId)));
			oneOf(clientHelper).getMessageAsList(txn, oldLocalVersionsId);
			will(returnValue(oldLocalVersionsBody));
			// Delete the old client versions
			oneOf(db).removeMessage(txn, oldLocalVersionsId);
			// Store the new client versions
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(localGroup.getId(), now,
					newLocalVersionsBody);
			will(returnValue(newLocalVersions));
			oneOf(db).addLocalMessage(txn, newLocalVersions, new Metadata(),
					false);
			// Inform contacts that client versions have changed
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			// Find the latest local and remote updates (no remote update)
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(singletonMap(oldLocalUpdateId,
					oldLocalUpdateMeta)));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// Delete the latest local update
			oneOf(db).deleteMessage(txn, oldLocalUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldLocalUpdateId);
			// Store the new local update
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(contactGroup.getId(), now,
					newLocalUpdateBody);
			will(returnValue(newLocalUpdate));
			oneOf(clientHelper).addLocalMessage(txn, newLocalUpdate,
					newLocalUpdateMeta, true);
			// No visibilities have changed
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 345, hook);
		c.startService();
	}

	@Test
	public void testActivatesNewClientAtStartupIfAlreadyAdvertisedByContact()
			throws Exception {
		testActivatesNewClientAtStartup(false, VISIBLE);
	}

	@Test
	public void testActivatesNewClientAtStartupIfAlreadyActivatedByContact()
			throws Exception {
		testActivatesNewClientAtStartup(true, SHARED);
	}

	private void testActivatesNewClientAtStartup(boolean remoteActive,
			Visibility visibility) throws Exception {
		// The client was missing from the old client versions
		BdfList oldLocalVersionsBody = new BdfList();
		// The client is included in the new client versions
		BdfList newLocalVersionsBody =
				BdfList.of(BdfList.of(clientId.getString(), 123, 234));
		// The client was missing from the old local update
		BdfList oldLocalUpdateBody = BdfList.of(new BdfList(), 1L);
		// The client was included in the old remote update
		BdfList oldRemoteUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 345, remoteActive)), 1L);
		// The client is active in the new local update
		BdfList newLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, true)), 2L);

		MessageId oldLocalVersionsId = new MessageId(getRandomId());
		Message newLocalVersions = getMessage(localGroup.getId());
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		MessageId oldRemoteUpdateId = new MessageId(getRandomId());
		BdfDictionary oldRemoteUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		Map<MessageId, BdfDictionary> messageMetadata = new HashMap<>();
		messageMetadata.put(oldLocalUpdateId, oldLocalUpdateMeta);
		messageMetadata.put(oldRemoteUpdateId, oldRemoteUpdateMeta);
		Message newLocalUpdate = getMessage(localGroup.getId());
		BdfDictionary newLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 2L),
				new BdfEntry(MSG_KEY_LOCAL, true));

		context.checking(new Expectations() {{
			oneOf(db).startTransaction(false);
			will(returnValue(txn));
			// Load the old client versions
			oneOf(db).getMessageIds(txn, localGroup.getId());
			will(returnValue(singletonList(oldLocalVersionsId)));
			oneOf(clientHelper).getMessageAsList(txn, oldLocalVersionsId);
			will(returnValue(oldLocalVersionsBody));
			// Delete the old client versions
			oneOf(db).removeMessage(txn, oldLocalVersionsId);
			// Store the new client versions
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(localGroup.getId(), now,
					newLocalVersionsBody);
			will(returnValue(newLocalVersions));
			oneOf(db).addLocalMessage(txn, newLocalVersions, new Metadata(),
					false);
			// Inform contacts that client versions have changed
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
			oneOf(contactGroupFactory).createContactGroup(CLIENT_ID,
					MAJOR_VERSION, contact);
			will(returnValue(contactGroup));
			// Find the latest local and remote updates
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// Load the latest remote update
			oneOf(clientHelper).getMessageAsList(txn, oldRemoteUpdateId);
			will(returnValue(oldRemoteUpdateBody));
			// Delete the latest local update
			oneOf(db).deleteMessage(txn, oldLocalUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldLocalUpdateId);
			// Store the new local update
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(contactGroup.getId(), now,
					newLocalUpdateBody);
			will(returnValue(newLocalUpdate));
			oneOf(clientHelper).addLocalMessage(txn, newLocalUpdate,
					newLocalUpdateMeta, true);
			// The client's visibility has changed
			oneOf(hook).onClientVisibilityChanging(txn, contact, visibility);
			oneOf(db).commitTransaction(txn);
			oneOf(db).endTransaction(txn);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		c.startService();
	}

	@Test
	public void testDeletesObsoleteRemoteUpdate() throws Exception {
		Message newRemoteUpdate = getMessage(contactGroup.getId());
		BdfList newRemoteUpdateBody = BdfList.of(new BdfList(), 1L);
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		MessageId oldRemoteUpdateId = new MessageId(getRandomId());
		BdfDictionary oldRemoteUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 2L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		Map<MessageId, BdfDictionary> messageMetadata = new HashMap<>();
		messageMetadata.put(oldLocalUpdateId, oldLocalUpdateMeta);
		messageMetadata.put(oldRemoteUpdateId, oldRemoteUpdateMeta);

		context.checking(new Expectations() {{
			oneOf(clientHelper).toList(newRemoteUpdate);
			will(returnValue(newRemoteUpdateBody));
			// Find the latest local and remote updates
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			// Delete the new remote update, which is obsolete
			oneOf(db).deleteMessage(txn, newRemoteUpdate.getId());
			oneOf(db).deleteMessageMetadata(txn, newRemoteUpdate.getId());
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		assertFalse(c.incomingMessage(txn, newRemoteUpdate, new Metadata()));
	}

	@Test
	public void testDeletesPreviousRemoteUpdate() throws Exception {
		Message newRemoteUpdate = getMessage(contactGroup.getId());
		BdfList newRemoteUpdateBody = BdfList.of(new BdfList(), 2L);
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		MessageId oldRemoteUpdateId = new MessageId(getRandomId());
		BdfDictionary oldRemoteUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		Map<MessageId, BdfDictionary> messageMetadata = new HashMap<>();
		messageMetadata.put(oldLocalUpdateId, oldLocalUpdateMeta);
		messageMetadata.put(oldRemoteUpdateId, oldRemoteUpdateMeta);
		BdfList oldLocalUpdateBody = BdfList.of(new BdfList(), 1L);
		BdfList oldRemoteUpdateBody = BdfList.of(new BdfList(), 1L);

		context.checking(new Expectations() {{
			oneOf(clientHelper).toList(newRemoteUpdate);
			will(returnValue(newRemoteUpdateBody));
			// Find the latest local and remote updates
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// Load the latest remote update
			oneOf(clientHelper).getMessageAsList(txn, oldRemoteUpdateId);
			will(returnValue(oldRemoteUpdateBody));
			// Delete the old remote update
			oneOf(db).deleteMessage(txn, oldRemoteUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldRemoteUpdateId);
			// No states or visibilities have changed
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		assertFalse(c.incomingMessage(txn, newRemoteUpdate, new Metadata()));
	}

	@Test
	public void testAcceptsFirstRemoteUpdate() throws Exception {
		Message newRemoteUpdate = getMessage(contactGroup.getId());
		BdfList newRemoteUpdateBody = BdfList.of(new BdfList(), 1L);
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		BdfList oldLocalUpdateBody = BdfList.of(new BdfList(), 1L);

		context.checking(new Expectations() {{
			oneOf(clientHelper).toList(newRemoteUpdate);
			will(returnValue(newRemoteUpdateBody));
			// Find the latest local and remote updates (no remote update)
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(singletonMap(oldLocalUpdateId,
					oldLocalUpdateMeta)));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// No states or visibilities have changed
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		assertFalse(c.incomingMessage(txn, newRemoteUpdate, new Metadata()));
	}

	@Test
	public void testActivatesClientOnIncomingMessageWhenAdvertisedByContact()
			throws Exception {
		testActivatesClientOnIncomingMessage(false, VISIBLE);
	}

	@Test
	public void testActivatesClientOnIncomingMessageWhenActivatedByContact()
			throws Exception {
		testActivatesClientOnIncomingMessage(true, SHARED);
	}

	private void testActivatesClientOnIncomingMessage(boolean remoteActive,
			Visibility visibility) throws Exception {
		// The client was missing from the old remote update
		BdfList oldRemoteUpdateBody = BdfList.of(new BdfList(), 1L);
		// The client was inactive in the old local update
		BdfList oldLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, false)), 1L);
		// The client is included in the new remote update
		BdfList newRemoteUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, remoteActive)), 2L);
		// The client is active in the new local update
		BdfList newLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, true)), 2L);

		Message newRemoteUpdate = getMessage(contactGroup.getId());
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		MessageId oldRemoteUpdateId = new MessageId(getRandomId());
		BdfDictionary oldRemoteUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		Map<MessageId, BdfDictionary> messageMetadata = new HashMap<>();
		messageMetadata.put(oldLocalUpdateId, oldLocalUpdateMeta);
		messageMetadata.put(oldRemoteUpdateId, oldRemoteUpdateMeta);
		Message newLocalUpdate = getMessage(contactGroup.getId());
		BdfDictionary newLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 2L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		BdfDictionary groupMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()));

		context.checking(new Expectations() {{
			oneOf(clientHelper).toList(newRemoteUpdate);
			will(returnValue(newRemoteUpdateBody));
			// Find the latest local and remote updates
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// Load the latest remote update
			oneOf(clientHelper).getMessageAsList(txn, oldRemoteUpdateId);
			will(returnValue(oldRemoteUpdateBody));
			// Delete the old remote update
			oneOf(db).deleteMessage(txn, oldRemoteUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldRemoteUpdateId);
			// Delete the old local update
			oneOf(db).deleteMessage(txn, oldLocalUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldLocalUpdateId);
			// Store the new local update
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(contactGroup.getId(), now,
					newLocalUpdateBody);
			will(returnValue(newLocalUpdate));
			oneOf(clientHelper).addLocalMessage(txn, newLocalUpdate,
					newLocalUpdateMeta, true);
			// The client's visibility has changed
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(groupMeta));
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(hook).onClientVisibilityChanging(txn, contact, visibility);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		assertFalse(c.incomingMessage(txn, newRemoteUpdate, new Metadata()));
	}

	@Test
	public void testDeactivatesClientOnIncomingMessage() throws Exception {
		// The client was active in the old local and remote updates
		BdfList oldLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, true)), 1L);
		BdfList oldRemoteUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, true)), 1L);
		// The client is missing from the new remote update
		BdfList newRemoteUpdateBody = BdfList.of(new BdfList(), 2L);
		// The client is inactive in the new local update
		BdfList newLocalUpdateBody = BdfList.of(BdfList.of(
				BdfList.of(clientId.getString(), 123, 234, false)), 2L);

		Message newRemoteUpdate = getMessage(contactGroup.getId());
		MessageId oldLocalUpdateId = new MessageId(getRandomId());
		BdfDictionary oldLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		MessageId oldRemoteUpdateId = new MessageId(getRandomId());
		BdfDictionary oldRemoteUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 1L),
				new BdfEntry(MSG_KEY_LOCAL, false));
		Map<MessageId, BdfDictionary> messageMetadata = new HashMap<>();
		messageMetadata.put(oldLocalUpdateId, oldLocalUpdateMeta);
		messageMetadata.put(oldRemoteUpdateId, oldRemoteUpdateMeta);
		Message newLocalUpdate = getMessage(contactGroup.getId());
		BdfDictionary newLocalUpdateMeta = BdfDictionary.of(
				new BdfEntry(MSG_KEY_UPDATE_VERSION, 2L),
				new BdfEntry(MSG_KEY_LOCAL, true));
		BdfDictionary groupMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()));

		context.checking(new Expectations() {{
			oneOf(clientHelper).toList(newRemoteUpdate);
			will(returnValue(newRemoteUpdateBody));
			// Find the latest local and remote updates
			oneOf(clientHelper).getMessageMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(messageMetadata));
			// Load the latest local update
			oneOf(clientHelper).getMessageAsList(txn, oldLocalUpdateId);
			will(returnValue(oldLocalUpdateBody));
			// Load the latest remote update
			oneOf(clientHelper).getMessageAsList(txn, oldRemoteUpdateId);
			will(returnValue(oldRemoteUpdateBody));
			// Delete the old remote update
			oneOf(db).deleteMessage(txn, oldRemoteUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldRemoteUpdateId);
			// Delete the old local update
			oneOf(db).deleteMessage(txn, oldLocalUpdateId);
			oneOf(db).deleteMessageMetadata(txn, oldLocalUpdateId);
			// Store the new local update
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(clientHelper).createMessage(contactGroup.getId(), now,
					newLocalUpdateBody);
			will(returnValue(newLocalUpdate));
			oneOf(clientHelper).addLocalMessage(txn, newLocalUpdate,
					newLocalUpdateMeta, true);
			// The client's visibility has changed
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(groupMeta));
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
			oneOf(hook).onClientVisibilityChanging(txn, contact, INVISIBLE);
		}});

		ClientVersioningManagerImpl c = createInstance();
		c.registerClient(clientId, 123, 234, hook);
		assertFalse(c.incomingMessage(txn, newRemoteUpdate, new Metadata()));
	}
}
