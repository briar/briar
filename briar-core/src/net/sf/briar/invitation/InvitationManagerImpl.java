package net.sf.briar.invitation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.invitation.InvitationManager;
import net.sf.briar.api.invitation.InvitationTask;
import net.sf.briar.api.plugins.PluginManager;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.WriterFactory;

import com.google.inject.Inject;

class InvitationManagerImpl implements InvitationManager {

	private final CryptoComponent crypto;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Clock clock;
	private final PluginManager pluginManager;

	private final AtomicInteger nextHandle;
	private final Map<Integer, InvitationTask> tasks;

	@Inject
	InvitationManagerImpl(CryptoComponent crypto, ReaderFactory readerFactory,
			WriterFactory writerFactory, Clock clock,
			PluginManager pluginManager) {
		this.crypto = crypto;
		this.readerFactory = readerFactory;
		this.writerFactory = writerFactory;
		this.clock = clock;
		this.pluginManager = pluginManager;
		nextHandle = new AtomicInteger(0);
		tasks = new ConcurrentHashMap<Integer, InvitationTask>();
	}

	public InvitationTask createTask(int localCode, int remoteCode) {
		int handle = nextHandle.incrementAndGet();
		return new ConnectorGroup(this, crypto, readerFactory, writerFactory,
				clock, pluginManager, handle, localCode, remoteCode);
	}

	public InvitationTask getTask(int handle) {
		return tasks.get(handle);
	}

	public void putTask(int handle, InvitationTask task) {
		tasks.put(handle, task);
	}

	public void removeTask(int handle) {
		tasks.remove(handle);
	}
}
