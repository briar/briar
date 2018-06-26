package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;

@NotNullByDefault
public class TorPluginCallBack implements DuplexPluginCallback {

	@Override
	public void incomingConnectionCreated(DuplexTransportConnection d) {

	}

	@Override
	public void outgoingConnectionCreated(ContactId c,
			DuplexTransportConnection d) {

	}

	@Override
	public Settings getSettings() {
		return new Settings();
	}

	@Override
	public TransportProperties getLocalProperties() {
		return new TransportProperties();
	}

	@Override
	public void mergeSettings(Settings s) {

	}

	@Override
	public void mergeLocalProperties(TransportProperties p) {

	}

	@Override
	public void transportEnabled() {

	}

	@Override
	public void transportDisabled() {

	}

}
