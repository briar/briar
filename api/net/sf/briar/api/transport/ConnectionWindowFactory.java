package net.sf.briar.api.transport;

public interface ConnectionWindowFactory {

	ConnectionWindow createConnectionWindow(long centre, int bitmap);
}
