package org.briarproject.briar.autodelete;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.db.CommitAction;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.EventAction;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.briar.api.autodelete.event.AutoDeleteTimerMirroredEvent;
import org.jmock.Expectations;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.client.ContactGroupConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MAX_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MIN_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.autodelete.AutoDeleteManager.CLIENT_ID;
import static org.briarproject.briar.api.autodelete.AutoDeleteManager.MAJOR_VERSION;
import static org.briarproject.briar.autodelete.AutoDeleteConstants.GROUP_KEY_PREVIOUS_TIMER;
import static org.briarproject.briar.autodelete.AutoDeleteConstants.GROUP_KEY_TIMER;
import static org.briarproject.briar.autodelete.AutoDeleteConstants.GROUP_KEY_TIMESTAMP;
import static org.briarproject.briar.autodelete.AutoDeleteConstants.NO_PREVIOUS_TIMER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("UnnecessaryLocalVariable") // Using them for readability
public class AutoDeleteManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final GroupFactory groupFactory = context.mock(GroupFactory.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);

	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Contact contact = getContact();
	private final long now = System.currentTimeMillis();

	private final AutoDeleteManagerImpl autoDeleteManager;

	public AutoDeleteManagerImplTest() {
		context.checking(new Expectations() {{
			oneOf(contactGroupFactory)
					.createLocalGroup(CLIENT_ID, MAJOR_VERSION);
			will(returnValue(localGroup));
		}});
		autoDeleteManager = new AutoDeleteManagerImpl(db, clientHelper,
				groupFactory, contactGroupFactory);
		context.assertIsSatisfied();
	}

	@Test
	public void testDoesNotAddContactGroupsAtStartupIfLocalGroupExists()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(true));
		}});

		autoDeleteManager.onDatabaseOpened(txn);
	}

	@Test
	public void testAddsContactGroupsAtStartupIfLocalGroupDoesNotExist()
			throws Exception {
		Transaction txn = new Transaction(null, false);

		context.checking(new Expectations() {{
			oneOf(db).containsGroup(txn, localGroup.getId());
			will(returnValue(false));
			oneOf(db).addGroup(txn, localGroup);
			oneOf(db).getContacts(txn);
			will(returnValue(singletonList(contact)));
		}});
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
		}});

		autoDeleteManager.onDatabaseOpened(txn);
	}

	@Test
	public void testAddsContactGroupWhenContactIsAdded() throws Exception {
		Transaction txn = new Transaction(null, false);

		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(db).addGroup(txn, contactGroup);
			oneOf(clientHelper).setContactId(txn, contactGroup.getId(),
					contact.getId());
		}});

		autoDeleteManager.addingContact(txn, contact);
	}

	@Test
	public void testRemovesContactGroupWhenContactIsRemoved() throws Exception {
		Transaction txn = new Transaction(null, false);

		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(db).removeGroup(txn, contactGroup);
		}});

		autoDeleteManager.removingContact(txn, contact);
	}

	@Test
	public void testStoresTimer() throws Exception {
		Transaction txn = new Transaction(null, false);
		long oldTimer = MIN_AUTO_DELETE_TIMER_MS;
		long newTimer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary oldMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, oldTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, newTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, oldTimer));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(oldMeta));
			oneOf(clientHelper).mergeGroupMetadata(txn,
					contactGroup.getId(), newMeta);
		}});

		autoDeleteManager.setAutoDeleteTimer(txn, contact.getId(), newTimer);
	}

	@Test
	public void testDoesNotStoreTimerIfUnchanged() throws Exception {
		Transaction txn = new Transaction(null, false);
		long timer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, timer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
		}});

		autoDeleteManager.setAutoDeleteTimer(txn, contact.getId(), timer);
	}

	@Test
	public void testRetrievesTimer() throws Exception {
		Transaction txn = new Transaction(null, false);
		long timer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()),
				new BdfEntry(GROUP_KEY_TIMER, timer));
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMESTAMP, now),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
			oneOf(clientHelper).mergeGroupMetadata(txn, contactGroup.getId(),
					newMeta);
		}});

		assertEquals(timer, autoDeleteManager
				.getAutoDeleteTimer(txn, contact.getId(), now));
	}

	@Test
	public void testReturnsConstantIfNoTimerIsStored() throws Exception {
		Transaction txn = new Transaction(null, false);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()));
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMESTAMP, now),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
			oneOf(clientHelper).mergeGroupMetadata(txn, contactGroup.getId(),
					newMeta);
		}});

		assertEquals(NO_AUTO_DELETE_TIMER, autoDeleteManager
				.getAutoDeleteTimer(txn, contact.getId(), now));
	}

	@Test
	public void testIgnoresReceivedTimerWithEarlierTimestamp()
			throws Exception {
		testIgnoresReceivedTimerWithTimestamp(now - 1);
	}

	@Test
	public void testIgnoresReceivedTimerWithEqualTimestamp() throws Exception {
		testIgnoresReceivedTimerWithTimestamp(now);
	}

	private void testIgnoresReceivedTimerWithTimestamp(long remoteTimestamp)
			throws Exception {
		Transaction txn = new Transaction(null, false);
		long localTimer = MIN_AUTO_DELETE_TIMER_MS;
		long remoteTimer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, localTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
		}});

		autoDeleteManager.receiveAutoDeleteTimer(txn, contact.getId(),
				remoteTimer, remoteTimestamp);

		// no events broadcast
		assertTrue(txn.getActions().isEmpty());
	}

	@Test
	public void testMirrorsRemoteTimestampIfNoUnsentChange() throws Exception {
		Transaction txn = new Transaction(null, false);
		long localTimer = MIN_AUTO_DELETE_TIMER_MS;
		long remoteTimer = MAX_AUTO_DELETE_TIMER_MS;
		long remoteTimestamp = now + 1;
		BdfDictionary oldMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, localTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));
		// The timestamp should be updated and the timer should be mirrored
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMESTAMP, remoteTimestamp),
				new BdfEntry(GROUP_KEY_TIMER, remoteTimer));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(oldMeta));
			oneOf(clientHelper).mergeGroupMetadata(txn,
					contactGroup.getId(), newMeta);
		}});

		autoDeleteManager.receiveAutoDeleteTimer(txn, contact.getId(),
				remoteTimer, remoteTimestamp);

		// assert that event is broadcast with new timer
		assertEvent(txn, remoteTimer);
	}

	@Test
	public void testDoesNotMirrorUnchangedRemoteTimestampIfUnsentChange()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		long localTimer = MIN_AUTO_DELETE_TIMER_MS;
		long remoteTimer = MAX_AUTO_DELETE_TIMER_MS;
		long remoteTimestamp = now + 1;
		BdfDictionary oldMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, localTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, remoteTimer),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));
		// The timestamp should be updated but the timer should not revert
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMESTAMP, remoteTimestamp));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(oldMeta));
			oneOf(clientHelper).mergeGroupMetadata(txn,
					contactGroup.getId(), newMeta);
		}});

		autoDeleteManager.receiveAutoDeleteTimer(txn, contact.getId(),
				remoteTimer, remoteTimestamp);

		// no events broadcast
		assertTrue(txn.getActions().isEmpty());
	}

	@Test
	public void testMirrorsChangedRemoteTimestampIfUnsentChange()
			throws Exception {
		Transaction txn = new Transaction(null, false);
		long localTimer = MIN_AUTO_DELETE_TIMER_MS;
		long oldRemoteTimer = MAX_AUTO_DELETE_TIMER_MS;
		long newRemoteTimer = MAX_AUTO_DELETE_TIMER_MS - 1;
		long remoteTimestamp = now + 1;
		BdfDictionary oldMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMER, localTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, oldRemoteTimer),
				new BdfEntry(GROUP_KEY_TIMESTAMP, now));
		// The timestamp should be updated , the timer should be mirrored and
		// the previous timer should be cleared
		BdfDictionary newMeta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_TIMESTAMP, remoteTimestamp),
				new BdfEntry(GROUP_KEY_TIMER, newRemoteTimer),
				new BdfEntry(GROUP_KEY_PREVIOUS_TIMER, NO_PREVIOUS_TIMER));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(oldMeta));
			oneOf(clientHelper).mergeGroupMetadata(txn,
					contactGroup.getId(), newMeta);
		}});

		autoDeleteManager.receiveAutoDeleteTimer(txn, contact.getId(),
				newRemoteTimer, remoteTimestamp);

		// assert that event is broadcast with new timer
		assertEvent(txn, newRemoteTimer);
	}

	private void expectGetContact(Transaction txn) throws Exception {
		context.checking(new Expectations() {{
			oneOf(db).getContact(txn, contact.getId());
			will(returnValue(contact));
		}});
	}

	private void expectGetContactGroup() {
		context.checking(new Expectations() {{
			oneOf(groupFactory).createGroup(CLIENT_ID, MAJOR_VERSION,
					contact.getAuthor().getId().getBytes());
			will(returnValue(contactGroup));
		}});
	}

	private void assertEvent(Transaction txn, long timer) {
		assertEquals(1, txn.getActions().size());
		CommitAction action = txn.getActions().get(0);
		assertTrue(action instanceof EventAction);
		Event event = ((EventAction) action).getEvent();
		assertTrue(event instanceof AutoDeleteTimerMirroredEvent);
		AutoDeleteTimerMirroredEvent e = (AutoDeleteTimerMirroredEvent) event;
		assertEquals(contact.getId(), e.getContactId());
		assertEquals(timer, e.getNewTimer());
	}
}
