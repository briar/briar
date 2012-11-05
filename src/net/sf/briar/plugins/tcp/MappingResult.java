package net.sf.briar.plugins.tcp;

import java.net.InetAddress;
import java.net.InetSocketAddress;

class MappingResult {

	private final InetAddress internal, external;
	private final int port;
	private final boolean succeeded;

	MappingResult(InetAddress internal, InetAddress external, int port,
			boolean succeeded) {
		this.internal = internal;
		this.external = external;
		this.port = port;
		this.succeeded = succeeded;
	}

	InetSocketAddress getInternal() {
		return isUsable() ? new InetSocketAddress(internal, port) : null;
	}

	InetSocketAddress getExternal() {
		return isUsable() ? new InetSocketAddress(external, port) : null;
	}

	boolean isUsable() {
		return internal != null && external != null && port != 0 && succeeded;
	}
}
