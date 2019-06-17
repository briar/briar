package org.briarproject.bramble.api.sync;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.List;

import javax.annotation.concurrent.Immutable;

/**
 * A record telling the recipient which versions of the sync protocol the
 * sender supports.
 */
@Immutable
@NotNullByDefault
public class Versions {

	private final List<Byte> supported;

	public Versions(List<Byte> supported) {
		this.supported = supported;
	}

	public List<Byte> getSupportedVersions() {
		return supported;
	}
}
