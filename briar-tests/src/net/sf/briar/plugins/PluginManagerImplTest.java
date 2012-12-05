package net.sf.briar.plugins;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.lifecycle.ShutdownManager;
import net.sf.briar.api.protocol.TransportId;
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
		final AndroidExecutor androidExecutor =
				context.mock(AndroidExecutor.class);
		final ShutdownManager shutdownManager =
				context.mock(ShutdownManager.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionDispatcher dispatcher =
				context.mock(ConnectionDispatcher.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		context.checking(new Expectations() {{
			// Start
			oneOf(poller).start(with(any(Collection.class)));
			allowing(db).getConfig(with(any(TransportId.class)));
			will(returnValue(new TransportConfig()));
			allowing(db).getLocalProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
			allowing(db).getRemoteProperties(with(any(TransportId.class)));
			will(returnValue(new TransportProperties()));
			allowing(db).mergeLocalProperties(with(any(TransportId.class)),
					with(any(TransportProperties.class)));
			// Stop
			oneOf(poller).stop();
			oneOf(androidExecutor).shutdown();
		}});
		ExecutorService executor = Executors.newCachedThreadPool();
		PluginManagerImpl p = new PluginManagerImpl(executor, androidExecutor,
				shutdownManager, db, poller, dispatcher, uiCallback);
		// We expect either 3 or 4 plugins to be started, depending on whether
		// the test machine has a Bluetooth device
		int started = p.start(null);
		int stopped = p.stop();
		assertEquals(started, stopped);
		assertTrue(started >= 3);
		assertTrue(started <= 4);
		context.assertIsSatisfied();
	}
}
