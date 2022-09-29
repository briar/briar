package org.briarproject.bramble.api.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;
import org.briarproject.nullsafety.NullSafety;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MailboxProperties {

	private final String onion;
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
	public MailboxProperties(String onion, MailboxAuthToken authToken,
			List<MailboxVersion> serverSupports) {
		this.onion = onion;
		this.authToken = authToken;
		this.owner = true;
		this.serverSupports = serverSupports;
		this.inboxId = null;
		this.outboxId = null;
	}

	/**
	 * Constructor for properties used by a contact of the mailbox's owner.
	 */
	public MailboxProperties(String onion, MailboxAuthToken authToken,
			List<MailboxVersion> serverSupports, MailboxFolderId inboxId,
			MailboxFolderId outboxId) {
		this.onion = onion;
		this.authToken = authToken;
		this.owner = false;
		this.serverSupports = serverSupports;
		this.inboxId = inboxId;
		this.outboxId = outboxId;
	}

	/**
	 * Returns the onion address of the mailbox, excluding the .onion suffix.
	 */
	public String getOnion() {
		return onion;
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

	@Override
	public boolean equals(Object o) {
		if (o instanceof MailboxProperties) {
			MailboxProperties m = (MailboxProperties) o;
			return owner == m.owner &&
					onion.equals(m.onion) &&
					authToken.equals(m.authToken) &&
					NullSafety.equals(inboxId, m.inboxId) &&
					NullSafety.equals(outboxId, m.outboxId) &&
					serverSupports.equals(m.serverSupports);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return authToken.hashCode();
	}
}
