package org.briarproject.bramble.api.plugin;

public interface LanTcpConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.lan");

	// Transport properties (shared with contacts)
	String PROP_IP_PORTS = "ipPorts";
	String PROP_PORT = "port";
	String PROP_SLAAC = "slaac";

	// A local setting
	String PREF_LAN_IP_PORTS = "ipPorts";
}
