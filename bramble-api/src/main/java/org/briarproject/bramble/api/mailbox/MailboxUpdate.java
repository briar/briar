package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxUpdate {

	boolean hasMailbox;
	private final List<MailboxVersion> clientSupports;

	public MailboxUpdate(List<MailboxVersion> clientSupports) {
		this.hasMailbox = false;
		this.clientSupports = clientSupports;
	}

	public List<MailboxVersion> getClientSupports() {
		return clientSupports;
	}

	public boolean hasMailbox() {
		return hasMailbox;
	}
}
