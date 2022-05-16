package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxUpdateWithMailbox extends MailboxUpdate {
	private final List<MailboxVersion> serverSupports;
	private final String onion;
	private final MailboxAuthToken authToken;
	private final MailboxFolderId inboxId;
	private final MailboxFolderId outboxId;

	public MailboxUpdateWithMailbox(List<MailboxVersion> clientSupports,
			List<MailboxVersion> serverSupports, String onion,
			MailboxAuthToken authToken, MailboxFolderId inboxId,
			MailboxFolderId outboxId
	) {
		super(clientSupports);
		this.hasMailbox = true;
		this.serverSupports = serverSupports;
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

	public List<MailboxVersion> getServerSupports() {
		return serverSupports;
	}
}
