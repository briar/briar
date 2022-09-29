package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.mailbox.MailboxUpdate;
import org.briarproject.bramble.api.mailbox.MailboxUpdateManager;
import org.briarproject.bramble.api.mailbox.MailboxUpdateWithMailbox;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxContact;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.test.TestUtils.getContact;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OwnMailboxContactListWorkerTest extends BrambleMockTestCase {

	private final Executor ioExecutor = context.mock(Executor.class);
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	private final MailboxUpdateManager mailboxUpdateManager =
			context.mock(MailboxUpdateManager.class);
	private final Cancellable apiCall = context.mock(Cancellable.class);

	private final MailboxProperties mailboxProperties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final Contact contact1 = getContact(), contact2 = getContact();
	private final MailboxProperties contactProperties1 =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxProperties contactProperties2 =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxUpdateWithMailbox update1 =
			new MailboxUpdateWithMailbox(CLIENT_SUPPORTS, contactProperties1);
	private final MailboxUpdateWithMailbox update2 =
			new MailboxUpdateWithMailbox(CLIENT_SUPPORTS, contactProperties2);

	private final OwnMailboxContactListWorker worker =
			new OwnMailboxContactListWorker(ioExecutor, db, eventBus,
					connectivityChecker, mailboxApiCaller, mailboxApi,
					mailboxUpdateManager, mailboxProperties);

	@Test
	public void testChecksConnectivityWhenStartedAndRemovesObserverWhenDestroyed() {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();

		worker.start();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveConnectivityObserverAndEventListener();

		worker.destroy();
	}

	@Test
	public void testUpdatesContactListWhenConnectivityCheckSucceeds()
			throws Exception {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();

		worker.start();

		// When the connectivity check succeeds, the worker should start a
		// task to fetch the remote contact list
		AtomicReference<ApiCall> fetchList = new AtomicReference<>();
		expectStartTaskToFetchRemoteContactList(fetchList);

		worker.onConnectivityCheckSucceeded();

		// When the fetch task runs it should fetch the remote contact list,
		// load the local contact list, and find the differences. Contact 2
		// needs to be added and contact 1 needs to be removed. The worker
		// should load the mailbox update for contact 2 and start a task to
		// add contact 2 to the mailbox
		expectFetchRemoteContactList(singletonList(contact1.getId()));
		expectRunTaskOnIoExecutor();
		expectLoadLocalContactList(singletonList(contact2));
		expectRunTaskOnIoExecutor();
		expectLoadMailboxUpdate(contact2, update2);
		AtomicReference<ApiCall> addContact = new AtomicReference<>();
		expectStartTaskToAddContact(addContact);

		assertFalse(fetchList.get().callApi());

		// When the add-contact task runs it should add contact 2 to the
		// mailbox, then continue with the next update
		AtomicReference<MailboxContact> added = new AtomicReference<>();
		expectAddContactToMailbox(added);
		AtomicReference<ApiCall> removeContact = new AtomicReference<>();
		expectStartTaskToRemoveContact(removeContact);

		assertFalse(addContact.get().callApi());

		// Check that the added contact has the expected properties
		MailboxContact expected = new MailboxContact(contact2.getId(),
				contactProperties2.getAuthToken(),
				requireNonNull(contactProperties2.getInboxId()),
				requireNonNull(contactProperties2.getOutboxId()));
		assertMailboxContactEquals(expected, added.get());

		// When the remove-contact task runs it should remove contact 1 from
		// the mailbox
		expectRemoveContactFromMailbox(contact1);

		assertFalse(removeContact.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveConnectivityObserverAndEventListener();

		worker.destroy();
	}

	@Test
	public void testHandlesEventsAfterMakingInitialUpdates() throws Exception {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();

		worker.start();

		// When the connectivity check succeeds, the worker should start a
		// task to fetch the remote contact list
		AtomicReference<ApiCall> fetchList = new AtomicReference<>();
		expectStartTaskToFetchRemoteContactList(fetchList);

		worker.onConnectivityCheckSucceeded();

		// When the fetch task runs it should fetch the remote contact list,
		// load the local contact list, and find the differences. The lists
		// are the same, so the worker should wait for changes
		expectFetchRemoteContactList(emptyList());
		expectRunTaskOnIoExecutor();
		expectLoadLocalContactList(emptyList());

		assertFalse(fetchList.get().callApi());

		// When a contact is added, the worker should load the contact's
		// mailbox update and start a task to add the contact to the mailbox
		expectRunTaskOnIoExecutor();
		expectLoadMailboxUpdate(contact1, update1);
		AtomicReference<ApiCall> addContact = new AtomicReference<>();
		expectStartTaskToAddContact(addContact);

		worker.eventOccurred(new ContactAddedEvent(contact1.getId(), true));

		// When the add-contact task runs it should add contact 1 to the
		// mailbox
		AtomicReference<MailboxContact> added = new AtomicReference<>();
		expectAddContactToMailbox(added);

		assertFalse(addContact.get().callApi());

		// Check that the added contact has the expected properties
		MailboxContact expected = new MailboxContact(contact1.getId(),
				contactProperties1.getAuthToken(),
				requireNonNull(contactProperties1.getInboxId()),
				requireNonNull(contactProperties1.getOutboxId()));
		assertMailboxContactEquals(expected, added.get());

		// When the contact is removed again, the worker should start a task
		// to remove the contact from the mailbox
		expectRunTaskOnIoExecutor();
		AtomicReference<ApiCall> removeContact = new AtomicReference<>();
		expectStartTaskToRemoveContact(removeContact);

		worker.eventOccurred(new ContactRemovedEvent(contact1.getId()));

		// When the remove-contact task runs it should remove the contact from
		// the mailbox
		expectRemoveContactFromMailbox(contact1);

		assertFalse(removeContact.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveConnectivityObserverAndEventListener();

		worker.destroy();
	}


	@Test
	public void testHandlesNoSuchContactException() throws Exception {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();

		worker.start();

		// When the connectivity check succeeds, the worker should start a
		// task to fetch the remote contact list
		AtomicReference<ApiCall> fetchList = new AtomicReference<>();
		expectStartTaskToFetchRemoteContactList(fetchList);

		worker.onConnectivityCheckSucceeded();

		// When the fetch task runs it should fetch the remote contact list,
		// load the local contact list, and find the differences. Contact 1
		// needs to be added, so the worker should submit a task to the
		// IO executor to load the contact's mailbox update
		expectFetchRemoteContactList(emptyList());
		expectRunTaskOnIoExecutor();
		expectLoadLocalContactList(singletonList(contact1));
		AtomicReference<Runnable> loadUpdate = new AtomicReference<>();
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new CaptureArgumentAction<>(loadUpdate, Runnable.class, 0));
		}});

		assertFalse(fetchList.get().callApi());

		// Before the contact's mailbox update can be loaded, the contact
		// is removed
		worker.eventOccurred(new ContactRemovedEvent(contact1.getId()));

		// When the load-update task runs, a NoSuchContactException is thrown.
		// The worker should abandon adding the contact and move on to the
		// next update, which is the removal of the same contact. The worker
		// should start a task to remove the contact from the mailbox
		Transaction txn = new Transaction(null, false);
		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true), withDbCallable(txn));
			oneOf(mailboxUpdateManager).getLocalUpdate(txn, contact1.getId());
			will(throwException(new NoSuchContactException()));
		}});
		AtomicReference<ApiCall> removeContact = new AtomicReference<>();
		expectStartTaskToRemoveContact(removeContact);

		loadUpdate.get().run();

		// When the remove-contact task runs it should remove the contact from
		// the mailbox. The contact was never added, so the call throws a
		// TolerableFailureException
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteContact(mailboxProperties,
					contact1.getId());
			will(throwException(new TolerableFailureException()));
		}});

		assertFalse(removeContact.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveConnectivityObserverAndEventListener();

		worker.destroy();
	}

	@Test
	public void testCancelsApiCallWhenDestroyed() {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();

		worker.start();

		// When the connectivity check succeeds, the worker should start a
		// task to fetch the remote contact list
		AtomicReference<ApiCall> fetchList = new AtomicReference<>();
		expectStartTaskToFetchRemoteContactList(fetchList);

		worker.onConnectivityCheckSucceeded();

		// The worker is destroyed before the task runs. The worker should
		// cancel the task remove the connectivity observer and event listener
		context.checking(new Expectations() {{
			oneOf(apiCall).cancel();
		}});
		expectRemoveConnectivityObserverAndEventListener();

		worker.destroy();
	}

	private void expectStartConnectivityCheck() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(
					with(mailboxProperties), with(worker));
		}});
	}

	private void expectRemoveConnectivityObserverAndEventListener() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(eventBus).removeListener(worker);
		}});
	}

	private void expectStartTaskToFetchRemoteContactList(
			AtomicReference<ApiCall> task) {
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});
	}

	private void expectFetchRemoteContactList(List<ContactId> remote)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getContacts(mailboxProperties);
			will(returnValue(remote));
		}});
	}

	private void expectLoadLocalContactList(List<Contact> local)
			throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).getContacts(txn);
			will(returnValue(local));
		}});
	}

	private void expectLoadMailboxUpdate(Contact c, MailboxUpdate update)
			throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transactionWithResult(with(true),
					withDbCallable(txn));
			oneOf(mailboxUpdateManager).getLocalUpdate(txn, c.getId());
			will(returnValue(update));
		}});
	}

	private void expectStartTaskToAddContact(AtomicReference<ApiCall> task) {
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});
	}

	private void expectAddContactToMailbox(
			AtomicReference<MailboxContact> added) throws Exception {
		context.checking(new DbExpectations() {{
			oneOf(mailboxApi).addContact(with(mailboxProperties),
					with(any(MailboxContact.class)));
			will(new CaptureArgumentAction<>(added, MailboxContact.class, 1));
		}});
	}

	private void expectStartTaskToRemoveContact(AtomicReference<ApiCall> task) {
		context.checking(new DbExpectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});
	}

	private void expectRemoveContactFromMailbox(Contact c) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteContact(mailboxProperties, c.getId());
		}});
	}

	private void expectRunTaskOnIoExecutor() {
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
		}});
	}

	private void assertMailboxContactEquals(MailboxContact expected,
			MailboxContact actual) {
		assertEquals(expected.contactId, actual.contactId);
		assertEquals(expected.token, actual.token);
		assertEquals(expected.inboxId, actual.inboxId);
		assertEquals(expected.outboxId, actual.outboxId);
	}
}
