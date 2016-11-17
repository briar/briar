package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;

public interface TorConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.tor");

	int SOCKS_PORT   = 59050;
	int CONTROL_PORT = 59051;

	int CONNECT_TO_PROXY_TIMEOUT = 5000; // Milliseconds
}
