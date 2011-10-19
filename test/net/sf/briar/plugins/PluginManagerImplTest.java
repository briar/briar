package net.sf.briar.plugins;

import java.util.concurrent.Executor;

import junit.framework.TestCase;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.db.DatabaseComponent;
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
		context.checking(new Expectations() {{
			allowing(db).getLocalProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
			allowing(db).getRemoteProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
		}});
		Executor executor = new ImmediateExecutor();
		Poller poller = new PollerImpl();
		PluginManagerImpl p =
			new PluginManagerImpl(executor, db, poller, dispatcher, uiCallback);
		// The Bluetooth plugin will not start without a Bluetooth device, so
		// we expect two plugins to be started
		assertEquals(2, p.startPlugins());
		assertEquals(2, p.stopPlugins());
	}
}
