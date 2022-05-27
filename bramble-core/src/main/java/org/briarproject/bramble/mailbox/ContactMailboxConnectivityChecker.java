package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.mailbox.MailboxApi.ApiException;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
class ContactMailboxConnectivityChecker extends ConnectivityCheckerImpl {

	private final MailboxApi mailboxApi;

	ContactMailboxConnectivityChecker(Clock clock,
			MailboxApiCaller mailboxApiCaller, MailboxApi mailboxApi) {
		super(clock, mailboxApiCaller);
		this.mailboxApi = mailboxApi;
	}

	@Override
	ApiCall createConnectivityCheckTask(MailboxProperties properties) {
		if (properties.isOwner()) throw new IllegalArgumentException();
		return new SimpleApiCall(() -> {
			if (!mailboxApi.checkStatus(properties)) throw new ApiException();
			// Call the observers and cache the result
			onConnectivityCheckSucceeded(clock.currentTimeMillis());
		});
	}

}
