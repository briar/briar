package org.briarproject.android.event;


import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppBusImp implements AppBus {
	private final Collection<EventListener> listeners =
			new CopyOnWriteArrayList<EventListener>();

	public void addListener(EventListener l) {
		listeners.add(l);
	}

	public void removeListener(EventListener l) {
		listeners.remove(l);
	}

	public void broadcast(Event e) {
		for (EventListener l : listeners) l.eventOccurred(e);
	}
}
