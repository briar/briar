package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class ClientMajorVersion implements Comparable<ClientMajorVersion> {

	final ClientId clientId;
	final int majorVersion;

	ClientMajorVersion(ClientId clientId, int majorVersion) {
		this.clientId = clientId;
		this.majorVersion = majorVersion;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClientMajorVersion) {
			ClientMajorVersion cv = (ClientMajorVersion) o;
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
	public int compareTo(ClientMajorVersion cv) {
		int compare = clientId.compareTo(cv.clientId);
		if (compare != 0) return compare;
		return majorVersion - cv.majorVersion;
	}
}

