package org.briarproject.plugins;

import org.briarproject.BriarTestCase;
import org.briarproject.api.TransportId;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginConfig;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginConfig;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.system.Clock;
import org.briarproject.api.ui.UiCallback;
import org.briarproject.system.SystemClock;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertTrue;

public class PluginManagerImplTest extends BriarTestCase {

	@Test
	public void testStartAndStop() throws Exception {
		Clock clock = new SystemClock();
		Mockery context = new Mockery() {{
			setThreadingPolicy(new Synchroniser());
		}};
		final Executor ioExecutor = Executors.newSingleThreadExecutor();
		final EventBus eventBus = context.mock(EventBus.class);
		final SimplexPluginConfig simplexPluginConfig =
				context.mock(SimplexPluginConfig.class);
		final DuplexPluginConfig duplexPluginConfig =
				context.mock(DuplexPluginConfig.class);
		final DatabaseComponent db = context.mock(DatabaseComponent.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final SettingsManager settingsManager =
				context.mock(SettingsManager.class);
		final TransportPropertyManager transportPropertyManager =
				context.mock(TransportPropertyManager.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		// Two simplex plugin factories: both create plugins, one fails to start
		final SimplexPluginFactory simplexFactory =
				context.mock(SimplexPluginFactory.class);
		final SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		final TransportId simplexId = new TransportId("simplex");
		final int simplexLatency = 12345;
		final Transaction simplexTxn = new Transaction(null);
		final SimplexPluginFactory simplexFailFactory =
				context.mock(SimplexPluginFactory.class, "simplexFailFactory");
		final SimplexPlugin simplexFailPlugin =
				context.mock(SimplexPlugin.class, "simplexFailPlugin");
		final TransportId simplexFailId = new TransportId("simplex1");
		final int simplexFailLatency = 23456;
		final Transaction simplexFailTxn = new Transaction(null);
		// Two duplex plugin factories: one creates a plugin, the other fails
		final DuplexPluginFactory duplexFactory =
				context.mock(DuplexPluginFactory.class);
		final DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		final TransportId duplexId = new TransportId("duplex");
		final int duplexLatency = 34567;
		final Transaction duplexTxn = new Transaction(null);
		final DuplexPluginFactory duplexFailFactory =
				context.mock(DuplexPluginFactory.class, "duplexFailFactory");
		final TransportId duplexFailId = new TransportId("duplex1");
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
			oneOf(db).startTransaction();
			will(returnValue(simplexTxn));
			oneOf(db).addTransport(simplexTxn, simplexId, simplexLatency);
			oneOf(db).endTransaction(simplexTxn);
			oneOf(simplexPlugin).start();
			will(returnValue(true)); // Started
			oneOf(simplexPlugin).shouldPoll();
			will(returnValue(true));
			oneOf(poller).addPlugin(simplexPlugin);
			// Second simplex plugin
			oneOf(simplexFailFactory).getId();
			will(returnValue(simplexFailId));
			oneOf(simplexFailFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexFailPlugin)); // Created
			oneOf(simplexFailPlugin).getMaxLatency();
			will(returnValue(simplexFailLatency));
			oneOf(db).startTransaction();
			will(returnValue(simplexFailTxn));
			oneOf(db).addTransport(simplexFailTxn, simplexFailId,
					simplexFailLatency);
			oneOf(db).endTransaction(simplexFailTxn);
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
			oneOf(db).startTransaction();
			will(returnValue(duplexTxn));
			oneOf(db).addTransport(duplexTxn, duplexId, duplexLatency);
			oneOf(db).endTransaction(duplexTxn);
			oneOf(duplexPlugin).start();
			will(returnValue(true)); // Started
			oneOf(duplexPlugin).shouldPoll();
			will(returnValue(false));
			// Second duplex plugin
			oneOf(duplexFailFactory).getId();
			will(returnValue(duplexFailId));
			oneOf(duplexFailFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(null)); // Failed to create a plugin
			// Stop the poller
			oneOf(poller).stop();
			// Stop the plugins
			oneOf(simplexPlugin).stop();
			oneOf(duplexPlugin).stop();
		}});
		PluginManagerImpl p = new PluginManagerImpl(ioExecutor, eventBus,
				simplexPluginConfig, duplexPluginConfig, clock, db, poller,
				connectionManager, settingsManager, transportPropertyManager,
				uiCallback);

		// Two plugins should be started and stopped
		assertTrue(p.start());
		assertTrue(p.stop());
		context.assertIsSatisfied();
	}
}
