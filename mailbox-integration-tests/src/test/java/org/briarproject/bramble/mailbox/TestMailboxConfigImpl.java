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
		return 1000;
	}

	@Override
	public long getApiCallerMaxRetryInterval() {
		return 2000;
	}

	@Override
	public long getTorReachabilityPeriod() {
		return 10_000;
	}
}
