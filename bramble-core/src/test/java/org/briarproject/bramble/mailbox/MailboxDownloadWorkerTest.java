package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;

abstract class MailboxDownloadWorkerTest<W extends MailboxDownloadWorker>
		extends BrambleMockTestCase {

	final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	final TorReachabilityMonitor torReachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	final MailboxFileManager mailboxFileManager =
			context.mock(MailboxFileManager.class);
	private final Cancellable apiCall = context.mock(Cancellable.class);

	private final long now = System.currentTimeMillis();
	final MailboxFile file1 =
			new MailboxFile(new MailboxFileId(getRandomId()), now - 1);
	final MailboxFile file2 =
			new MailboxFile(new MailboxFileId(getRandomId()), now);
	final List<MailboxFile> files = asList(file1, file2);

	private File testDir, tempFile;
	MailboxProperties mailboxProperties;
	W worker;

	@Before
	public void setUp() {
		testDir = getTestDirectory();
		tempFile = new File(testDir, "temp");
	}

	@After
	public void tearDown() {
		deleteTestDirectory(testDir);
	}


	void expectStartConnectivityCheck() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(
					with(mailboxProperties), with(worker));
		}});
	}

	void expectStartTask(AtomicReference<ApiCall> task) {
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});
	}

	void expectCheckForFoldersWithAvailableFiles(
			List<MailboxFolderId> folderIds) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFolders(mailboxProperties);
			will(returnValue(folderIds));
		}});
	}

	void expectCheckForFiles(MailboxFolderId folderId,
			List<MailboxFile> files) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFiles(mailboxProperties, folderId);
			will(returnValue(files));
		}});
	}

	void expectDownloadFile(MailboxFolderId folderId,
			MailboxFile file)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createTempFileForDownload();
			will(returnValue(tempFile));
			oneOf(mailboxApi).getFile(mailboxProperties, folderId, file.name,
					tempFile);
			oneOf(mailboxFileManager).handleDownloadedFile(tempFile);
		}});
	}

	void expectDeleteFile(MailboxFolderId folderId, MailboxFile file,
			boolean tolerableFailure) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteFile(mailboxProperties, folderId,
					file.name);
			if (tolerableFailure) {
				will(throwException(new TolerableFailureException()));
			}
		}});
	}

	void expectAddReachabilityObserver() {
		context.checking(new Expectations() {{
			oneOf(torReachabilityMonitor).addOneShotObserver(worker);
		}});
	}

	void expectRemoveObservers() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(torReachabilityMonitor).removeObserver(worker);
		}});
	}
}
