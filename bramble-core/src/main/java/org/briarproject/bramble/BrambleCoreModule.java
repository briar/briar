package org.briarproject.bramble;

import org.briarproject.bramble.cleanup.CleanupModule;
import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.connection.ConnectionModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseExecutorModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.io.IoModule;
import org.briarproject.bramble.keyagreement.KeyAgreementModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.properties.PropertiesModule;
import org.briarproject.bramble.record.RecordModule;
import org.briarproject.bramble.reliability.ReliabilityModule;
import org.briarproject.bramble.rendezvous.RendezvousModule;
import org.briarproject.bramble.settings.SettingsModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.sync.validation.ValidationModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;

import dagger.Module;

@Module(includes = {
		CleanupModule.class,
		ClientModule.class,
		ConnectionModule.class,
		ContactModule.class,
		CryptoModule.class,
		CryptoExecutorModule.class,
		DataModule.class,
		DatabaseModule.class,
		DatabaseExecutorModule.class,
		EventModule.class,
		IdentityModule.class,
		IoModule.class,
		KeyAgreementModule.class,
		LifecycleModule.class,
		PluginModule.class,
		PropertiesModule.class,
		RecordModule.class,
		ReliabilityModule.class,
		RendezvousModule.class,
		SettingsModule.class,
		SyncModule.class,
		TransportModule.class,
		ValidationModule.class,
		VersioningModule.class
})
public class BrambleCoreModule {
}
