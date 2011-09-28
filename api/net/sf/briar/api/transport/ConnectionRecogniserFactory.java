package net.sf.briar.api.transport;

public interface ConnectionRecogniserFactory {

	ConnectionRecogniser createConnectionRecogniser(int transportId);
}
