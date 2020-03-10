package org.briarproject.bramble.api.plugin;

public interface LanTcpConstants {

	TransportId ID = new TransportId("org.briarproject.bramble.lan");

	// Transport properties (shared with contacts)
	String PROP_IP_PORTS = "ipPorts";
	String PROP_PORT = "port";
	String PROP_IPV6 = "ipv6";

	// Local settings (not shared with contacts)
	String PREF_LAN_IP_PORTS = "ipPorts";
	String PREF_IPV6 = "ipv6";
}
