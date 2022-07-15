package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.junit.Test;

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
import static org.briarproject.bramble.mailbox.MailboxDownloadWorker.FolderFile;
import static org.briarproject.bramble.mailbox.OwnMailboxDownloadWorker.MAX_ROUND_ROBIN_FILES;
import static org.briarproject.bramble.test.TestUtils.getMailboxProperties;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class OwnMailboxDownloadWorkerTest
		extends MailboxDownloadWorkerTest<OwnMailboxDownloadWorker> {

	private final MailboxFolderId folderId1 =
			new MailboxFolderId(getRandomId());
	private final MailboxFolderId folderId2 =
			new MailboxFolderId(getRandomId());
	private final List<MailboxFolderId> folderIds =
			asList(folderId1, folderId2);

	public OwnMailboxDownloadWorkerTest() {
		mailboxProperties = getMailboxProperties(true, CLIENT_SUPPORTS);
		worker = new OwnMailboxDownloadWorker(connectivityChecker,
				torReachabilityMonitor, mailboxApiCaller, mailboxApi,
				mailboxFileManager, mailboxProperties);
	}

	@Override
	public void setUp() {
		super.setUp();
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
