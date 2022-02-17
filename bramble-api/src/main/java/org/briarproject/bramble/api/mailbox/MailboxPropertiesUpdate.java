package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxPropertiesUpdate {

	private final String onionAddress;
	private final MailboxAuthToken authToken;
	private final MailboxFolderId inboxId;
	private final MailboxFolderId outboxId;

	public MailboxPropertiesUpdate(String onionAddress,
			MailboxAuthToken authToken, MailboxFolderId inboxId,
			MailboxFolderId outboxId) {
		this.onionAddress = onionAddress;
		this.authToken = authToken;
		this.inboxId = inboxId;
		this.outboxId = outboxId;
	}

	public String getOnionAddress() {
		return onionAddress;
	}

	public MailboxAuthToken getAuthToken() {
		return authToken;
	}

	public MailboxFolderId getInboxId() {
		return inboxId;
	}

	public MailboxFolderId getOutboxId() {
		return outboxId;
	}

}
