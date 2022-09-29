package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.mailbox.ConnectivityChecker.ConnectivityObserver;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
class OwnMailboxClient implements MailboxClient, ConnectivityObserver {

	/**
	 * How often to check our own mailbox's connectivity.
	 * <p>
	 * Package access for testing.
	 */
	static final long CONNECTIVITY_CHECK_INTERVAL_MS = HOURS.toMillis(1);

	private static final Logger LOG =
			getLogger(OwnMailboxClient.class.getName());

	private final MailboxWorkerFactory workerFactory;
	private final ConnectivityChecker connectivityChecker;
	private final TorReachabilityMonitor reachabilityMonitor;
	private final TaskScheduler taskScheduler;
	private final Executor ioExecutor;
	private final MailboxProperties properties;
	private final MailboxWorker contactListWorker;
	private final Object lock = new Object();

	/**
	 * Upload workers: one worker per contact assigned for upload.
	 */
	@GuardedBy("lock")
	private final Map<ContactId, MailboxWorker> uploadWorkers = new HashMap<>();

	/**
	 * Download worker: shared between all contacts assigned for download.
	 * Null if no contacts are assigned for download.
	 */
	@GuardedBy("lock")
	@Nullable
	private MailboxWorker downloadWorker = null;

	/**
	 * IDs of contacts assigned for download, so that we know when to
	 * create/destroy the download worker.
	 */
	@GuardedBy("lock")
	private final Set<ContactId> assignedForDownload = new HashSet<>();

	/**
	 * Scheduled task for periodically checking whether the mailbox is
	 * reachable.
	 */
	@GuardedBy("lock")
	@Nullable
	private Cancellable connectivityTask = null;

	@GuardedBy("lock")
	private boolean destroyed = false;

	OwnMailboxClient(MailboxWorkerFactory workerFactory,
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor reachabilityMonitor,
			TaskScheduler taskScheduler,
			@IoExecutor Executor ioExecutor,
			MailboxProperties properties) {
		if (!properties.isOwner()) throw new IllegalArgumentException();
		this.workerFactory = workerFactory;
		this.connectivityChecker = connectivityChecker;
		this.reachabilityMonitor = reachabilityMonitor;
		this.taskScheduler = taskScheduler;
		this.ioExecutor = ioExecutor;
		this.properties = properties;
		contactListWorker = workerFactory.createContactListWorkerForOwnMailbox(
				connectivityChecker, properties);
	}

	@Override
	public void start() {
		LOG.info("Started");
		checkConnectivity();
		contactListWorker.start();
	}

	@Override
	public void destroy() {
		LOG.info("Destroyed");
		List<MailboxWorker> uploadWorkers;
		MailboxWorker downloadWorker;
		Cancellable connectivityTask;
		synchronized (lock) {
			uploadWorkers = new ArrayList<>(this.uploadWorkers.values());
			this.uploadWorkers.clear();
			downloadWorker = this.downloadWorker;
			this.downloadWorker = null;
			connectivityTask = this.connectivityTask;
			this.connectivityTask = null;
			destroyed = true;
		}
		// Destroy the workers (with apologies to Mr Marx and Mr Engels)
		for (MailboxWorker worker : uploadWorkers) worker.destroy();
		if (downloadWorker != null) downloadWorker.destroy();
		// If a connectivity check is scheduled, cancel it
		if (connectivityTask != null) connectivityTask.cancel();
		contactListWorker.destroy();
		// The connectivity checker belongs to the client, so it should be
		// destroyed. The Tor reachability monitor is shared between clients,
		// so it should not be destroyed
		connectivityChecker.destroy();
	}

	@Override
	public void assignContactForUpload(ContactId contactId,
			MailboxProperties properties, MailboxFolderId folderId) {
		LOG.info("Contact assigned for upload");
		if (!properties.isOwner()) throw new IllegalArgumentException();
		MailboxWorker uploadWorker = workerFactory.createUploadWorker(
				connectivityChecker, properties, folderId, contactId);
		synchronized (lock) {
			MailboxWorker old = uploadWorkers.put(contactId, uploadWorker);
			if (old != null) throw new IllegalStateException();
		}
		uploadWorker.start();
	}

	@Override
	public void deassignContactForUpload(ContactId contactId) {
		LOG.info("Contact deassigned for upload");
		MailboxWorker uploadWorker;
		synchronized (lock) {
			uploadWorker = uploadWorkers.remove(contactId);
		}
		if (uploadWorker != null) uploadWorker.destroy();
	}

	@Override
	public void assignContactForDownload(ContactId contactId,
			MailboxProperties properties, MailboxFolderId folderId) {
		LOG.info("Contact assigned for download");
		if (!properties.isOwner()) throw new IllegalArgumentException();
		// Create a download worker if we don't already have one. The worker
		// will use the API to discover which folders have files to download,
		// so it doesn't need to track the set of assigned contacts
		MailboxWorker toStart = null;
		synchronized (lock) {
			if (!assignedForDownload.add(contactId)) {
				throw new IllegalStateException();
			}
			if (downloadWorker == null) {
				toStart = workerFactory.createDownloadWorkerForOwnMailbox(
						connectivityChecker, reachabilityMonitor, properties);
				downloadWorker = toStart;
			}
		}
		if (toStart != null) toStart.start();
	}

	@Override
	public void deassignContactForDownload(ContactId contactId) {
		LOG.info("Contact deassigned for download");
		// If there are no more contacts assigned for download, destroy the
		// download worker
		MailboxWorker toDestroy = null;
		synchronized (lock) {
			if (!assignedForDownload.remove(contactId)) {
				throw new IllegalStateException();
			}
			if (assignedForDownload.isEmpty()) {
				toDestroy = downloadWorker;
				downloadWorker = null;
			}
		}
		if (toDestroy != null) toDestroy.destroy();
	}

	private void checkConnectivity() {
		synchronized (lock) {
			if (destroyed) return;
			connectivityTask = null;
		}
		connectivityChecker.checkConnectivity(properties, this);
		// Avoid leaking observer in case destroy() is called concurrently
		// before observer is added
		boolean removeObserver;
		synchronized (lock) {
			removeObserver = destroyed;
		}
		if (removeObserver) connectivityChecker.removeObserver(this);
	}

	@Override
	public void onConnectivityCheckSucceeded() {
		synchronized (lock) {
			if (destroyed) return;
			connectivityTask = taskScheduler.schedule(this::checkConnectivity,
					ioExecutor, CONNECTIVITY_CHECK_INTERVAL_MS, MILLISECONDS);
		}
	}
}
