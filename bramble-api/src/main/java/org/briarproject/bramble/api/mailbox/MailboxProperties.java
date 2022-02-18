package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxProperties {

	private final String baseUrl;
	private final MailboxAuthToken authToken;
	private final boolean owner;

	public MailboxProperties(String baseUrl, MailboxAuthToken authToken,
			boolean owner) {
		this.baseUrl = baseUrl;
		this.authToken = authToken;
		this.owner = owner;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public MailboxAuthToken getAuthToken() {
		return authToken;
	}

	public boolean isOwner() {
		return owner;
	}
}
