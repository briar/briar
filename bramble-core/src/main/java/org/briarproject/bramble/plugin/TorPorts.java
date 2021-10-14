package org.briarproject.bramble.plugin;

/**
 * Interface used for injecting the tor ports.
 */
public interface TorPorts {

	int getSocksPort();

	int getControlPort();

}
