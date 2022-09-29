package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.mailbox.MailboxFolderId;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
interface MailboxWorkerFactory {

	MailboxWorker createUploadWorker(ConnectivityChecker connectivityChecker,
			MailboxProperties properties, MailboxFolderId folderId,
			ContactId contactId);

	MailboxWorker createDownloadWorkerForContactMailbox(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor reachabilityMonitor,
			MailboxProperties properties);

	MailboxWorker createDownloadWorkerForOwnMailbox(
			ConnectivityChecker connectivityChecker,
			TorReachabilityMonitor reachabilityMonitor,
			MailboxProperties properties);

	MailboxWorker createContactListWorkerForOwnMailbox(
			ConnectivityChecker connectivityChecker,
			MailboxProperties properties);
}
