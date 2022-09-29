package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

public class ContactMailboxClientTest extends BrambleMockTestCase {

	private final MailboxWorkerFactory workerFactory =
			context.mock(MailboxWorkerFactory.class);
	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final TorReachabilityMonitor reachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final MailboxWorker uploadWorker =
			context.mock(MailboxWorker.class, "uploadWorker");
	private final MailboxWorker downloadWorker =
			context.mock(MailboxWorker.class, "downloadWorker");

	private final MailboxProperties properties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final MailboxFolderId inboxId =
			requireNonNull(properties.getInboxId());
	private final MailboxFolderId outboxId =
			requireNonNull(properties.getOutboxId());
	private final ContactId contactId = getContactId();

	private final ContactMailboxClient client =
			new ContactMailboxClient(workerFactory, connectivityChecker,
					reachabilityMonitor);

	@Test
	public void testStartAndDestroyWithNoContactsAssigned() {
		client.start();

		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignContactForUploadAndDestroyClient() {
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateAndStartUploadWorker();
		client.assignContactForUpload(contactId, properties, outboxId);

		// When the client is destroyed, the worker should be destroyed
		expectDestroyWorker(uploadWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignContactForUpload() {
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateAndStartUploadWorker();
		client.assignContactForUpload(contactId, properties, outboxId);

		// When the contact is deassigned, the worker should be destroyed
		expectDestroyWorker(uploadWorker);
		client.deassignContactForUpload(contactId);
		context.assertIsSatisfied();

		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignContactForDownloadAndDestroyClient() {
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateAndStartDownloadWorker();
		client.assignContactForDownload(contactId, properties, inboxId);

		// When the client is destroyed, the worker should be destroyed
		expectDestroyWorker(downloadWorker);
		expectDestroyConnectivityChecker();
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignContactForDownload() {
		client.start();

		// When the contact is assigned, the worker should be created and
		// started
		expectCreateAndStartDownloadWorker();
		client.assignContactForDownload(contactId, properties, inboxId);

		// When the contact is deassigned, the worker should be destroyed
		expectDestroyWorker(downloadWorker);
		client.deassignContactForDownload(contactId);
		context.assertIsSatisfied();

		expectDestroyConnectivityChecker();
		client.destroy();
	}

	private void expectCreateAndStartUploadWorker() {
		context.checking(new Expectations() {{
			oneOf(workerFactory).createUploadWorker(connectivityChecker,
					properties, outboxId, contactId);
			will(returnValue(uploadWorker));
			oneOf(uploadWorker).start();
		}});
	}

	private void expectCreateAndStartDownloadWorker() {
		context.checking(new Expectations() {{
			oneOf(workerFactory).createDownloadWorkerForContactMailbox(
					connectivityChecker, reachabilityMonitor, properties);
			will(returnValue(downloadWorker));
			oneOf(downloadWorker).start();
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
