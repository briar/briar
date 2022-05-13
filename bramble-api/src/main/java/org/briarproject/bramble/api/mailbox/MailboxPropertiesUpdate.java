package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxPropertiesUpdate {

	boolean hasMailbox;
	private final List<MailboxVersion> clientSupports;

	public MailboxPropertiesUpdate(List<MailboxVersion> clientSupports) {
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
