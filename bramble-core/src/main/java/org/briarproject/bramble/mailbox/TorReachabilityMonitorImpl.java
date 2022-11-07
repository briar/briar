package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.Cancellable;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.TorConstants.ID;

@ThreadSafe
@NotNullByDefault
class TorReachabilityMonitorImpl
		implements TorReachabilityMonitor, EventListener {

	private final Executor ioExecutor;
	private final TaskScheduler taskScheduler;
	private final MailboxConfig mailboxConfig;
	private final PluginManager pluginManager;
	private final EventBus eventBus;
	private final Object lock = new Object();

	@GuardedBy("lock")
	private boolean reachable = false, destroyed = false;

	@GuardedBy("lock")
	private final List<TorReachabilityObserver> observers = new ArrayList<>();

	@GuardedBy("lock")
	@Nullable
	private Cancellable task = null;

	@Inject
	TorReachabilityMonitorImpl(
			@IoExecutor Executor ioExecutor,
			TaskScheduler taskScheduler,
			MailboxConfig mailboxConfig,
			PluginManager pluginManager,
			EventBus eventBus) {
		this.ioExecutor = ioExecutor;
		this.taskScheduler = taskScheduler;
		this.mailboxConfig = mailboxConfig;
		this.pluginManager = pluginManager;
		this.eventBus = eventBus;
	}

	@Override
	public void start() {
		eventBus.addListener(this);
		Plugin plugin = pluginManager.getPlugin(ID);
		if (plugin != null && plugin.getState() == ACTIVE) onTorActive();
	}

	@Override
	public void destroy() {
		eventBus.removeListener(this);
		synchronized (lock) {
			destroyed = true;
			if (task != null) task.cancel();
			task = null;
			observers.clear();
		}
	}

	@Override
	public void addOneShotObserver(TorReachabilityObserver o) {
		boolean callNow = false;
		synchronized (lock) {
			if (destroyed) return;
			if (reachable) callNow = true;
			else observers.add(o);
		}
		if (callNow) o.onTorReachable();
	}

	@Override
	public void removeObserver(TorReachabilityObserver o) {
		synchronized (lock) {
			if (destroyed) return;
			observers.remove(o);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportActiveEvent) {
			TransportActiveEvent t = (TransportActiveEvent) e;
			if (t.getTransportId().equals(ID)) onTorActive();
		} else if (e instanceof TransportInactiveEvent) {
			TransportInactiveEvent t = (TransportInactiveEvent) e;
			if (t.getTransportId().equals(ID)) onTorInactive();
		}
	}

	private void onTorActive() {
		synchronized (lock) {
			if (destroyed || task != null) return;
			task = taskScheduler.schedule(this::onTorReachable, ioExecutor,
					mailboxConfig.getTorReachabilityPeriod(), MILLISECONDS);
		}
	}

	private void onTorInactive() {
		synchronized (lock) {
			reachable = false;
			if (task != null) task.cancel();
			task = null;
		}
	}

	@IoExecutor
	private void onTorReachable() {
		List<TorReachabilityObserver> observers;
		synchronized (lock) {
			if (destroyed) return;
			reachable = true;
			observers = new ArrayList<>(this.observers);
			this.observers.clear();
			task = null;
		}
		for (TorReachabilityObserver o : observers) o.onTorReachable();
	}
}
