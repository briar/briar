package net.sf.briar.api.transport;

import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;

public interface TransportCallback {

	void setLocalProperties(TransportProperties p);

	void setConfig(TransportConfig c);

	void showMessage(String... message);

	boolean showConfirmationMessage(String... message);

	int showChoice(String[] choices, String... message);
}
