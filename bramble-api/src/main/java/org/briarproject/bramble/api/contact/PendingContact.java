package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PendingContact {

	private final PendingContactId id;
	private final PublicKey publicKey;
	private final String alias;
	private final PendingContactState state;
	private final long timestamp;

	public PendingContact(PendingContactId id, PublicKey publicKey,
			String alias, PendingContactState state, long timestamp) {
		this.id = id;
		this.publicKey = publicKey;
		this.alias = alias;
		this.state = state;
		this.timestamp = timestamp;
	}

	public PendingContactId getId() {
		return id;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public String getAlias() {
		return alias;
	}

	public PendingContactState getState() {
		return state;
	}

	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof PendingContact &&
				id.equals(((PendingContact) o).id);
	}
}
