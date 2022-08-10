package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.mailbox.OwnMailboxClient.CONNECTIVITY_CHECK_INTERVAL_MS;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getRandomId;

public class OwnMailboxClientTest extends BrambleMockTestCase {

	private final MailboxWorkerFactory workerFactory =
			context.mock(MailboxWorkerFactory.class);
	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final TorReachabilityMonitor reachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final TaskScheduler taskScheduler =
			context.mock(TaskScheduler.class);
	private final Executor ioExecutor = context.mock(Executor.class);
	private final MailboxWorker contactListWorker =
			context.mock(MailboxWorker.class, "contactListWorker");
	private final MailboxWorker uploadWorker1 =
			context.mock(MailboxWorker.class, "uploadWorker1");
	private final MailboxWorker uploadWorker2 =
			context.mock(MailboxWorker.class, "uploadWorker2");
	private final MailboxWorker downloadWorker =
			context.mock(MailboxWorker.class, "downloadWorker");
	private final Cancellable connectivityCheck =
			context.mock(Cancellable.class);

	private final MailboxProperties properties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final MailboxFolderId folderId = new MailboxFolderId(getRandomId());
	private final ContactId contactId1 = getContactId();
	private final ContactId contactId2 = getContactId();

	private final OwnMailboxClient client;

	public OwnMailboxClientTest() {
		expectCreateContactListWorker();
		client = new OwnMailboxClient(workerFactory, connectivityChecker,
				reachabilityMonitor, taskScheduler, ioExecutor, properties);
		context.assertIsSatisfied();
	}

	@Test
	public void testStartAndDestroyWithNoContactsAssigned() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignContactForUploadAndDestroyClient() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateUploadWorker(contactId1, uploadWorker1);
		expectStartWorker(uploadWorker1);
		client.assignContactForUpload(contactId1, properties, folderId);

		// When the client is destroyed, the worker should be destroyed
		expectDestroyWorker(uploadWorker1);
		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignContactForUpload() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateUploadWorker(contactId1, uploadWorker1);
		expectStartWorker(uploadWorker1);
		client.assignContactForUpload(contactId1, properties, folderId);

		// When the contact is deassigned, the worker should be destroyed
		expectDestroyWorker(uploadWorker1);
		client.deassignContactForUpload(contactId1);
		context.assertIsSatisfied();

		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignTwoContactsForUpload() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the first contact is assigned, the first worker should be
		// created and started
		expectCreateUploadWorker(contactId1, uploadWorker1);
		expectStartWorker(uploadWorker1);
		client.assignContactForUpload(contactId1, properties, folderId);

		// When the second contact is assigned, the second worker should be
		// created and started
		expectCreateUploadWorker(contactId2, uploadWorker2);
		expectStartWorker(uploadWorker2);
		client.assignContactForUpload(contactId2, properties, folderId);

		// When the second contact is deassigned, the second worker should be
		// destroyed
		expectDestroyWorker(uploadWorker2);
		client.deassignContactForUpload(contactId2);
		context.assertIsSatisfied();

		// When the first contact is deassigned, the first worker should be
		// destroyed
		expectDestroyWorker(uploadWorker1);
		client.deassignContactForUpload(contactId1);
		context.assertIsSatisfied();

		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignContactForDownloadAndDestroyClient() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateDownloadWorker();
		expectStartWorker(downloadWorker);
		client.assignContactForDownload(contactId1, properties, folderId);

		// When the client is destroyed, the worker should be destroyed
		expectDestroyWorker(downloadWorker);
		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignTwoContactsForDownload() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the first contact is assigned, the worker should be created and
		// started
		expectCreateDownloadWorker();
		expectStartWorker(downloadWorker);
		client.assignContactForDownload(contactId1, properties, folderId);

		// When the second contact is assigned, nothing should happen to the
		// worker
		client.assignContactForDownload(contactId2, properties, folderId);

		// When the first contact is deassigned, nothing should happen to the
		// worker
		client.deassignContactForDownload(contactId1);

		// When the second contact is deassigned, the worker should be
		// destroyed
		expectDestroyWorker(downloadWorker);
		client.deassignContactForDownload(contactId2);
		context.assertIsSatisfied();

		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testCancelsConnectivityCheckWhenClientIsDestroyed() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the first connectivity check succeeds, the worker should
		// schedule a second check
		AtomicReference<Runnable> task = new AtomicReference<>();
		expectScheduleConnectivityCheck(task);
		client.onConnectivityCheckSucceeded();

		// When the task runs, the worker should check the mailbox's
		// connectivity
		expectStartConnectivityCheck();
		task.get().run();

		// When the second connectivity check succeeds, the worker should
		// schedule a third check
		expectScheduleConnectivityCheck(task);
		client.onConnectivityCheckSucceeded();

		// When the client is destroyed, the scheduled check should be cancelled
		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		context.checking(new Expectations() {{
			oneOf(connectivityCheck).cancel();
		}});
		client.destroy();

		// If the task runs anyway (cancellation came too late), it should
		// return when it finds that the client has been destroyed
		task.get().run();
	}

	@Test
	public void testIgnoresConnectivityResultWhenClientIsDestroyed() {
		expectStartConnectivityCheck();
		expectStartWorker(contactListWorker);
		client.start();

		// When the first connectivity check succeeds, the worker should
		// schedule a second check
		AtomicReference<Runnable> task = new AtomicReference<>();
		expectScheduleConnectivityCheck(task);
		client.onConnectivityCheckSucceeded();

		// When the task runs, the worker should check the mailbox's
		// connectivity
		expectStartConnectivityCheck();
		task.get().run();

		// Before the connectivity check succeeds, the client is destroyed
		expectDestroyWorker(contactListWorker);
		expectDestroyConnectivityChecker();
		client.destroy();

		// If the connectivity check succeeds despite the connectivity checker
		// having been destroyed, the client should not schedule another check
		client.onConnectivityCheckSucceeded();
	}

	private void expectCreateContactListWorker() {
		context.checking(new Expectations() {{
			oneOf(workerFactory).createContactListWorkerForOwnMailbox(
					connectivityChecker, properties);
			will(returnValue(contactListWorker));
		}});
	}

	private void expectCreateUploadWorker(ContactId contactId,
			MailboxWorker worker) {
		context.checking(new Expectations() {{
			oneOf(workerFactory).createUploadWorker(connectivityChecker,
					properties, folderId, contactId);
			will(returnValue(worker));
		}});
	}

	private void expectCreateDownloadWorker() {
		context.checking(new Expectations() {{
			oneOf(workerFactory).createDownloadWorkerForOwnMailbox(
					connectivityChecker, reachabilityMonitor, properties);
			will(returnValue(downloadWorker));
		}});
	}

	private void expectStartWorker(MailboxWorker worker) {
		context.checking(new Expectations() {{
			oneOf(worker).start();
		}});
	}

	private void expectStartConnectivityCheck() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(properties, client);
		}});
	}

	private void expectScheduleConnectivityCheck(
			AtomicReference<Runnable> task) {
		context.checking(new Expectations() {{
			oneOf(taskScheduler).schedule(with(any(Runnable.class)),
					with(ioExecutor), with(CONNECTIVITY_CHECK_INTERVAL_MS),
					with(TimeUnit.MILLISECONDS));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, Runnable.class, 0),
					returnValue(connectivityCheck)
			));
		}});
	}

	private void expectDestroyWorker(MailboxWorker worker) {
		context.checking(new Expectations() {{
			oneOf(worker).destroy();
		}});
	}

	private void expectDestroyConnectivityChecker() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).destroy();
		}});
	}
}
