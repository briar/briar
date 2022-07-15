package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.mailbox.TorReachabilityMonitor.TorReachabilityObserver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.Collections.shuffle;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class OwnMailboxDownloadWorker implements MailboxWorker, ConnectivityObserver,
		TorReachabilityObserver {

	/**
	 * When the worker is started it waits for a connectivity check, then
	 * starts its first download cycle: checking for folders with available
	 * files, listing the files in each folder, downloading and deleting the
	 * files, and checking again until all files have been downloaded and
	 * deleted.
	 * <p>
	 * The worker then waits for our Tor hidden service to be reachable before
	 * starting its second download cycle. This ensures that if a contact
	 * tried and failed to connect to our hidden service before it was
	 * reachable, and therefore uploaded a file to the mailbox instead, we'll
	 * find the file in the second download cycle.
	 */
	private enum State {
		CREATED,
		CONNECTIVITY_CHECK,
		DOWNLOAD_CYCLE_1,
		WAITING_FOR_TOR,
		DOWNLOAD_CYCLE_2,
		FINISHED,
		DESTROYED
	}

	/**
	 * The maximum number of files that will be downloaded before checking
	 * again for folders with available files. This ensures that if a file
	 * arrives during a download cycle, its folder will be checked within a
	 * reasonable amount of time even if another folder has a very large number
	 * of files.
	 * <p>
	 * Package access for testing.
	 */
	static final int MAX_ROUND_ROBIN_FILES = 1000;

	private static final Logger LOG =
			getLogger(OwnMailboxDownloadWorker.class.getName());

	private final ConnectivityChecker connectivityChecker;
	private final TorReachabilityMonitor torReachabilityMonitor;
	private final MailboxApiCaller mailboxApiCaller;
	private final MailboxApi mailboxApi;
	private final MailboxFileManager mailboxFileManager;
	private final MailboxProperties mailboxProperties;
	private final Object lock = new Object();

	@GuardedBy("lock")
	private State state = State.CREATED;

	@GuardedBy("lock")
	@Nullable
	private Cancellable apiCall = null;

	OwnMailboxDownloadWorker(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor torReachabilityMonitor,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxFileManager mailboxFileManager,
			MailboxProperties mailboxProperties) {
		if (!mailboxProperties.isOwner()) throw new IllegalArgumentException();
		this.connectivityChecker = connectivityChecker;
		this.torReachabilityMonitor = torReachabilityMonitor;
		this.mailboxApiCaller = mailboxApiCaller;
		this.mailboxApi = mailboxApi;
		this.mailboxFileManager = mailboxFileManager;
		this.mailboxProperties = mailboxProperties;
	}

	@Override
	public void start() {
		LOG.info("Started");
		synchronized (lock) {
			// Don't allow the worker to be reused
			if (state != State.CREATED) return;
			state = State.CONNECTIVITY_CHECK;
		}
		// Avoid leaking observer in case destroy() is called concurrently
		// before observer is added
		connectivityChecker.checkConnectivity(mailboxProperties, this);
		boolean destroyed;
		synchronized (lock) {
			destroyed = state == State.DESTROYED;
		}
		if (destroyed) connectivityChecker.removeObserver(this);
	}

	@Override
	public void destroy() {
		LOG.info("Destroyed");
		Cancellable apiCall;
		synchronized (lock) {
			state = State.DESTROYED;
			apiCall = this.apiCall;
			this.apiCall = null;
		}
		if (apiCall != null) apiCall.cancel();
		connectivityChecker.removeObserver(this);
		torReachabilityMonitor.removeObserver(this);
	}

	@Override
	public void onConnectivityCheckSucceeded() {
		LOG.info("Connectivity check succeeded");
		synchronized (lock) {
			if (state != State.CONNECTIVITY_CHECK) return;
			state = State.DOWNLOAD_CYCLE_1;
			// Start first download cycle
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(this::apiCallListFolders));
		}
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

	private void onDownloadCycleFinished() {
		boolean addObserver = false;
		synchronized (lock) {
			if (state == State.DOWNLOAD_CYCLE_1) {
				LOG.info("First download cycle finished");
				state = State.WAITING_FOR_TOR;
				apiCall = null;
				addObserver = true;
			} else if (state == State.DOWNLOAD_CYCLE_2) {
				LOG.info("Second download cycle finished");
				state = State.FINISHED;
				apiCall = null;
			}
		}
		if (addObserver) {
			// Avoid leaking observer in case destroy() is called concurrently
			// before observer is added
			torReachabilityMonitor.addOneShotObserver(this);
			boolean destroyed;
			synchronized (lock) {
				destroyed = state == State.DESTROYED;
			}
			if (destroyed) torReachabilityMonitor.removeObserver(this);
		}
	}

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
		List<MailboxFile> files =
				mailboxApi.getFiles(mailboxProperties, folder);
		if (!files.isEmpty()) available.put(folder, new LinkedList<>(files));
		if (queue.isEmpty()) {
			LOG.info("Finished listing folders");
			if (available.isEmpty()) onDownloadCycleFinished();
			else createDownloadQueue(available);
		} else {
			listNextFolder(queue, available);
		}
	}

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

	private void downloadNextFile(Queue<FolderFile> queue) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			FolderFile file = queue.remove();
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(() -> apiCallDownloadFile(file, queue)));
		}
	}

	private void apiCallDownloadFile(FolderFile file,
			Queue<FolderFile> queue) throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		LOG.info("Downloading file");
		File tempFile = mailboxFileManager.createTempFileForDownload();
		try {
			mailboxApi.getFile(mailboxProperties, file.folderId, file.fileId,
					tempFile);
		} catch (IOException | ApiException e) {
			if (!tempFile.delete()) {
				LOG.warning("Failed to delete temporary file");
			}
			throw e;
		}
		mailboxFileManager.handleDownloadedFile(tempFile);
		deleteFile(file, queue);
	}

	private void deleteFile(FolderFile file, Queue<FolderFile> queue) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(() -> apiCallDeleteFile(file, queue)));
		}
	}

	private void apiCallDeleteFile(FolderFile file, Queue<FolderFile> queue)
			throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		try {
			mailboxApi.deleteFile(mailboxProperties, file.folderId,
					file.fileId);
		} catch (TolerableFailureException e) {
			// Catch this so we can continue to the next file
			logException(LOG, INFO, e);
		}
		if (queue.isEmpty()) {
			// List the folders with available files again to check for files
			// that may have arrived while we were downloading
			synchronized (lock) {
				if (state == State.DESTROYED) return;
				apiCall = mailboxApiCaller.retryWithBackoff(
						new SimpleApiCall(this::apiCallListFolders));
			}
		} else {
			downloadNextFile(queue);
		}
	}

	@Override
	public void onTorReachable() {
		LOG.info("Our Tor hidden service is reachable");
		synchronized (lock) {
			if (state != State.WAITING_FOR_TOR) return;
			state = State.DOWNLOAD_CYCLE_2;
			// Start second download cycle
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(this::apiCallListFolders));
		}
	}

	// Package access for testing
	static class FolderFile {

		final MailboxFolderId folderId;
		final MailboxFileId fileId;

		private FolderFile(MailboxFolderId folderId, MailboxFileId fileId) {
			this.folderId = folderId;
			this.fileId = fileId;
		}
	}
}
