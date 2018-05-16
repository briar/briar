package org.briarproject.bramble;

import org.briarproject.bramble.contact.ContactModule;
import org.briarproject.bramble.crypto.CryptoExecutorModule;
import org.briarproject.bramble.db.DatabaseExecutorModule;
import org.briarproject.bramble.identity.IdentityModule;
import org.briarproject.bramble.lifecycle.LifecycleModule;
import org.briarproject.bramble.plugin.PluginModule;
import org.briarproject.bramble.properties.PropertiesModule;
import org.briarproject.bramble.reporting.ReportingModule;
import org.briarproject.bramble.sync.SyncModule;
import org.briarproject.bramble.system.SystemModule;
import org.briarproject.bramble.transport.TransportModule;
import org.briarproject.bramble.versioning.VersioningModule;

public interface BrambleCoreEagerSingletons {

	void inject(ContactModule.EagerSingletons init);

	void inject(CryptoExecutorModule.EagerSingletons init);

	void inject(DatabaseExecutorModule.EagerSingletons init);

	void inject(IdentityModule.EagerSingletons init);

	void inject(LifecycleModule.EagerSingletons init);

	void inject(PluginModule.EagerSingletons init);

	void inject(PropertiesModule.EagerSingletons init);

	void inject(ReportingModule.EagerSingletons init);

	void inject(SyncModule.EagerSingletons init);

	void inject(SystemModule.EagerSingletons init);

	void inject(TransportModule.EagerSingletons init);

	void inject(VersioningModule.EagerSingletons init);
}
