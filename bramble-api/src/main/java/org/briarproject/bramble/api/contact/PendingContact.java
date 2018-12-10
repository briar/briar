package org.briarproject.bramble.api.contact;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PendingContact {

	public enum PendingContactState {
		WAITING_FOR_CONNECTION,
		CONNECTED,
		ADDING_CONTACT,
		FAILED
	}

	private final PendingContactId id;
	private final String alias;
	private final PendingContactState state;
	private final long timestamp;

	public PendingContact(PendingContactId id, String alias,
			PendingContactState state, long timestamp) {
		this.id = id;
		this.alias = alias;
		this.state = state;
		this.timestamp = timestamp;
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
