package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.inject.Inject;
import javax.inject.Provider;

@NotNullByDefault
class MailboxClientFactoryImpl implements MailboxClientFactory {

	private final MailboxWorkerFactory workerFactory;
	private final Provider<ContactMailboxConnectivityChecker>
			contactCheckerProvider;
	private final Provider<OwnMailboxConnectivityChecker> ownCheckerProvider;

	@Inject
	MailboxClientFactoryImpl(MailboxWorkerFactory workerFactory,
			Provider<ContactMailboxConnectivityChecker> contactCheckerProvider,
			Provider<OwnMailboxConnectivityChecker> ownCheckerProvider) {
		this.workerFactory = workerFactory;
		this.contactCheckerProvider = contactCheckerProvider;
		this.ownCheckerProvider = ownCheckerProvider;
	}

	@Override
	public MailboxClient createContactMailboxClient(
			TorReachabilityMonitor reachabilityMonitor) {
		ConnectivityChecker connectivityChecker = contactCheckerProvider.get();
		return new ContactMailboxClient(workerFactory, connectivityChecker,
				reachabilityMonitor);
	}

	@Override
	public MailboxClient createOwnMailboxClient(
			TorReachabilityMonitor reachabilityMonitor,
			MailboxProperties properties) {
		ConnectivityChecker connectivityChecker = ownCheckerProvider.get();
		return new OwnMailboxClient(workerFactory, connectivityChecker,
				reachabilityMonitor, properties);
	}
}
