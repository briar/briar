package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxProperties {

	private final String baseUrl;
	private final MailboxAuthToken authToken;
	private final boolean owner;
	private final List<MailboxVersion> serverSupports;
	@Nullable
	private final MailboxFolderId inboxId; // Null for own mailbox
	@Nullable
	private final MailboxFolderId outboxId; // Null for own mailbox

	/**
	 * Constructor for properties used by the mailbox's owner.
	 */
	public MailboxProperties(String baseUrl, MailboxAuthToken authToken,
			List<MailboxVersion> serverSupports) {
		this.baseUrl = baseUrl;
		this.authToken = authToken;
		this.owner = true;
		this.serverSupports = serverSupports;
		this.inboxId = null;
		this.outboxId = null;
	}

	/**
	 * Constructor for properties used by a contact of the mailbox's owner.
	 */
	public MailboxProperties(String baseUrl, MailboxAuthToken authToken,
			List<MailboxVersion> serverSupports, MailboxFolderId inboxId,
			MailboxFolderId outboxId) {
		this.baseUrl = baseUrl;
		this.authToken = authToken;
		this.owner = false;
		this.serverSupports = serverSupports;
		this.inboxId = inboxId;
		this.outboxId = outboxId;
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

	@Nullable
	public MailboxFolderId getInboxId() {
		return inboxId;
	}

	@Nullable
	public MailboxFolderId getOutboxId() {
		return outboxId;
	}
}
