package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class MailboxConfigImpl implements MailboxConfig {

	@Inject
	MailboxConfigImpl() {
	}

	@Override
	public long getApiCallerMinRetryInterval() {
		return MailboxApiCaller.MIN_RETRY_INTERVAL_MS;
	}

	@Override
	public long getApiCallerMaxRetryInterval() {
		return MailboxApiCaller.MAX_RETRY_INTERVAL_MS;
	}

	@Override
	public long getTorReachabilityPeriod() {
		return TorReachabilityMonitor.REACHABILITY_PERIOD_MS;
	}
}
