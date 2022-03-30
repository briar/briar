package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxPropertiesUpdate {

	private final String onion;
	private final MailboxAuthToken authToken;
	private final MailboxFolderId inboxId;
	private final MailboxFolderId outboxId;

	public MailboxPropertiesUpdate(String onion,
			MailboxAuthToken authToken, MailboxFolderId inboxId,
			MailboxFolderId outboxId) {
		this.onion = onion;
		this.authToken = authToken;
		this.inboxId = inboxId;
		this.outboxId = outboxId;
	}

	public String getOnion() {
		return onion;
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
