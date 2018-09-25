package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PendingRequestsItem {

	private final String name;
	private final long timestamp;

	public PendingRequestsItem(String name, long timestamp) {
		this.name = name;
		this.timestamp = timestamp;
	}

	public String getName() {
		return name;
	}

	public long getTimestamp() {
		return timestamp;
	}

}
