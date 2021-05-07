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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.GuardedBy;
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

	private final Object lock = new Object();
	@GuardedBy("lock")
	private final List<Consumer<State>> observers = new ArrayList<>();
	@GuardedBy("lock")
	private State state = new State(0, 0, false, false);

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
	public void addObserver(Consumer<State> o) {
		State state;
		synchronized (lock) {
			observers.add(o);
			state = this.state;
		}
		if (state.isFinished()) {
			eventExecutor.execute(() -> o.accept(state));
		}
	}

	@Override
	public void removeObserver(Consumer<State> o) {
		synchronized (lock) {
			observers.remove(o);
		}
	}

	SimplexPlugin getPlugin() {
		return (SimplexPlugin) requireNonNull(pluginManager.getPlugin(ID));
	}

	TransportProperties createProperties() {
		TransportProperties p = new TransportProperties();
		p.put(PROP_PATH, file.getAbsolutePath());
		return p;
	}

	void setTotal(long total) {
		synchronized (lock) {
			state = new State(state.getDone(), total, state.isFinished(),
					state.isSuccess());
			notifyObservers();
		}
	}

	void addDone(long done) {
		synchronized (lock) {
			// Done and total come from different sources; make them consistent
			done = min(state.getDone() + done, state.getTotal());
			state = new State(done, state.getTotal(), state.isFinished(),
					state.isSuccess());
		}
		notifyObservers();
	}

	void setSuccess(boolean success) {
		synchronized (lock) {
			state = new State(state.getDone(), state.getTotal(), true, success);
		}
		notifyObservers();
	}

	@GuardedBy("lock")
	private void notifyObservers() {
		List<Consumer<State>> observers = new ArrayList<>(this.observers);
		State state = this.state;
		eventExecutor.execute(() -> {
			for (Consumer<State> o : observers) o.accept(state);
		});
	}
}
