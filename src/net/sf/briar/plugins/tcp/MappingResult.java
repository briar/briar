package net.sf.briar.plugins.tcp;

import java.net.InetAddress;

class MappingResult {

	private final InetAddress internal, external;
	private final boolean succeeded;

	MappingResult(InetAddress internal, InetAddress external,
			boolean succeeded) {
		this.internal = internal;
		this.external = external;
		this.succeeded = succeeded;
	}

	InetAddress getInternal() {
		return internal;
	}

	InetAddress getExternal() {
		return external;
	}

	boolean getSucceeded() {
		return succeeded;
	}

	boolean isUsable() {
		return internal != null && external != null && succeeded;
	}
}
