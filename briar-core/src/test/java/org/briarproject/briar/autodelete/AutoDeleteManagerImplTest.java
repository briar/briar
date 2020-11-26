package org.briarproject.briar.autodelete;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.client.ContactGroupFactory;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.client.ContactGroupConstants.GROUP_KEY_CONTACT_ID;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getGroup;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.MAX_AUTO_DELETE_TIMER_MS;
import static org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER;
import static org.briarproject.briar.api.autodelete.AutoDeleteManager.CLIENT_ID;
import static org.briarproject.briar.api.autodelete.AutoDeleteManager.MAJOR_VERSION;
import static org.briarproject.briar.autodelete.AutoDeleteConstants.GROUP_KEY_AUTO_DELETE_TIMER;
import static org.junit.Assert.assertEquals;

public class AutoDeleteManagerImplTest extends BrambleMockTestCase {

	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final ClientHelper clientHelper = context.mock(ClientHelper.class);
	private final GroupFactory groupFactory = context.mock(GroupFactory.class);
	private final ContactGroupFactory contactGroupFactory =
			context.mock(ContactGroupFactory.class);

	private final Group localGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Group contactGroup = getGroup(CLIENT_ID, MAJOR_VERSION);
	private final Contact contact = getContact();

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
		long timer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_AUTO_DELETE_TIMER, timer));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).mergeGroupMetadata(txn,
					contactGroup.getId(), meta);
		}});

		autoDeleteManager.setAutoDeleteTimer(txn, contact.getId(), timer);
	}

	@Test
	public void testRetrievesTimer() throws Exception {
		Transaction txn = new Transaction(null, false);
		long timer = MAX_AUTO_DELETE_TIMER_MS;
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()),
				new BdfEntry(GROUP_KEY_AUTO_DELETE_TIMER, timer));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
		}});

		assertEquals(timer,
				autoDeleteManager.getAutoDeleteTimer(txn, contact.getId()));
	}

	@Test
	public void testReturnsConstantIfNoTimerIsStored() throws Exception {
		Transaction txn = new Transaction(null, false);
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_CONTACT_ID, contact.getId().getInt()));

		expectGetContact(txn);
		expectGetContactGroup();
		context.checking(new Expectations() {{
			oneOf(clientHelper).getGroupMetadataAsDictionary(txn,
					contactGroup.getId());
			will(returnValue(meta));
		}});

		assertEquals(NO_AUTO_DELETE_TIMER,
				autoDeleteManager.getAutoDeleteTimer(txn, contact.getId()));
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
}
