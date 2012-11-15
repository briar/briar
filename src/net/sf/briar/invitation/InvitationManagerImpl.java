package net.sf.briar.invitation;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.invitation.InvitationManager;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class InvitationManagerImpl implements InvitationManager {

	private final CryptoComponent crypto;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final PluginManager pluginManager;

	private final AtomicInteger nextHandle;
	private final Map<Integer, InvitationTask> tasks;

	@Inject
	InvitationManagerImpl(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, PluginManager pluginManager) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.pluginManager = pluginManager;
		nextHandle = new AtomicInteger(0);
		tasks = new ConcurrentHashMap<Integer, InvitationTask>();
	}

	public InvitationTask createTask(int localCode, int remoteCode) {
		Collection<DuplexPlugin> plugins = pluginManager.getInvitationPlugins();
		int handle = nextHandle.incrementAndGet();
		ConnectorGroup group =
				new ConnectorGroup(this, handle, localCode, remoteCode);
		// Alice is the peer with the lesser invitation code
		if(localCode < remoteCode) {
			for(DuplexPlugin plugin : plugins) {
				group.addConnector(new AliceConnector(crypto, readerFactory,
						writerFactory, group, plugin, localCode, remoteCode));
			}
		} else {
			for(DuplexPlugin plugin : plugins) {
				group.addConnector(new BobConnector(crypto, readerFactory,
						writerFactory, group, plugin, localCode, remoteCode));
			}
		}
		tasks.put(handle, group);
		return group;
	}

	public InvitationTask getTask(int handle) {
		return tasks.get(handle);
	}

	public void removeTask(int handle) {
		tasks.remove(handle);
	}
}
