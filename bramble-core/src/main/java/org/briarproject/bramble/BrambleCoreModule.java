package org.briarproject.bramble;

import org.briarproject.bramble.client.ClientModule;
import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoModule;
import org.briarproject.bramble.data.DataModule;
import org.briarproject.bramble.db.DatabaseExecutorModule;
import org.briarproject.bramble.db.DatabaseModule;
import org.briarproject.bramble.event.EventModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.invitation.InvitationModule;
import org.briarproject.bramble.keyagreement.KeyAgreementModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.properties.PropertiesModule;
import org.briarproject.bramble.reliability.ReliabilityModule;
import org.briarproject.bramble.reporting.ReportingModule;
import org.briarproject.bramble.settings.SettingsModule;
import org.briarproject.bramble.socks.SocksModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.transport.TransportModule;

import dagger.Module;

@Module(includes = {
		ClientModule.class,
		ContactModule.class,
		CryptoModule.class,
		DataModule.class,
		DatabaseModule.class,
		DatabaseExecutorModule.class,
		EventModule.class,
		IdentityModule.class,
		InvitationModule.class,
		KeyAgreementModule.class,
		LifecycleModule.class,
		PluginModule.class,
		PropertiesModule.class,
		ReliabilityModule.class,
		ReportingModule.class,
		SettingsModule.class,
		SocksModule.class,
		SyncModule.class,
		SystemModule.class,
		TransportModule.class
})
public class BrambleCoreModule {

	public static void initEagerSingletons(BrambleCoreEagerSingletons c) {
		c.inject(new ContactModule.EagerSingletons());
		c.inject(new CryptoModule.EagerSingletons());
		c.inject(new DatabaseExecutorModule.EagerSingletons());
		c.inject(new IdentityModule.EagerSingletons());
		c.inject(new LifecycleModule.EagerSingletons());
		c.inject(new PluginModule.EagerSingletons());
		c.inject(new SyncModule.EagerSingletons());
		c.inject(new SystemModule.EagerSingletons());
		c.inject(new TransportModule.EagerSingletons());
	}
}
