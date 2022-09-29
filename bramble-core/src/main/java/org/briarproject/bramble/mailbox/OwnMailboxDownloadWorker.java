package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.annotation.concurrent.ThreadSafe;

import static java.util.Collections.shuffle;
import static java.util.logging.Level.INFO;

@ThreadSafe
@NotNullByDefault
class OwnMailboxDownloadWorker extends MailboxDownloadWorker {

	/**
	 * The maximum number of files that will be downloaded before checking
	 * again for folders with available files. This ensures that if a file
	 * arrives during a download cycle, its folder will be checked within a
	 * reasonable amount of time even if some other folder has a very large
	 * number of files.
	 * <p>
	 * Package access for testing.
	 */
	static final int MAX_ROUND_ROBIN_FILES = 1000;

	OwnMailboxDownloadWorker(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor torReachabilityMonitor,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxFileManager mailboxFileManager,
			MailboxProperties mailboxProperties) {
		super(connectivityChecker, torReachabilityMonitor, mailboxApiCaller,
				mailboxApi, mailboxFileManager, mailboxProperties);
		if (!mailboxProperties.isOwner()) throw new IllegalArgumentException();
	}

	@Override
	protected ApiCall createApiCallForDownloadCycle() {
		return new SimpleApiCall(this::apiCallListFolders);
	}

	private void apiCallListFolders() throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		LOG.info("Listing folders with available files");
		List<MailboxFolderId> folders =
				mailboxApi.getFolders(mailboxProperties);
		if (folders.isEmpty()) onDownloadCycleFinished();
		else listNextFolder(new LinkedList<>(folders), new HashMap<>());
	}

	/**
	 * Removes the next folder from `queue` and starts a task to list the
	 * files in the folder and add them to `available`.
	 */
	private void listNextFolder(Queue<MailboxFolderId> queue,
			Map<MailboxFolderId, Queue<MailboxFile>> available) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			MailboxFolderId folder = queue.remove();
			apiCall = mailboxApiCaller.retryWithBackoff(new SimpleApiCall(() ->
					apiCallListFolder(folder, queue, available)));
		}
	}

	private void apiCallListFolder(MailboxFolderId folder,
			Queue<MailboxFolderId> queue,
			Map<MailboxFolderId, Queue<MailboxFile>> available)
			throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		LOG.info("Listing folder");
		try {
			List<MailboxFile> files =
					mailboxApi.getFiles(mailboxProperties, folder);
			if (!files.isEmpty()) {
				available.put(folder, new LinkedList<>(files));
			}
		} catch (TolerableFailureException e) {
			LOG.warning("Folder does not exist");
		}
		if (queue.isEmpty()) {
			LOG.info("Finished listing folders");
			if (available.isEmpty()) onDownloadCycleFinished();
			else createDownloadQueue(available);
		} else {
			listNextFolder(queue, available);
		}
	}

	/**
	 * Visits the given folders in round-robin order to create a queue of up to
	 * {@link #MAX_ROUND_ROBIN_FILES} to download.
	 */
	private void createDownloadQueue(
			Map<MailboxFolderId, Queue<MailboxFile>> available) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		if (LOG.isLoggable(INFO)) {
			LOG.info(available.size() + " folders have available files");
		}
		Queue<FolderFile> queue = createRoundRobinQueue(available);
		if (LOG.isLoggable(INFO)) {
			LOG.info("Downloading " + queue.size() + " files");
		}
		downloadNextFile(queue);
	}

	// Package access for testing
	Queue<FolderFile> createRoundRobinQueue(
			Map<MailboxFolderId, Queue<MailboxFile>> available) {
		List<MailboxFolderId> roundRobin = new ArrayList<>(available.keySet());
		// Shuffle the folders so we don't always favour the same folders
		shuffle(roundRobin);
		Queue<FolderFile> queue = new LinkedList<>();
		while (queue.size() < MAX_ROUND_ROBIN_FILES && !available.isEmpty()) {
			Iterator<MailboxFolderId> it = roundRobin.iterator();
			while (queue.size() < MAX_ROUND_ROBIN_FILES && it.hasNext()) {
				MailboxFolderId folder = it.next();
				Queue<MailboxFile> files = available.get(folder);
				MailboxFile file = files.remove();
				queue.add(new FolderFile(folder, file.name));
				if (files.isEmpty()) {
					available.remove(folder);
					it.remove();
				}
			}
		}
		return queue;
	}
}
