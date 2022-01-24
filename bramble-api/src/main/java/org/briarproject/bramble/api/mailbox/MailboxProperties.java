package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxProperties {

	private final String onionAddress;
	private final MailboxId authToken;
	private final boolean owner;

	public MailboxProperties(String onionAddress, MailboxId authToken,
			boolean owner) {
		this.onionAddress = onionAddress;
		this.authToken = authToken;
		this.owner = owner;
	}

	public String getOnionAddress() {
		return onionAddress;
	}

	public MailboxId getAuthToken() {
		return authToken;
	}

	public boolean isOwner() {
		return owner;
	}
}
