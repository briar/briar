package net.sf.briar.plugins;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PluginManagerImplTest extends BriarTestCase {

	@SuppressWarnings("unchecked")
	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionDispatcher dispatcher =
			context.mock(ConnectionDispatcher.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		final AtomicInteger index = new AtomicInteger(0);
		context.checking(new Expectations() {{
			oneOf(poller).startPolling(with(any(Collection.class)));
			allowing(db).getLocalIndex(with(any(TransportId.class)));
			will(returnValue(null));
			allowing(db).addTransport(with(any(TransportId.class)));
			will(returnValue(new TransportIndex(index.getAndIncrement())));
			allowing(db).getLocalProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
			allowing(db).getRemoteProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
			allowing(db).setLocalProperties(with(any(TransportId.class)),
					with(any(TransportProperties.class)));
			oneOf(poller).stopPolling();
		}});
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
		PluginManagerImpl p = new PluginManagerImpl(executor, db, poller,
				dispatcher, uiCallback);
		// We expect either 2 or 3 plugins to be started, depending on whether
		// the test machine has a Bluetooth device
		int started = p.startPlugins();
		int stopped = p.stopPlugins();
		assertEquals(started, stopped);
		assertTrue(started >= 2);
		assertTrue(started <= 3);
	}
}
