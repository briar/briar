package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class ClientVersion implements Comparable<ClientVersion> {

	final ClientId clientId;
	final int clientVersion;

	ClientVersion(ClientId clientId, int clientVersion) {
		this.clientId = clientId;
		this.clientVersion = clientVersion;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClientVersion) {
			ClientVersion cv = (ClientVersion) o;
			return clientId.equals(cv.clientId)
					&& clientVersion == cv.clientVersion;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (clientId.hashCode() << 16) + clientVersion;
	}

	@Override
	public int compareTo(ClientVersion c) {
		int compare = clientId.compareTo(c.clientId);
		if (compare != 0) return compare;
		return clientVersion - c.clientVersion;
	}
}

