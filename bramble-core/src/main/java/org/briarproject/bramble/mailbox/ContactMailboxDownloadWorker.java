package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;
import org.briarproject.bramble.mailbox.MailboxApi.MailboxFile;
import org.briarproject.bramble.mailbox.MailboxApi.TolerableFailureException;
import org.briarproject.bramble.mailbox.TorReachabilityMonitor.TorReachabilityObserver;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class ContactMailboxDownloadWorker implements MailboxWorker,
		ConnectivityObserver, TorReachabilityObserver {

	private enum State {
		CREATED,
		CONNECTIVITY_CHECK,
		DOWNLOAD_CYCLE_1,
		WAITING_FOR_TOR,
		DOWNLOAD_CYCLE_2,
		FINISHED,
		DESTROYED
	}

	private static final Logger LOG =
			getLogger(ContactMailboxDownloadWorker.class.getName());

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

	@GuardedBy("lock")
	@Nullable
	private ConnectivityObserver connectivityObserver = null;

	@GuardedBy("lock")
	@Nullable
	private TorReachabilityObserver reachabilityObserver = null;

	ContactMailboxDownloadWorker(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor torReachabilityMonitor,
			MailboxApiCaller mailboxApiCaller,
			MailboxApi mailboxApi,
			MailboxFileManager mailboxFileManager,
			MailboxProperties mailboxProperties) {
		if (mailboxProperties.isOwner()) throw new IllegalArgumentException();
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
			if (state != State.CREATED) throw new IllegalStateException();
			state = State.CONNECTIVITY_CHECK;
		}
		connectivityChecker.checkConnectivity(mailboxProperties, this);
		// Avoid leaking observer in case destroy() is called concurrently
		boolean remove = false;
		synchronized (lock) {
			if (state == State.DESTROYED) remove = true;
			else connectivityObserver = this;
		}
		if (remove) connectivityChecker.removeObserver(this);
	}

	@Override
	public void destroy() {
		LOG.info("Destroyed");
		Cancellable apiCall;
		ConnectivityObserver connectivityObserver;
		TorReachabilityObserver reachabilityObserver;
		synchronized (lock) {
			state = State.DESTROYED;
			apiCall = this.apiCall;
			this.apiCall = null;
			connectivityObserver = this.connectivityObserver;
			this.connectivityObserver = null;
			reachabilityObserver = this.reachabilityObserver;
			this.reachabilityObserver = null;
		}
		if (apiCall != null) apiCall.cancel();
		if (connectivityObserver != null) {
			connectivityChecker.removeObserver(connectivityObserver);
		}
		if (reachabilityObserver != null) {
			torReachabilityMonitor.removeObserver(reachabilityObserver);
		}
	}

	@Override
	public void onConnectivityCheckSucceeded() {
		LOG.info("Connectivity check succeeded");
		synchronized (lock) {
			if (state != State.CONNECTIVITY_CHECK) return;
			state = State.DOWNLOAD_CYCLE_1;
			// No need to remove the observer in destroy()
			connectivityObserver = null;
			// Start first download cycle
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(this::apiCallListInbox));
		}
	}

	private void apiCallListInbox() throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		LOG.info("Listing inbox");
		List<MailboxFile> files = mailboxApi.getFiles(mailboxProperties,
				requireNonNull(mailboxProperties.getInboxId()));
		if (files.isEmpty()) onDownloadCycleFinished();
		else downloadNextFile(new LinkedList<>(files));
	}

	private void onDownloadCycleFinished() {
		boolean addObserver = false;
		synchronized (lock) {
			if (state == State.DOWNLOAD_CYCLE_1) {
				LOG.info("First download cycle finished");
				state = State.WAITING_FOR_TOR;
				addObserver = true;
			} else if (state == State.DOWNLOAD_CYCLE_2) {
				LOG.info("Second download cycle finished");
				state = State.FINISHED;
			}
		}
		if (addObserver) {
			torReachabilityMonitor.addOneShotObserver(this);
			// Avoid leaking observer in case destroy() is called concurrently
			boolean remove = false;
			synchronized (lock) {
				if (state == State.DESTROYED) remove = true;
				else reachabilityObserver = this;
			}
			if (remove) torReachabilityMonitor.removeObserver(this);
		}
	}

	private void downloadNextFile(Queue<MailboxFile> queue) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			MailboxFile file = queue.remove();
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(() -> apiCallDownloadFile(file, queue)));
		}
	}

	private void apiCallDownloadFile(MailboxFile file,
			Queue<MailboxFile> queue) throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		LOG.info("Downloading file");
		File tempFile = mailboxFileManager.createTempFileForDownload();
		try {
			mailboxApi.getFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()),
					file.name, tempFile);
		} catch (IOException | ApiException e) {
			if (!tempFile.delete()) {
				LOG.warning("Failed to delete temporary file");
			}
			throw e;
		}
		mailboxFileManager.handleDownloadedFile(tempFile);
		deleteFile(file, queue);
	}

	private void deleteFile(MailboxFile file, Queue<MailboxFile> queue) {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(() -> apiCallDeleteFile(file, queue)));
		}
	}

	private void apiCallDeleteFile(MailboxFile file, Queue<MailboxFile> queue)
			throws IOException, ApiException {
		synchronized (lock) {
			if (state == State.DESTROYED) return;
		}
		try {
			mailboxApi.deleteFile(mailboxProperties,
					requireNonNull(mailboxProperties.getInboxId()), file.name);
		} catch (TolerableFailureException e) {
			// Catch this so we can continue to the next file
			logException(LOG, INFO, e);
		}
		if (queue.isEmpty()) {
			// List the inbox again to check for files that may have arrived
			// while we were downloading
			synchronized (lock) {
				if (state == State.DESTROYED) return;
				apiCall = mailboxApiCaller.retryWithBackoff(
						new SimpleApiCall(this::apiCallListInbox));
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
			// No need to remove the observer in destroy()
			reachabilityObserver = null;
			// Start second download cycle
			apiCall = mailboxApiCaller.retryWithBackoff(
					new SimpleApiCall(this::apiCallListInbox));
		}
	}
}
