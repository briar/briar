package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.sync.OutgoingSessionRecord;
import org.briarproject.bramble.api.sync.event.MessageSharedEvent;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.briarproject.bramble.test.ConsumeArgumentAction;
import org.briarproject.bramble.test.DbExpectations;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.MAX_LATENCY;
import static org.briarproject.bramble.mailbox.MailboxUploadWorker.CHECK_DELAY_MS;
import static org.briarproject.bramble.mailbox.MailboxUploadWorker.RETRY_DELAY_MS;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MailboxUploadWorkerTest extends BrambleMockTestCase {

	private final Executor ioExecutor = context.mock(Executor.class);
	private final DatabaseComponent db = context.mock(DatabaseComponent.class);
	private final Clock clock = context.mock(Clock.class);
	private final TaskScheduler taskScheduler =
			context.mock(TaskScheduler.class);
	private final EventBus eventBus = context.mock(EventBus.class);
	private final ConnectionRegistry connectionRegistry =
			context.mock(ConnectionRegistry.class);
	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	private final MailboxFileManager mailboxFileManager =
			context.mock(MailboxFileManager.class);
	private final Cancellable apiCall =
			context.mock(Cancellable.class, "apiCall");
	private final Cancellable wakeupTask =
			context.mock(Cancellable.class, "wakeupTask");
	private final Cancellable checkTask =
			context.mock(Cancellable.class, "checkTask");

	private final MailboxProperties mailboxProperties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final long now = System.currentTimeMillis();
	private final MailboxFolderId folderId = new MailboxFolderId(getRandomId());
	private final ContactId contactId = getContactId();
	private final MessageId ackedId = new MessageId(getRandomId());
	private final MessageId sentId = new MessageId(getRandomId());
	private final MessageId newMessageId = new MessageId(getRandomId());
	private final GroupId groupId = new GroupId(getRandomId());
	private final Map<ContactId, Boolean> groupVisibility =
			singletonMap(contactId, true);

	private File testDir, tempFile;
	private MailboxUploadWorker worker;

	@Before
	public void setUp() {
		testDir = getTestDirectory();
		tempFile = new File(testDir, "temp");
		worker = new MailboxUploadWorker(ioExecutor, db, clock, taskScheduler,
				eventBus, connectionRegistry, connectivityChecker,
				mailboxApiCaller, mailboxApi, mailboxFileManager,
				mailboxProperties, folderId, contactId);
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}

	@Test
	public void testChecksForDataWhenStartedAndRemovesObserverWhenDestroyed()
			throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.start();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testDoesNotCheckForDataWhenStartedIfConnectedToContact() {
		// When the worker is started it should check the connection registry.
		// We're connected to the contact, so the worker should not check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(true);

		worker.start();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testChecksForDataWhenContactDisconnects() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're connected to the contact, so the worker should not check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(true);

		worker.start();

		// When the contact disconnects, the worker should start a task to
		// check for data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.eventOccurred(new ContactDisconnectedEvent(contactId));

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testChecksConnectivityWhenStartedIfDataIsReady()
			throws Exception {
		Transaction recordTxn = new Transaction(null, false);

		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As there's data ready to send immediately, the worker
		// should start a connectivity check
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndStartConnectivityCheck();

		worker.start();

		// Create the temporary file so we can test that it gets deleted
		assertTrue(testDir.mkdirs());
		assertTrue(tempFile.createNewFile());

		// When the connectivity check succeeds, the worker should write a file
		// and start an upload task
		expectRunTaskOnIoExecutor();
		AtomicReference<ApiCall> upload = new AtomicReference<>();
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createAndWriteTempFileForUpload(
					with(contactId), with(any(OutgoingSessionRecord.class)));
			will(new DoAllAction(
					// Record some IDs as acked and sent
					new ConsumeArgumentAction<>(OutgoingSessionRecord.class, 1,
							record -> {
								record.onAckSent(singletonList(ackedId));
								record.onMessageSent(sentId);
							}),
					returnValue(tempFile)
			));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(upload, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});

		worker.onConnectivityCheckSucceeded();

		// When the upload task runs, it should upload the file, record
		// the acked/sent messages in the DB, and check the connection
		// registry. We're not connected to the contact, so the worker should
		// check for more data to send
		context.checking(new DbExpectations() {{
			oneOf(mailboxApi).addFile(mailboxProperties, folderId, tempFile);
			oneOf(db).transaction(with(false), withDbRunnable(recordTxn));
			oneOf(db).setAckSent(recordTxn, contactId, singletonList(ackedId));
			oneOf(db).setMessagesSent(recordTxn, contactId,
					singletonList(sentId), MAX_LATENCY);
		}});
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		assertFalse(upload.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();

		// The file should have been deleted
		assertFalse(tempFile.exists());
	}

	@Test
	public void testDoesNotWriteFileIfContactConnectsDuringConnectivityCheck()
			throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As there's data ready to send immediately, the worker
		// should start a connectivity check
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndStartConnectivityCheck();

		worker.start();

		// Before the connectivity check succeeds, we make a direct connection
		// to the contact
		worker.eventOccurred(new ContactConnectedEvent(contactId));

		// When the connectivity check succeeds, the worker should not start
		// writing and uploading a file
		worker.onConnectivityCheckSucceeded();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testCancelsApiCallWhenDestroyed() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As there's data ready to send immediately, the worker
		// should start a connectivity check
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndStartConnectivityCheck();

		worker.start();

		// Create the temporary file so we can test that it gets deleted
		assertTrue(testDir.mkdirs());
		assertTrue(tempFile.createNewFile());

		// When the connectivity check succeeds, the worker should write a file
		// and start an upload task
		expectRunTaskOnIoExecutor();
		AtomicReference<ApiCall> upload = new AtomicReference<>();
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createAndWriteTempFileForUpload(
					with(contactId), with(any(OutgoingSessionRecord.class)));
			will(new DoAllAction(
					// Record some IDs as acked and sent
					new ConsumeArgumentAction<>(OutgoingSessionRecord.class, 1,
							record -> {
								record.onAckSent(singletonList(ackedId));
								record.onMessageSent(sentId);
							}),
					returnValue(tempFile)
			));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(upload, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});

		worker.onConnectivityCheckSucceeded();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener and cancel the upload task
		expectCancelTask(apiCall);
		expectRemoveObserverAndListener();

		worker.destroy();

		// The file should have been deleted
		assertFalse(tempFile.exists());

		// If the upload task runs anyway (cancellation came too late), it
		// should return early when it finds the state has changed
		assertFalse(upload.get().callApi());
	}

	@Test
	public void testSchedulesWakeupWhenStartedIfDataIsNotReady()
			throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As the data isn't ready to send immediately, the
		// worker should schedule a wakeup
		expectRunTaskOnIoExecutor();
		AtomicReference<Runnable> wakeup = new AtomicReference<>();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndScheduleWakeup(wakeup);

		worker.start();

		// When the wakeup task runs it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		wakeup.get().run();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testCancelsWakeupIfContactConnectsBeforeWakingUp()
			throws Exception {
		// When the worker is started it should check for data to send. As
		// the data isn't ready to send immediately, the worker should
		// schedule a wakeup
		expectRunTaskOnIoExecutor();
		AtomicReference<Runnable> wakeup = new AtomicReference<>();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndScheduleWakeup(wakeup);

		worker.start();

		// Before the wakeup task runs, we make a direct connection to the
		// contact. The worker should cancel the wakeup task
		expectCancelTask(wakeupTask);

		worker.eventOccurred(new ContactConnectedEvent(contactId));

		// If the wakeup task runs anyway (cancellation came too late), it
		// should return without doing anything
		wakeup.get().run();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testCancelsWakeupIfDestroyedBeforeWakingUp() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As the data isn't ready to send immediately, the
		// worker should schedule a wakeup
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		AtomicReference<Runnable> wakeup = new AtomicReference<>();
		expectCheckForDataToSendAndScheduleWakeup(wakeup);

		worker.start();

		// When the worker is destroyed it should cancel the wakeup and
		// remove the connectivity observer and event listener
		expectCancelTask(wakeupTask);
		expectRemoveObserverAndListener();

		worker.destroy();

		// If the wakeup task runs anyway (cancellation came too late), it
		// should return early without doing anything
		wakeup.get().run();
	}

	@Test
	public void testCancelsWakeupIfEventIsReceivedBeforeWakingUp()
			throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As the data isn't ready to send immediately, the
		// worker should schedule a wakeup
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		AtomicReference<Runnable> wakeup = new AtomicReference<>();
		expectCheckForDataToSendAndScheduleWakeup(wakeup);

		worker.start();

		// Before the wakeup task runs, the worker receives an event that
		// indicates new data may be available. The worker should cancel the
		// wakeup task and schedule a check for new data after a short delay
		AtomicReference<Runnable> check = new AtomicReference<>();
		expectScheduleCheck(check, CHECK_DELAY_MS);
		expectCancelTask(wakeupTask);

		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				groupVisibility));

		// If the wakeup task runs anyway (cancellation came too late), it
		// should return early when it finds the state has changed
		wakeup.get().run();

		// Before the check task runs, the worker receives another event that
		// indicates new data may be available. The event should be ignored,
		// as a check for new data has already been scheduled
		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				groupVisibility));

		// When the check task runs, it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// new data
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		check.get().run();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testCancelsCheckWhenDestroyed() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.start();

		// The worker receives an event that indicates new data may be
		// available. The worker should schedule a check for new data after
		// a short delay
		AtomicReference<Runnable> check = new AtomicReference<>();
		expectScheduleCheck(check, CHECK_DELAY_MS);

		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				groupVisibility));

		// When the worker is destroyed it should cancel the check and
		// remove the connectivity observer and event listener
		expectCancelTask(checkTask);
		expectRemoveObserverAndListener();

		worker.destroy();

		// If the check runs anyway (cancellation came too late), it should
		// return early when it finds the state has changed
		check.get().run();
	}

	@Test
	public void testCancelsCheckIfContactConnects() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.start();

		// The worker receives an event that indicates new data may be
		// available. The worker should schedule a check for new data after
		// a short delay
		AtomicReference<Runnable> check = new AtomicReference<>();
		expectScheduleCheck(check, CHECK_DELAY_MS);

		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				groupVisibility));

		// Before the check task runs, we make a direct connection to the
		// contact. The worker should cancel the check
		expectCancelTask(checkTask);

		worker.eventOccurred(new ContactConnectedEvent(contactId));

		// If the check runs anyway (cancellation came too late), it should
		// return early when it finds the state has changed
		check.get().run();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testDoesNotScheduleCheckIfGroupIsVisible() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.start();

		// The worker receives an event that indicates new data may be
		// available. The group is visible to the contact but not shared, so
		// the worker should not schedule a check for new data
		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				singletonMap(contactId, false)));

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testDoesNotScheduleCheckIfGroupIsInvisible() throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		worker.start();

		// The worker receives an event that indicates new data may be
		// available. The group is not visible to the contact, so the worker
		// should not schedule a check for new data
		worker.eventOccurred(new MessageSharedEvent(newMessageId, groupId,
				emptyMap()));

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	@Test
	public void testRetriesAfterDelayIfExceptionOccursWhileWritingFile()
			throws Exception {
		// When the worker is started it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// data to send. As there's data ready to send immediately, the worker
		// should start a connectivity check
		expectRunTaskOnIoExecutor();
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendAndStartConnectivityCheck();

		worker.start();

		// When the connectivity check succeeds, the worker should try to
		// write a file. This fails with an exception, so the worker should
		// retry by scheduling a check for new data after a short delay
		expectRunTaskOnIoExecutor();
		AtomicReference<Runnable> check = new AtomicReference<>();
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createAndWriteTempFileForUpload(
					with(contactId), with(any(OutgoingSessionRecord.class)));
			will(throwException(new IOException())); // Oh noes!
		}});
		expectScheduleCheck(check, RETRY_DELAY_MS);

		worker.onConnectivityCheckSucceeded();

		// When the check task runs it should check the connection registry.
		// We're not connected to the contact, so the worker should check for
		// new data
		expectCheckConnectionRegistry(false);
		expectCheckForDataToSendNoDataWaiting();

		check.get().run();

		// When the worker is destroyed it should remove the connectivity
		// observer and event listener
		expectRemoveObserverAndListener();

		worker.destroy();
	}

	private void expectCheckConnectionRegistry(boolean connected) {
		context.checking(new Expectations() {{
			oneOf(connectionRegistry).isConnected(contactId);
			will(returnValue(connected));
		}});
	}

	private void expectRunTaskOnIoExecutor() {
		context.checking(new Expectations() {{
			oneOf(ioExecutor).execute(with(any(Runnable.class)));
			will(new RunAction());
		}});
	}

	private void expectCheckForDataToSendNoDataWaiting() throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).containsAcksToSend(txn, contactId);
			will(returnValue(false));
			oneOf(db).getNextSendTime(txn, contactId, MAX_LATENCY);
			will(returnValue(Long.MAX_VALUE)); // No data waiting
		}});
	}

	private void expectCheckForDataToSendAndScheduleWakeup(
			AtomicReference<Runnable> wakeup) throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).containsAcksToSend(txn, contactId);
			will(returnValue(false));
			oneOf(db).getNextSendTime(txn, contactId, MAX_LATENCY);
			will(returnValue(now + 1234L)); // Data waiting but not ready
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(taskScheduler).schedule(with(any(Runnable.class)),
					with(ioExecutor), with(1234L), with(MILLISECONDS));
			will(new DoAllAction(
					new CaptureArgumentAction<>(wakeup, Runnable.class, 0),
					returnValue(wakeupTask)
			));
		}});
	}

	private void expectCheckForDataToSendAndStartConnectivityCheck()
			throws Exception {
		Transaction txn = new Transaction(null, true);

		context.checking(new DbExpectations() {{
			oneOf(db).transaction(with(true), withDbRunnable(txn));
			oneOf(db).containsAcksToSend(txn, contactId);
			will(returnValue(false));
			oneOf(db).getNextSendTime(txn, contactId, MAX_LATENCY);
			will(returnValue(0L)); // Data ready to send
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(connectivityChecker).checkConnectivity(mailboxProperties,
					worker);
		}});
	}

	private void expectScheduleCheck(AtomicReference<Runnable> check,
			long delay) {
		context.checking(new Expectations() {{
			oneOf(taskScheduler).schedule(with(any(Runnable.class)),
					with(ioExecutor), with(delay), with(MILLISECONDS));
			will(new DoAllAction(
					new CaptureArgumentAction<>(check, Runnable.class, 0),
					returnValue(checkTask)
			));
		}});
	}

	private void expectCancelTask(Cancellable task) {
		context.checking(new Expectations() {{
			oneOf(task).cancel();
		}});
	}

	private void expectRemoveObserverAndListener() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(eventBus).removeListener(worker);
		}});
	}
}
