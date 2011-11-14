package net.sf.briar.plugins;

import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PluginManagerImplTest extends TestCase {

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final ConnectionDispatcher dispatcher =
			context.mock(ConnectionDispatcher.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		final AtomicInteger index = new AtomicInteger(0);
		context.checking(new Expectations() {{
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
		}});
		Poller poller = new PollerImpl();
		PluginManagerImpl p = new PluginManagerImpl(db, poller, dispatcher,
				uiCallback);
		// The Bluetooth plugin will not start without a Bluetooth device, so
		// we expect two plugins to be started
		assertEquals(2, p.startPlugins());
		assertEquals(2, p.stopPlugins());
	}
}
