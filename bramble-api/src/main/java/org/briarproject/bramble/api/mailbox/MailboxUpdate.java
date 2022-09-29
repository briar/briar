package org.briarproject.bramble.api.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxUpdate {
	private final boolean hasMailbox;
	private final List<MailboxVersion> clientSupports;

	public MailboxUpdate(List<MailboxVersion> clientSupports) {
		this(clientSupports, false);
	}

	MailboxUpdate(List<MailboxVersion> clientSupports, boolean hasMailbox) {
		this.clientSupports = clientSupports;
		this.hasMailbox = hasMailbox;
	}

	public List<MailboxVersion> getClientSupports() {
		return clientSupports;
	}

	public boolean hasMailbox() {
		return hasMailbox;
	}
}
