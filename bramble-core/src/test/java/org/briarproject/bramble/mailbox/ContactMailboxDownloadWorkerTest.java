package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertFalse;

public class ContactMailboxDownloadWorkerTest extends BrambleMockTestCase {

	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final TorReachabilityMonitor torReachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	private final MailboxFileManager mailboxFileManager =
			context.mock(MailboxFileManager.class);

	private final MailboxProperties mailboxProperties =
			getMailboxProperties(false, CLIENT_SUPPORTS);
	private final long now = System.currentTimeMillis();
	private final MailboxFile file1 =
			new MailboxFile(new MailboxFileId(getRandomId()), now - 1);
	private final MailboxFile file2 =
			new MailboxFile(new MailboxFileId(getRandomId()), now);
	private final List<MailboxFile> files = asList(file1, file2);

	private File testDir, tempFile;
	private ContactMailboxDownloadWorker worker;

	@Before
	public void setUp() {
		testDir = getTestDirectory();
		tempFile = new File(testDir, "temp");
		worker = new ContactMailboxDownloadWorker(connectivityChecker,
				torReachabilityMonitor, mailboxApiCaller, mailboxApi,
				mailboxFileManager, mailboxProperties);
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}

	@Test
	public void testChecksConnectivityWhenStartedAndRemovesObserverWhenDestroyed() {
		// When the worker is started it should start a connectivity check
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(
					with(mailboxProperties), with(worker));
		}});

		worker.start();

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(torReachabilityMonitor).removeObserver(worker);
		}});

		worker.destroy();
	}

	@Test
	public void testDownloadsFilesWhenConnectivityCheckSucceeds()
			throws Exception {
		// When the worker is started it should start a connectivity check
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(
					with(mailboxProperties), with(worker));
		}});

		worker.start();

		// When the connectivity check succeeds, a list-inbox task should be
		// started for the first download cycle
		AtomicReference<ApiCall> listTask = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(listTask, ApiCall.class, 0));
		}});

		worker.onConnectivityCheckSucceeded();

		// When the list-inbox tasks runs and finds some files to download,
		// it should start a download task for the first file
		AtomicReference<ApiCall> downloadTask = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFiles(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()));
			will(returnValue(files));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(downloadTask, ApiCall.class, 0));
		}});

		assertFalse(listTask.get().callApi());

		// When the first download task runs it should download the file to the
		// location provided by the file manager and start a delete task
		AtomicReference<ApiCall> deleteTask = new AtomicReference<>(null);
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createTempFileForDownload();
			will(returnValue(tempFile));
			oneOf(mailboxApi).getFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()),
					file1.name, tempFile);
			oneOf(mailboxFileManager).handleDownloadedFile(tempFile);
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(deleteTask, ApiCall.class, 0));
		}});

		assertFalse(downloadTask.get().callApi());

		// When the first delete task runs it should delete the file, ignore
		// the tolerable failure, and start a download task for the next file
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()), file1.name);
			will(throwException(new TolerableFailureException()));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(downloadTask, ApiCall.class, 0));
		}});

		assertFalse(deleteTask.get().callApi());

		// When the second download task runs it should download the file to
		// the location provided by the file manager and start a delete task
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createTempFileForDownload();
			will(returnValue(tempFile));
			oneOf(mailboxApi).getFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()),
					file2.name, tempFile);
			oneOf(mailboxFileManager).handleDownloadedFile(tempFile);
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(deleteTask, ApiCall.class, 0));
		}});

		assertFalse(downloadTask.get().callApi());

		// When the second delete task runs it should delete the file and
		// start a list-inbox task to check for files that may have arrived
		// since the first download cycle started
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()), file2.name);
			will(throwException(new TolerableFailureException()));
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(listTask, ApiCall.class, 0));
		}});

		assertFalse(deleteTask.get().callApi());

		// When the list-inbox tasks runs and finds no more files to download,
		// it should add a Tor reachability observer
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFiles(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()));
			will(returnValue(emptyList()));
			oneOf(torReachabilityMonitor).addOneShotObserver(worker);
		}});

		assertFalse(listTask.get().callApi());

		// When the reachability observer is called, a list-inbox task should
		// be started for the second download cycle
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new CaptureArgumentAction<>(listTask, ApiCall.class, 0));
		}});

		worker.onTorReachable();

		// When the list-inbox tasks runs and finds no more files to download,
		// it should finish the second download cycle
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFiles(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()));
			will(returnValue(emptyList()));
		}});

		assertFalse(listTask.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(torReachabilityMonitor).removeObserver(worker);
		}});

		worker.destroy();
	}
}
