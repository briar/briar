package net.sf.briar.api.transport;

import java.util.Map;

public interface TransportCallback {

	void setLocalTransports(Map<String, String> transports);

	void setConfig(Map<String, String> config);

	void showMessage(String message);

	boolean showConfirmationMessage(String message);

	int showChoice(String message, String[] choices);
}
