package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.logging.Logger.getLogger;

@ThreadSafe
@NotNullByDefault
class OwnMailboxClient implements MailboxClient {

	private static final Logger LOG =
			getLogger(OwnMailboxClient.class.getName());

	private final MailboxWorkerFactory workerFactory;
	private final ConnectivityChecker connectivityChecker;
	private final TorReachabilityMonitor reachabilityMonitor;
	private final MailboxWorker contactListWorker;
	private final Object lock = new Object();

	@GuardedBy("lock")
	private final Map<ContactId, MailboxWorker> uploadWorkers = new HashMap<>();

	@GuardedBy("lock")
	@Nullable
	private MailboxWorker downloadWorker = null;

	@GuardedBy("lock")
	private final Set<ContactId> assignedForDownload = new HashSet<>();

	OwnMailboxClient(MailboxWorkerFactory workerFactory,
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor reachabilityMonitor,
			MailboxProperties properties) {
		this.workerFactory = workerFactory;
		this.connectivityChecker = connectivityChecker;
		this.reachabilityMonitor = reachabilityMonitor;
		contactListWorker = workerFactory.createContactListWorkerForOwnMailbox(
				connectivityChecker, properties);
	}

	@Override
	public void start() {
		LOG.info("Started");
		contactListWorker.start();
	}

	@Override
	public void destroy() {
		LOG.info("Destroyed");
		List<MailboxWorker> uploadWorkers;
		MailboxWorker downloadWorker;
		synchronized (lock) {
			uploadWorkers = new ArrayList<>(this.uploadWorkers.values());
			this.uploadWorkers.clear();
			downloadWorker = this.downloadWorker;
			this.downloadWorker = null;
		}
		for (MailboxWorker worker : uploadWorkers) worker.destroy();
		if (downloadWorker != null) downloadWorker.destroy();
		contactListWorker.destroy();
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
		MailboxWorker toStart = null;
		synchronized (lock) {
			if (!assignedForDownload.add(contactId)) {
				throw new IllegalStateException();
			}
			// Create a download worker if we don't already have one
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
		MailboxWorker toDestroy = null;
		synchronized (lock) {
			if (!assignedForDownload.remove(contactId)) {
				throw new IllegalStateException();
			}
			// If there are no more contacts assigned for download, destroy
			// the download worker
			if (assignedForDownload.isEmpty()) {
				toDestroy = downloadWorker;
				downloadWorker = null;
			}
		}
		if (toDestroy != null) toDestroy.destroy();
	}
}
