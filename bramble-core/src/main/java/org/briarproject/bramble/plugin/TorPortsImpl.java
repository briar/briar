package org.briarproject.bramble.plugin;

public class TorPortsImpl implements TorPorts {

	private int socksPort;
	private int controlPort;

	public TorPortsImpl(int socksPort, int controlPort) {
		this.socksPort = socksPort;
		this.controlPort = controlPort;
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
