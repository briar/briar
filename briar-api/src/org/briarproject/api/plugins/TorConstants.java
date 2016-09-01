package org.briarproject.api.plugins;

import org.briarproject.api.TransportId;

public interface TorConstants {

	TransportId ID = new TransportId("tor");

	int SOCKS_PORT   = 59050;
	int CONTROL_PORT = 59051;

}
