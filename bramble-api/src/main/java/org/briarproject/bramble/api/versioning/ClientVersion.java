package org.briarproject.bramble.api.versioning;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class ClientVersion implements Comparable<ClientVersion> {

	private final ClientMajorVersion majorVersion;
	private final int minorVersion;

	public ClientVersion(ClientMajorVersion majorVersion,
			int minorVersion) {
		this.majorVersion = majorVersion;
		this.minorVersion = minorVersion;
	}

	public ClientVersion(ClientId clientId, int majorVersion,
			int minorVersion) {
		this(new ClientMajorVersion(clientId, majorVersion), minorVersion);
	}

	public ClientMajorVersion getClientMajorVersion() {
		return majorVersion;
	}

	public ClientId getClientId() {
		return majorVersion.getClientId();
	}

	public int getMajorVersion() {
		return majorVersion.getMajorVersion();
	}

	public int getMinorVersion() {
		return minorVersion;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ClientVersion) {
			ClientVersion cv = (ClientVersion) o;
			return majorVersion.equals(cv.majorVersion)
					&& minorVersion == cv.minorVersion;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return majorVersion.hashCode();
	}

	@Override
	public int compareTo(ClientVersion cv) {
		int compare = majorVersion.compareTo(cv.majorVersion);
		if (compare != 0) return compare;
		return minorVersion - cv.minorVersion;
	}
}
