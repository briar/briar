package org.briarproject.briar.test;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import static org.briarproject.briar.test.BriarIntegrationTest.waitForEvents;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@NotNullByDefault
public class TestEventListener<T extends Event> implements EventListener {

	@FunctionalInterface
	public interface EventRunnable {
		void run() throws Exception;
	}

	public static <T extends Event> T assertEvent(
			BriarIntegrationTestComponent c, Class<T> clazz, EventRunnable r)
			throws Exception {
		TestEventListener<T> listener = new TestEventListener<>(clazz);
		c.getEventBus().addListener(listener);
		try {
			r.run();
			waitForEvents(c);
			return listener.assertAndGetEvent();
		} finally {
			c.getEventBus().removeListener(listener);
		}
	}

	private TestEventListener(Class<T> clazz) {
		this.clazz = clazz;
	}

	private final Class<T> clazz;

	@Nullable
	private T event;

	@Override
	public void eventOccurred(Event e) {
		if (e.getClass().equals(clazz)) {
			assertNull("Event already received", event);
			//noinspection unchecked
			event = (T) e;
		}
	}

	private T assertAndGetEvent() {
		assertNotNull("No event received", event);
		return event;
	}

}
