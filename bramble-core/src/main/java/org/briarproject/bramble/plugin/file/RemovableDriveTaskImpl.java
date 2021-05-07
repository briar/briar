package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.concurrent.ThreadSafe;

import static java.lang.Math.min;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.api.plugin.file.FileConstants.PROP_PATH;
import static org.briarproject.bramble.api.plugin.file.RemovableDriveConstants.ID;

@ThreadSafe
@NotNullByDefault
abstract class RemovableDriveTaskImpl implements RemovableDriveTask {

	private final Executor eventExecutor;
	private final PluginManager pluginManager;
	final ConnectionManager connectionManager;
	final EventBus eventBus;
	final RemovableDriveTaskRegistry registry;
	final ContactId contactId;
	final File file;
	private final List<Observer> observers = new CopyOnWriteArrayList<>();
	final AtomicLong progressTotal = new AtomicLong(0);
	private final AtomicLong progressDone = new AtomicLong(0);

	RemovableDriveTaskImpl(
			Executor eventExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus,
			RemovableDriveTaskRegistry registry,
			ContactId contactId,
			File file) {
		this.eventExecutor = eventExecutor;
		this.pluginManager = pluginManager;
		this.connectionManager = connectionManager;
		this.eventBus = eventBus;
		this.registry = registry;
		this.contactId = contactId;
		this.file = file;
	}

	@Override
	public File getFile() {
		return file;
	}

	@Override
	public void addObserver(Observer o) {
		observers.add(o);
	}

	@Override
	public void removeObserver(Observer o) {
		observers.remove(o);
	}

	void visitObservers(Consumer<Observer> visitor) {
		eventExecutor.execute(() -> {
			for (Observer o : observers) visitor.accept(o);
		});
	}

	SimplexPlugin getPlugin() {
		return (SimplexPlugin) requireNonNull(pluginManager.getPlugin(ID));
	}

	TransportProperties createProperties() {
		TransportProperties p = new TransportProperties();
		p.put(PROP_PATH, file.getAbsolutePath());
		return p;
	}

	void updateProgress(long progress) {
		long done = progressDone.addAndGet(progress);
		long total = progressTotal.get();
		visitObservers(o -> o.onProgress(min(done, total), total));
	}
}
