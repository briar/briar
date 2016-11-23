package org.briarproject.bramble.plugin.tcp;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
class MappingResult {

	private final InetAddress internal;
	@Nullable
	private final InetAddress external;
	private final int port;
	private final boolean succeeded;

	MappingResult(InetAddress internal, @Nullable InetAddress external,
			int port, boolean succeeded) {
		this.internal = internal;
		this.external = external;
		this.port = port;
		this.succeeded = succeeded;
	}

	@Nullable
	InetSocketAddress getInternal() {
		return isUsable() ? new InetSocketAddress(internal, port) : null;
	}

	@Nullable
	InetSocketAddress getExternal() {
		return isUsable() ? new InetSocketAddress(external, port) : null;
	}

	boolean isUsable() {
		return external != null && port != 0 && succeeded;
	}
}
