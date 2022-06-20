package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
@NotNullByDefault
class ContactMailboxClient implements MailboxClient {

	private final MailboxWorkerFactory workerFactory;
	private final ConnectivityChecker connectivityChecker;
	private final TorReachabilityMonitor reachabilityMonitor;
	private final Object lock = new Object();

	@GuardedBy("lock")
	@Nullable
	private MailboxWorker uploadWorker = null, downloadWorker = null;

	@Inject
	ContactMailboxClient(MailboxWorkerFactory workerFactory,
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor reachabilityMonitor) {
		this.workerFactory = workerFactory;
		this.connectivityChecker = connectivityChecker;
		this.reachabilityMonitor = reachabilityMonitor;
	}

	@Override
	public void start() {
		// Nothing to do until contact is assigned
	}

	@Override
	public void destroy() {
		MailboxWorker uploadWorker, downloadWorker;
		synchronized (lock) {
			uploadWorker = this.uploadWorker;
			this.uploadWorker = null;
			downloadWorker = this.downloadWorker;
			this.downloadWorker = null;
		}
		if (uploadWorker != null) uploadWorker.destroy();
		if (downloadWorker != null) downloadWorker.destroy();
	}

	@Override
	public void assignContactForUpload(ContactId contactId,
			MailboxProperties properties, MailboxFolderId folderId) {
		if (properties.isOwner()) throw new IllegalArgumentException();
		// For a contact's mailbox we should always be uploading to the outbox
		// assigned to us by the contact
		if (!folderId.equals(properties.getOutboxId())) {
			throw new IllegalArgumentException();
		}
		MailboxWorker uploadWorker = workerFactory.createUploadWorker(
				connectivityChecker, properties, folderId, contactId);
		synchronized (lock) {
			if (this.uploadWorker != null) throw new IllegalStateException();
			this.uploadWorker = uploadWorker;
		}
		uploadWorker.start();
	}

	@Override
	public void deassignContactForUpload(ContactId contactId) {
		MailboxWorker uploadWorker;
		synchronized (lock) {
			uploadWorker = this.uploadWorker;
			this.uploadWorker = null;
		}
		if (uploadWorker != null) uploadWorker.destroy();
	}

	@Override
	public void assignContactForDownload(ContactId contactId,
			MailboxProperties properties, MailboxFolderId folderId) {
		if (properties.isOwner()) throw new IllegalArgumentException();
		// For a contact's mailbox we should always be downloading from the
		// inbox assigned to us by the contact
		if (!folderId.equals(properties.getInboxId())) {
			throw new IllegalArgumentException();
		}
		MailboxWorker downloadWorker =
				workerFactory.createDownloadWorkerForContactMailbox(
						connectivityChecker, reachabilityMonitor, properties);
		synchronized (lock) {
			if (this.downloadWorker != null) throw new IllegalStateException();
			this.downloadWorker = downloadWorker;
		}
		downloadWorker.start();
	}

	@Override
	public void deassignContactForDownload(ContactId contactId) {
		MailboxWorker downloadWorker;
		synchronized (lock) {
			downloadWorker = this.downloadWorker;
			this.downloadWorker = null;
		}
		if (downloadWorker != null) downloadWorker.destroy();
	}
}
