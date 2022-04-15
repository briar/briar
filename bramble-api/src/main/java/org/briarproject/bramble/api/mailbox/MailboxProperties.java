package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxProperties {

	private final String baseUrl;
	private final MailboxAuthToken authToken;
	private final boolean owner;
	private final List<MailboxVersion> serverSupports;

	public MailboxProperties(String baseUrl, MailboxAuthToken authToken,
			boolean owner, List<MailboxVersion> serverSupports) {
		this.baseUrl = baseUrl;
		this.authToken = authToken;
		this.owner = owner;
		this.serverSupports = serverSupports;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public String getOnion() {
		return baseUrl.replaceFirst("^http://", "")
				.replaceFirst("\\.onion$", "");
	}

	public MailboxAuthToken getAuthToken() {
		return authToken;
	}

	public boolean isOwner() {
		return owner;
	}

	public List<MailboxVersion> getServerSupports() {
		return serverSupports;
	}
}
