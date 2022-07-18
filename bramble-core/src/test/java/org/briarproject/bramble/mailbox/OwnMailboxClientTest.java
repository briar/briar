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
import static org.briarproject.bramble.test.TestUtils.getRandomId;

public class OwnMailboxClientTest extends BrambleMockTestCase {

	private final MailboxWorkerFactory workerFactory =
			context.mock(MailboxWorkerFactory.class);
	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final TorReachabilityMonitor reachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final MailboxWorker contactListWorker =
			context.mock(MailboxWorker.class, "contactListWorker");
	private final MailboxWorker uploadWorker1 =
			context.mock(MailboxWorker.class, "uploadWorker1");
	private final MailboxWorker uploadWorker2 =
			context.mock(MailboxWorker.class, "uploadWorker2");
	private final MailboxWorker downloadWorker =
			context.mock(MailboxWorker.class, "downloadWorker");

	private final MailboxProperties properties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final MailboxFolderId folderId = new MailboxFolderId(getRandomId());
	private final ContactId contactId1 = getContactId();
	private final ContactId contactId2 = getContactId();

	private final OwnMailboxClient client;

	public OwnMailboxClientTest() {
		expectCreateContactListWorker();
		client = new OwnMailboxClient(workerFactory, connectivityChecker,
				reachabilityMonitor, properties);
		context.assertIsSatisfied();
	}

	@Test
	public void testStartAndDestroyWithNoContactsAssigned() {
		expectStartWorker(contactListWorker);
		client.start();

		expectDestroyWorker(contactListWorker);
		client.destroy();
	}

	@Test
	public void testAssignContactForUploadAndDestroyClient() {
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
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignContactForUpload() {
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
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignTwoContactsForUpload() {
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
		client.destroy();
	}

	@Test
	public void testAssignContactForDownloadAndDestroyClient() {
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
		client.destroy();
	}

	@Test
	public void testAssignAndDeassignTwoContactsForDownload() {
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
		client.destroy();
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

	private void expectDestroyWorker(MailboxWorker worker) {
		context.checking(new Expectations() {{
			oneOf(worker).destroy();
		}});
	}
}
