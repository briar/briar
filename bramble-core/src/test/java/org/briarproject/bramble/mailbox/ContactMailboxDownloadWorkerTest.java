package org.briarproject.bramble.mailbox;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.junit.Assert.assertFalse;

public class ContactMailboxDownloadWorkerTest
		extends MailboxDownloadWorkerTest<ContactMailboxDownloadWorker> {

	public ContactMailboxDownloadWorkerTest() {
		mailboxProperties = getMailboxProperties(false, CLIENT_SUPPORTS);
		worker = new ContactMailboxDownloadWorker(connectivityChecker,
				torReachabilityMonitor, mailboxApiCaller, mailboxApi,
				mailboxFileManager, mailboxProperties);
	}

	@Test
	public void testChecksConnectivityWhenStartedAndRemovesObserverWhenDestroyed() {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();
		worker.start();

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		expectRemoveObservers();
		worker.destroy();
	}

	@Test
	public void testChecksForFilesWhenConnectivityCheckSucceeds()
			throws Exception {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();
		worker.start();

		// When the connectivity check succeeds, a list-inbox task should be
		// started for the first download cycle
		AtomicReference<ApiCall> listTask = new AtomicReference<>();
		expectStartTask(listTask);
		worker.onConnectivityCheckSucceeded();

		// When the list-inbox tasks runs and finds no files to download,
		// it should add a Tor reachability observer
		expectCheckForFiles(mailboxProperties.getInboxId(), emptyList());
		expectAddReachabilityObserver();
		assertFalse(listTask.get().callApi());

		// When the reachability observer is called, a list-inbox task should
		// be started for the second download cycle
		expectStartTask(listTask);
		worker.onTorReachable();

		// When the list-inbox tasks runs and finds no files to download,
		// it should finish the second download cycle
		expectCheckForFiles(mailboxProperties.getInboxId(), emptyList());
		assertFalse(listTask.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		expectRemoveObservers();
		worker.destroy();
	}

	@Test
	public void testDownloadsFilesWhenConnectivityCheckSucceeds()
			throws Exception {
		// When the worker is started it should start a connectivity check
		expectStartConnectivityCheck();
		worker.start();

		// When the connectivity check succeeds, a list-inbox task should be
		// started for the first download cycle
		AtomicReference<ApiCall> listTask = new AtomicReference<>();
		expectStartTask(listTask);
		worker.onConnectivityCheckSucceeded();

		// When the list-inbox tasks runs and finds some files to download,
		// it should start a download task for the first file
		AtomicReference<ApiCall> downloadTask = new AtomicReference<>();
		expectCheckForFiles(mailboxProperties.getInboxId(), files);
		expectStartTask(downloadTask);
		assertFalse(listTask.get().callApi());

		// When the first download task runs it should download the file to the
		// location provided by the file manager and start a delete task
		AtomicReference<ApiCall> deleteTask = new AtomicReference<>();
		expectDownloadFile(mailboxProperties.getInboxId(), file1);
		expectStartTask(deleteTask);
		assertFalse(downloadTask.get().callApi());

		// When the first delete task runs it should delete the file, ignore
		// the tolerable failure, and start a download task for the next file
		expectDeleteFile(mailboxProperties.getInboxId(), file1, true);
		expectStartTask(downloadTask);
		assertFalse(deleteTask.get().callApi());

		// When the second download task runs it should download the file to
		// the location provided by the file manager and start a delete task
		expectDownloadFile(mailboxProperties.getInboxId(), file2);
		expectStartTask(deleteTask);
		assertFalse(downloadTask.get().callApi());

		// When the second delete task runs it should delete the file and
		// start a list-inbox task to check for files that may have arrived
		// since the first download cycle started
		expectDeleteFile(mailboxProperties.getInboxId(), file2, false);
		expectStartTask(listTask);
		assertFalse(deleteTask.get().callApi());

		// When the list-inbox tasks runs and finds no more files to download,
		// it should add a Tor reachability observer
		expectCheckForFiles(mailboxProperties.getInboxId(), emptyList());
		expectAddReachabilityObserver();
		assertFalse(listTask.get().callApi());

		// When the reachability observer is called, a list-inbox task should
		// be started for the second download cycle
		expectStartTask(listTask);
		worker.onTorReachable();

		// When the list-inbox tasks runs and finds no more files to download,
		// it should finish the second download cycle
		expectCheckForFiles(mailboxProperties.getInboxId(), emptyList());
		assertFalse(listTask.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		expectRemoveObservers();
		worker.destroy();
	}
}
