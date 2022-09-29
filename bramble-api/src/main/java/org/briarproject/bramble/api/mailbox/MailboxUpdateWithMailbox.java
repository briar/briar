package org.briarproject.bramble.api.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxUpdateWithMailbox extends MailboxUpdate {

	private final MailboxProperties properties;

	public MailboxUpdateWithMailbox(List<MailboxVersion> clientSupports,
			MailboxProperties properties) {
		super(clientSupports, true);
		if (properties.isOwner()) throw new IllegalArgumentException();
		this.properties = properties;
	}

	public MailboxUpdateWithMailbox(MailboxUpdateWithMailbox o,
			List<MailboxVersion> newClientSupports) {
		this(newClientSupports, o.getMailboxProperties());
	}

	public MailboxProperties getMailboxProperties() {
		return properties;
	}
}
