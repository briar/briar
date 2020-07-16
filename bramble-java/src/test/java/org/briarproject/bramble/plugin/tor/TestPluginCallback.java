package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.TransportConnectionReader;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.settings.Settings;

import java.util.Collection;

import static java.util.Collections.emptyList;

@NotNullByDefault
public class TestPluginCallback implements PluginCallback {

	@Override
	public Settings getSettings() {
		return new Settings();
	}

	@Override
	public TransportProperties getLocalProperties() {
		return new TransportProperties();
	}

	@Override
	public Collection<TransportProperties> getRemoteProperties() {
		return emptyList();
	}

	@Override
	public void mergeSettings(Settings s) {
	}

	@Override
	public void mergeLocalProperties(TransportProperties p) {
	}

	@Override
	public void pluginStateChanged(State state) {
	}

	@Override
	public void handleConnection(DuplexTransportConnection c) {
	}

	@Override
	public void handleReader(TransportConnectionReader r) {
	}

	@Override
	public void handleWriter(TransportConnectionWriter w) {
	}
}
