package org.briarproject.bramble.api.plugin;

public interface LanTcpConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.lan");

	// a transport property (shared with contacts)
	String PROP_IP_PORTS = "ipPorts";

	// a local setting
	String PREF_LAN_IP_PORTS = "ipPorts";

}
