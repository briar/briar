package net.sf.briar.plugins;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.plugins.duplex.DuplexPlugin;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexPluginConfig;
import net.sf.briar.api.plugins.duplex.DuplexPluginFactory;
import net.sf.briar.api.plugins.simplex.SimplexPlugin;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexPluginConfig;
import net.sf.briar.api.plugins.simplex.SimplexPluginFactory;
import net.sf.briar.api.transport.ConnectionDispatcher;
import net.sf.briar.api.ui.UiCallback;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class PluginManagerImplTest extends BriarTestCase {

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery();
		final Executor pluginExecutor = Executors.newCachedThreadPool();
		final SimplexPluginConfig simplexPluginConfig =
				context.mock(SimplexPluginConfig.class);
		final DuplexPluginConfig duplexPluginConfig =
				context.mock(DuplexPluginConfig.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionDispatcher dispatcher =
				context.mock(ConnectionDispatcher.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		// Two simplex plugin factories: both create plugins, one fails to start
		final SimplexPluginFactory simplexFactory =
				context.mock(SimplexPluginFactory.class);
		final SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		final TransportId simplexId = new TransportId(TestUtils.getRandomId());
		final long simplexLatency = 12345;
		final SimplexPluginFactory simplexFailFactory =
				context.mock(SimplexPluginFactory.class, "simplexFailFactory");
		final SimplexPlugin simplexFailPlugin =
				context.mock(SimplexPlugin.class, "simplexFailPlugin");
		final TransportId simplexFailId =
				new TransportId(TestUtils.getRandomId());
		final long simplexFailLatency = 23456;
		// Two duplex plugin factories: one creates a plugin, the other fails
		final DuplexPluginFactory duplexFactory =
				context.mock(DuplexPluginFactory.class);
		final DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		final TransportId duplexId = new TransportId(TestUtils.getRandomId());
		final long duplexLatency = 34567;
		final DuplexPluginFactory duplexFailFactory =
				context.mock(DuplexPluginFactory.class, "duplexFailFactory");
		final TransportId duplexFailId =
				new TransportId(TestUtils.getRandomId());
		context.checking(new Expectations() {{
			// First simplex plugin
			oneOf(simplexPluginConfig).getFactories();
			will(returnValue(Arrays.asList(simplexFactory,
					simplexFailFactory)));
			oneOf(simplexFactory).getId();
			will(returnValue(simplexId));
			oneOf(simplexFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexPlugin)); // Created
			oneOf(simplexPlugin).getMaxLatency();
			will(returnValue(simplexLatency));
			oneOf(db).addTransport(simplexId, simplexLatency);
			will(returnValue(true));
			oneOf(simplexPlugin).start();
			will(returnValue(true)); // Started
			// Second simplex plugin
			oneOf(simplexFailFactory).getId();
			will(returnValue(simplexFailId));
			oneOf(simplexFailFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexFailPlugin)); // Created
			oneOf(simplexFailPlugin).getMaxLatency();
			will(returnValue(simplexFailLatency));
			oneOf(db).addTransport(simplexFailId, simplexFailLatency);
			will(returnValue(true));
			oneOf(simplexFailPlugin).start();
			will(returnValue(false)); // Failed to start
			// First duplex plugin
			oneOf(duplexPluginConfig).getFactories();
			will(returnValue(Arrays.asList(duplexFactory, duplexFailFactory)));
			oneOf(duplexFactory).getId();
			will(returnValue(duplexId));
			oneOf(duplexFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(duplexPlugin)); // Created
			oneOf(duplexPlugin).getMaxLatency();
			will(returnValue(duplexLatency));
			oneOf(db).addTransport(duplexId, duplexLatency);
			will(returnValue(true));
			oneOf(duplexPlugin).start();
			will(returnValue(true)); // Started
			// Second duplex plugin
			oneOf(duplexFailFactory).getId();
			will(returnValue(duplexFailId));
			oneOf(duplexFailFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(null)); // Failed to create a plugin
			// Start the poller
			oneOf(poller).start(Arrays.asList(simplexPlugin, duplexPlugin));
			// Stop the poller
			oneOf(poller).stop();
			// Stop the plugins
			oneOf(simplexPlugin).stop();
			oneOf(duplexPlugin).stop();
		}});
		PluginManagerImpl p = new PluginManagerImpl(pluginExecutor,
				simplexPluginConfig, duplexPluginConfig, db, poller,
				dispatcher, uiCallback);
		// Two plugins should be started and stopped
		assertEquals(2, p.start());
		assertEquals(2, p.stop());
		context.assertIsSatisfied();
	}
}
