package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.plugin.bluetooth.JavaBluetoothPluginFactory;
import org.briarproject.bramble.plugin.modem.ModemPluginFactory;
import org.briarproject.bramble.plugin.tcp.LanTcpPluginFactory;
import org.briarproject.bramble.plugin.tcp.WanTcpPluginFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import dagger.Module;
import dagger.Provides;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

@Module
public class DesktopPluginModule extends PluginModule {

	@Provides
	PluginConfig getPluginConfig(JavaBluetoothPluginFactory bluetooth,
			ModemPluginFactory modem, LanTcpPluginFactory lan,
			WanTcpPluginFactory wan) {
		@NotNullByDefault
		PluginConfig pluginConfig = new PluginConfig() {

			@Override
			public Collection<DuplexPluginFactory> getDuplexFactories() {
				return asList(bluetooth, modem, lan, wan);
			}

			@Override
			public Collection<SimplexPluginFactory> getSimplexFactories() {
				return emptyList();
			}

			@Override
			public boolean shouldPoll() {
				return true;
			}

			@Override
			public Map<TransportId, List<TransportId>> getTransportPreferences() {
				// Prefer LAN to Bluetooth
				return singletonMap(BluetoothConstants.ID,
						singletonList(LanTcpConstants.ID));
			}
		};
		return pluginConfig;
	}
}
