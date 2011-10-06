package net.sf.briar.plugins;

import java.util.Map;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.stream.StreamTransportCallback;
import net.sf.briar.api.transport.stream.StreamTransportConnection;

public class StubStreamCallback implements StreamTransportCallback {

	public Map<String, String> localProperties = null;
	public volatile int incomingConnections = 0;

	public void setLocalProperties(Map<String, String> properties) {
		localProperties = properties;
	}

	public void setConfig(Map<String, String> config) {
	}

	public void showMessage(String... message) {
	}

	public boolean showConfirmationMessage(String... message) {
		return false;
	}

	public int showChoice(String[] choices, String... message) {
		return -1;
	}

	public void incomingConnectionCreated(StreamTransportConnection c) {
		incomingConnections++;
	}

	public void outgoingConnectionCreated(ContactId contactId,
			StreamTransportConnection c) {
	}
}
