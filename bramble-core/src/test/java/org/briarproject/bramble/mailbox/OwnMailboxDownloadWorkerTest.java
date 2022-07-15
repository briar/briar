package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.mailbox.OwnMailboxDownloadWorker.FolderFile;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.CaptureArgumentAction;
import org.jmock.Expectations;
import org.jmock.lib.action.DoAllAction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.CLIENT_SUPPORTS;
import static org.briarproject.bramble.mailbox.OwnMailboxDownloadWorker.MAX_ROUND_ROBIN_FILES;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OwnMailboxDownloadWorkerTest extends BrambleMockTestCase {

	private final ConnectivityChecker connectivityChecker =
			context.mock(ConnectivityChecker.class);
	private final TorReachabilityMonitor torReachabilityMonitor =
			context.mock(TorReachabilityMonitor.class);
	private final MailboxApiCaller mailboxApiCaller =
			context.mock(MailboxApiCaller.class);
	private final MailboxApi mailboxApi = context.mock(MailboxApi.class);
	private final MailboxFileManager mailboxFileManager =
			context.mock(MailboxFileManager.class);
	private final Cancellable apiCall = context.mock(Cancellable.class);

	private final MailboxProperties mailboxProperties =
			getMailboxProperties(true, CLIENT_SUPPORTS);
	private final long now = System.currentTimeMillis();
	private final MailboxFolderId folderId1 =
			new MailboxFolderId(getRandomId());
	private final MailboxFolderId folderId2 =
			new MailboxFolderId(getRandomId());
	private final List<MailboxFolderId> folderIds =
			asList(folderId1, folderId2);
	private final MailboxFile file1 =
			new MailboxFile(new MailboxFileId(getRandomId()), now - 1);
	private final MailboxFile file2 =
			new MailboxFile(new MailboxFileId(getRandomId()), now);
	private final List<MailboxFile> files = asList(file1, file2);

	private File testDir, tempFile;
	private OwnMailboxDownloadWorker worker;

	@Before
	public void setUp() {
		testDir = getTestDirectory();
		tempFile = new File(testDir, "temp");
		worker = new OwnMailboxDownloadWorker(connectivityChecker,
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

		// When the connectivity check succeeds, a list-folders task should be
		// started for the first download cycle
		AtomicReference<ApiCall> listFoldersTask = new AtomicReference<>();
		expectStartTask(listFoldersTask);
		worker.onConnectivityCheckSucceeded();

		// When the list-folders tasks runs and finds no folders with files
		// to download, it should add a Tor reachability observer
		expectCheckForFoldersWithAvailableFiles(emptyList());
		expectAddReachabilityObserver();
		assertFalse(listFoldersTask.get().callApi());

		// When the reachability observer is called, a list-folders task should
		// be started for the second download cycle
		expectStartTask(listFoldersTask);
		worker.onTorReachable();

		// When the list-folders tasks runs and finds no folders with files
		// to download, it should finish the second download cycle
		expectCheckForFoldersWithAvailableFiles(emptyList());
		assertFalse(listFoldersTask.get().callApi());

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

		// When the connectivity check succeeds, a list-folders task should be
		// started for the first download cycle
		AtomicReference<ApiCall> listFoldersTask = new AtomicReference<>();
		expectStartTask(listFoldersTask);
		worker.onConnectivityCheckSucceeded();

		// When the list-folders tasks runs and finds some folders with files
		// to download, it should start a list-files task for the first folder
		AtomicReference<ApiCall> listFilesTask = new AtomicReference<>();
		expectCheckForFoldersWithAvailableFiles(folderIds);
		expectStartTask(listFilesTask);
		assertFalse(listFoldersTask.get().callApi());

		// When the first list-files task runs and finds no files to download,
		// it should start a second list-files task for the next folder
		expectCheckForFiles(folderId1, emptyList());
		expectStartTask(listFilesTask);
		assertFalse(listFilesTask.get().callApi());

		// When the second list-files task runs and finds some files to
		// download, it should create the round-robin queue and start a
		// download task for the first file
		AtomicReference<ApiCall> downloadTask = new AtomicReference<>();
		expectCheckForFiles(folderId2, files);
		expectStartTask(downloadTask);
		assertFalse(listFilesTask.get().callApi());

		// When the first download task runs it should download the file to the
		// location provided by the file manager and start a delete task
		AtomicReference<ApiCall> deleteTask = new AtomicReference<>();
		expectDownloadFile(folderId2, file1);
		expectStartTask(deleteTask);
		assertFalse(downloadTask.get().callApi());

		// When the first delete task runs it should delete the file, ignore
		// the tolerable failure, and start a download task for the next file
		expectDeleteFile(folderId2, file1, true); // Delete fails tolerably
		expectStartTask(downloadTask);
		assertFalse(deleteTask.get().callApi());

		// When the second download task runs it should download the file to
		// the location provided by the file manager and start a delete task
		expectDownloadFile(folderId2, file2);
		expectStartTask(deleteTask);
		assertFalse(downloadTask.get().callApi());

		// When the second delete task runs it should delete the file and
		// start a list-inbox task to check for files that may have arrived
		// since the first download cycle started
		expectDeleteFile(folderId2, file2, false); // Delete succeeds
		expectStartTask(listFoldersTask);
		assertFalse(deleteTask.get().callApi());

		// When the list-inbox tasks runs and finds no more files to download,
		// it should add a Tor reachability observer
		expectCheckForFoldersWithAvailableFiles(emptyList());
		expectAddReachabilityObserver();
		assertFalse(listFoldersTask.get().callApi());

		// When the reachability observer is called, a list-inbox task should
		// be started for the second download cycle
		expectStartTask(listFoldersTask);
		worker.onTorReachable();

		// When the list-inbox tasks runs and finds no more files to download,
		// it should finish the second download cycle
		expectCheckForFoldersWithAvailableFiles(emptyList());
		assertFalse(listFoldersTask.get().callApi());

		// When the worker is destroyed it should remove the connectivity
		// and reachability observers
		expectRemoveObservers();
		worker.destroy();
	}

	@Test
	public void testRoundRobinQueueVisitsAllFolders() {
		// Ten folders with two files each
		Map<MailboxFolderId, Queue<MailboxFile>> available =
				createAvailableFiles(10, 2);
		Queue<FolderFile> queue = worker.createRoundRobinQueue(available);
		// Check that all files were queued
		for (MailboxFolderId folderId : available.keySet()) {
			assertEquals(2, countFilesWithFolderId(queue, folderId));
		}
	}

	@Test
	public void testSizeOfRoundRobinQueueIsLimited() {
		// Two folders with MAX_ROUND_ROBIN_FILES each
		Map<MailboxFolderId, Queue<MailboxFile>> available =
				createAvailableFiles(2, MAX_ROUND_ROBIN_FILES);
		Queue<FolderFile> queue = worker.createRoundRobinQueue(available);
		// Check that half the files in each folder were queued
		for (MailboxFolderId folderId : available.keySet()) {
			assertEquals(MAX_ROUND_ROBIN_FILES / 2,
					countFilesWithFolderId(queue, folderId));
		}
	}

	private void expectStartConnectivityCheck() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).checkConnectivity(
					with(mailboxProperties), with(worker));
		}});
	}

	private void expectStartTask(AtomicReference<ApiCall> task) {
		context.checking(new Expectations() {{
			oneOf(mailboxApiCaller).retryWithBackoff(with(any(ApiCall.class)));
			will(new DoAllAction(
					new CaptureArgumentAction<>(task, ApiCall.class, 0),
					returnValue(apiCall)
			));
		}});
	}

	private void expectCheckForFoldersWithAvailableFiles(
			List<MailboxFolderId> folderIds) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFolders(mailboxProperties);
			will(returnValue(folderIds));
		}});
	}

	private void expectCheckForFiles(MailboxFolderId folderId,
			List<MailboxFile> files) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).getFiles(mailboxProperties, folderId);
			will(returnValue(files));
		}});
	}

	private void expectDownloadFile(MailboxFolderId folderId, MailboxFile file)
			throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxFileManager).createTempFileForDownload();
			will(returnValue(tempFile));
			oneOf(mailboxApi).getFile(mailboxProperties, folderId, file.name,
					tempFile);
			oneOf(mailboxFileManager).handleDownloadedFile(tempFile);
		}});
	}

	private void expectDeleteFile(MailboxFolderId folderId, MailboxFile file,
			boolean tolerableFailure) throws Exception {
		context.checking(new Expectations() {{
			oneOf(mailboxApi).deleteFile(mailboxProperties, folderId,
					file.name);
			if (tolerableFailure) {
				will(throwException(new TolerableFailureException()));
			}
		}});
	}

	private void expectAddReachabilityObserver() {
		context.checking(new Expectations() {{
			oneOf(torReachabilityMonitor).addOneShotObserver(worker);
		}});
	}

	private void expectRemoveObservers() {
		context.checking(new Expectations() {{
			oneOf(connectivityChecker).removeObserver(worker);
			oneOf(torReachabilityMonitor).removeObserver(worker);
		}});
	}

	private Map<MailboxFolderId, Queue<MailboxFile>> createAvailableFiles(
			int numFolders, int numFiles) {
		Map<MailboxFolderId, Queue<MailboxFile>> available = new HashMap<>();
		List<MailboxFolderId> folderIds = createFolderIds(numFolders);
		for (MailboxFolderId folderId : folderIds) {
			available.put(folderId, createFiles(numFiles));
		}
		return available;
	}

	private List<MailboxFolderId> createFolderIds(int size) {
		List<MailboxFolderId> folderIds = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			folderIds.add(new MailboxFolderId(getRandomId()));
		}
		return folderIds;
	}

	private Queue<MailboxFile> createFiles(int size) {
		Queue<MailboxFile> files = new LinkedList<>();
		for (int i = 0; i < size; i++) {
			files.add(new MailboxFile(new MailboxFileId(getRandomId()), i));
		}
		return files;
	}

	private int countFilesWithFolderId(Queue<FolderFile> queue,
			MailboxFolderId folderId) {
		int count = 0;
		for (FolderFile file : queue) {
			if (file.folderId.equals(folderId)) count++;
		}
		return count;
	}
}
