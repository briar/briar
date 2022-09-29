package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxFileId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.mailbox.TorReachabilityMonitor.TorReachabilityObserver;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
abstract class MailboxDownloadWorker implements MailboxWorker,
		ConnectivityObserver, TorReachabilityObserver {

	/**
	 * When the worker is started it waits for a connectivity check, then
	 * starts its first download cycle: checking for files to download,
	 * downloading and deleting the files, and checking again until all files
	 * have been downloaded and deleted.
	 * <p>
	 * The worker then waits for our Tor hidden service to be reachable before
	 * starting its second download cycle. This ensures that if a contact
	 * tried and failed to connect to our hidden service before it was
	 * reachable, and therefore uploaded a file to the mailbox instead, we'll
	 * find the file in the second download cycle.
	 */
	protected enum State {
		CREATED,
		CONNECTIVITY_CHECK,
		DOWNLOAD_CYCLE_1,
		WAITING_FOR_TOR,
		DOWNLOAD_CYCLE_2,
		FINISHED,
		DESTROYED
	}

	protected static final Logger LOG =
			getLogger(MailboxDownloadWorker.class.getName());

	private final ConnectivityChecker connectivityChecker;
	private final TorReachabilityMonitor torReachabilityMonitor;
	protected final MailboxApiCaller mailboxApiCaller;
	protected final MailboxApi mailboxApi;
	private final MailboxFileManager mailboxFileManager;
	protected final MailboxProperties mailboxProperties;
	protected final Object lock = new Object();

	@GuardedBy("lock")
	protected State state = State.CREATED;

	@GuardedBy("lock")
	@Nullable
	protected Cancellable apiCall = null;

	/**
	 * Creates the API call that starts the worker's download cycle.
	 */
	protected abstract ApiCall createApiCallForDownloadCycle();

	MailboxDownloadWorker(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor torReachabilityMonitor,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxFileManager mailboxFileManager,
			MailboxProperties mailboxProperties) {
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
					createApiCallForDownloadCycle());
		}
	}

	void onDownloadCycleFinished() {
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

	void downloadNextFile(Queue<FolderFile> queue) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			if (queue.isEmpty()) {
				// Check for files again, as new files may have arrived while
				// we were downloading
				apiCall = mailboxApiCaller.retryWithBackoff(
						createApiCallForDownloadCycle());
			} else {
				FolderFile file = queue.remove();
				apiCall = mailboxApiCaller.retryWithBackoff(
						new SimpleApiCall(() ->
								apiCallDownloadFile(file, queue)));
			}
		}
	}

	private void apiCallDownloadFile(FolderFile file, Queue<FolderFile> queue)
			throws IOException, ApiException {
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
		} catch (TolerableFailureException e) {
			// File not found - continue to the next file
			LOG.warning("File does not exist");
			if (!tempFile.delete()) {
				LOG.warning("Failed to delete temporary file");
			}
			downloadNextFile(queue);
			return;
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
			// File not found - continue to the next file
			LOG.warning("File does not exist");
		}
		downloadNextFile(queue);
	}

	@Override
	public void onTorReachable() {
		LOG.info("Our Tor hidden service is reachable");
		synchronized (lock) {
			if (state != State.WAITING_FOR_TOR) return;
			state = State.DOWNLOAD_CYCLE_2;
			// Start second download cycle
			apiCall = mailboxApiCaller.retryWithBackoff(
					createApiCallForDownloadCycle());
		}
	}

	// Package access for testing
	static class FolderFile {

		final MailboxFolderId folderId;
		final MailboxFileId fileId;

		FolderFile(MailboxFolderId folderId, MailboxFileId fileId) {
			this.folderId = folderId;
			this.fileId = fileId;
		}
	}
}
