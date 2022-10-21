package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class TestMailboxConfigImpl implements MailboxConfig {

	@Inject
	TestMailboxConfigImpl() {
	}

	@Override
	public long getApiCallerMinRetryInterval() {
		return 1000; // MailboxApiCaller.MIN_RETRY_INTERVAL_MS;
	}

	@Override
	public long getApiCallerMaxRetryInterval() {
		return 2000; // MailboxApiCaller.MAX_RETRY_INTERVAL_MS;
	}

	@Override
	public long getTorReachabilityPeriod() {
		return 5000; // TorReachabilityMonitor.REACHABILITY_PERIOD_MS;
	}
}
