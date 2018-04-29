package org.briarproject.bramble.api.versioning;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ClientMajorVersion implements Comparable<ClientMajorVersion> {

	private final ClientId clientId;
	private final int majorVersion;

	public ClientMajorVersion(ClientId clientId, int majorVersion) {
		this.clientId = clientId;
		this.majorVersion = majorVersion;
	}

	public ClientId getClientId() {
		return clientId;
	}

	public int getMajorVersion() {
		return majorVersion;
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

