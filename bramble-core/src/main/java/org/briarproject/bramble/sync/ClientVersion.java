package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class ClientVersion implements Comparable<ClientVersion> {

	final ClientId clientId;
	final int majorVersion;

	ClientVersion(ClientId clientId, int majorVersion) {
		this.clientId = clientId;
		this.majorVersion = majorVersion;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClientVersion) {
			ClientVersion cv = (ClientVersion) o;
			return clientId.equals(cv.clientId)
					&& majorVersion == cv.majorVersion;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (clientId.hashCode() << 16) + majorVersion;
	}

	@Override
	public int compareTo(ClientVersion c) {
		int compare = clientId.compareTo(c.clientId);
		if (compare != 0) return compare;
		return majorVersion - c.majorVersion;
	}

	@Override
	public String toString() {
		return clientId.getString() + ":" + majorVersion;
	}
}

