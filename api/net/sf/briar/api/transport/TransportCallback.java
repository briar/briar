package net.sf.briar.api.transport;

import java.util.Map;

public interface TransportCallback {

	void setLocalProperties(Map<String, String> properties);

	void setConfig(Map<String, String> config);

	void showMessage(String... message);

	boolean showConfirmationMessage(String... message);

	int showChoice(String[] choices, String... message);
}
