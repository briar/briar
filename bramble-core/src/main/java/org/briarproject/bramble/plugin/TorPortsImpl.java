package org.briarproject.bramble.plugin;

public class TorPortsImpl implements TorPorts {

	private static int currentPort = 59050;

	private static int nextPort() {
		return currentPort++;
	}

	private int socksPort;
	private int controlPort;

	public TorPortsImpl() {
		socksPort = nextPort();
		controlPort = nextPort();
	}

	@Override
	public int getSocksPort() {
		return socksPort;
	}

	@Override
	public int getControlPort() {
		return controlPort;
	}
}
