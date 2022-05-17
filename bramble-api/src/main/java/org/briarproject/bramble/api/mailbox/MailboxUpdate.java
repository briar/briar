package org.briarproject.bramble.api.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxUpdate {

	boolean hasMailbox;
	private List<MailboxVersion> clientSupports;

	public MailboxUpdate(List<MailboxVersion> clientSupports) {
		this.hasMailbox = false;
		this.clientSupports = clientSupports;
	}

	public List<MailboxVersion> getClientSupports() {
		return clientSupports;
	}

	public void setClientSupports(List<MailboxVersion> clientSupports) {
		this.clientSupports = clientSupports;
	}

	public boolean hasMailbox() {
		return hasMailbox;
	}
}
