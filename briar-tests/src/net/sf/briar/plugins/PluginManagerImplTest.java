package net.sf.briar.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.android.AndroidExecutor;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.messaging.TransportId;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;
import net.sf.briar.plugins.file.RemovableDrivePluginFactory;
import net.sf.briar.plugins.tcp.LanTcpPluginFactory;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PluginManagerImplTest extends BriarTestCase {

	@SuppressWarnings("unchecked")
	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final ExecutorService pluginExecutor = Executors.newCachedThreadPool();
		final AndroidExecutor androidExecutor =
				context.mock(AndroidExecutor.class);
		final SimplexPluginConfig simplexPluginConfig =
				context.mock(SimplexPluginConfig.class);
		final DuplexPluginConfig duplexPluginConfig =
				context.mock(DuplexPluginConfig.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionDispatcher dispatcher =
				context.mock(ConnectionDispatcher.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		// One simplex plugin
		final SimplexPluginFactory removableDrive =
				new RemovableDrivePluginFactory(pluginExecutor);
		// One duplex plugin
		final DuplexPluginFactory lanTcp =
				new LanTcpPluginFactory(pluginExecutor);
		context.checking(new Expectations() {{
			// Start
			oneOf(simplexPluginConfig).getFactories();
			will(returnValue(Arrays.asList(removableDrive)));
			oneOf(duplexPluginConfig).getFactories();
			will(returnValue(Arrays.asList(lanTcp)));
			exactly(2).of(db).addTransport(with(any(TransportId.class)));
			will(returnValue(true));
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
		PluginManagerImpl p = new PluginManagerImpl(pluginExecutor,
				androidExecutor, simplexPluginConfig, duplexPluginConfig, db,
				poller, dispatcher, uiCallback);
		// Two plugins should be started and stopped
		assertEquals(2, p.start(null));
		assertEquals(2, p.stop());
		context.assertIsSatisfied();
	}
}
