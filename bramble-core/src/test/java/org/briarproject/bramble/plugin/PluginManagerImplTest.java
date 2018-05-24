package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.PluginException;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginFactory;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginCallback;
import org.briarproject.bramble.api.plugin.simplex.SimplexPluginFactory;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.briarproject.bramble.test.TestUtils.getTransportId;

public class PluginManagerImplTest extends BrambleTestCase {

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery() {{
			setThreadingPolicy(new Synchroniser());
		}};
		Executor ioExecutor = Executors.newSingleThreadExecutor();
		ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		SecureRandom random = new SecureRandom();
		Clock clock = context.mock(Clock.class);
		EventBus eventBus = context.mock(EventBus.class);
		PluginConfig pluginConfig = context.mock(PluginConfig.class);
		ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		SettingsManager settingsManager =
				context.mock(SettingsManager.class);
		TransportPropertyManager transportPropertyManager =
				context.mock(TransportPropertyManager.class);

		// Two simplex plugin factories: both create plugins, one fails to start
		SimplexPluginFactory simplexFactory =
				context.mock(SimplexPluginFactory.class);
		SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		TransportId simplexId = getTransportId();
		SimplexPluginFactory simplexFailFactory =
				context.mock(SimplexPluginFactory.class, "simplexFailFactory");
		SimplexPlugin simplexFailPlugin =
				context.mock(SimplexPlugin.class, "simplexFailPlugin");
		TransportId simplexFailId = getTransportId();

		// Two duplex plugin factories: one creates a plugin, the other fails
		DuplexPluginFactory duplexFactory =
				context.mock(DuplexPluginFactory.class);
		DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		TransportId duplexId = getTransportId();
		DuplexPluginFactory duplexFailFactory =
				context.mock(DuplexPluginFactory.class, "duplexFailFactory");
		TransportId duplexFailId = getTransportId();

		context.checking(new Expectations() {{
			allowing(simplexPlugin).getId();
			will(returnValue(simplexId));
			allowing(simplexFailPlugin).getId();
			will(returnValue(simplexFailId));
			allowing(duplexPlugin).getId();
			will(returnValue(duplexId));
			allowing(pluginConfig).shouldPoll();
			will(returnValue(false));
			// start()
			// First simplex plugin
			oneOf(pluginConfig).getSimplexFactories();
			will(returnValue(Arrays.asList(simplexFactory,
					simplexFailFactory)));
			oneOf(simplexFactory).getId();
			will(returnValue(simplexId));
			oneOf(simplexFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexPlugin)); // Created
			oneOf(simplexPlugin).start();
			// Second simplex plugin
			oneOf(simplexFailFactory).getId();
			will(returnValue(simplexFailId));
			oneOf(simplexFailFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexFailPlugin)); // Created
			oneOf(simplexFailPlugin).start();
			will(throwException(new PluginException()));
			// First duplex plugin
			oneOf(pluginConfig).getDuplexFactories();
			will(returnValue(Arrays.asList(duplexFactory, duplexFailFactory)));
			oneOf(duplexFactory).getId();
			will(returnValue(duplexId));
			oneOf(duplexFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(duplexPlugin)); // Created
			oneOf(duplexPlugin).start();
			// Second duplex plugin
			oneOf(duplexFailFactory).getId();
			will(returnValue(duplexFailId));
			oneOf(duplexFailFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(null)); // Failed to create a plugin
			// stop()
			// Stop the plugins
			oneOf(simplexPlugin).stop();
			oneOf(simplexFailPlugin).stop();
			oneOf(duplexPlugin).stop();
		}});

		PluginManagerImpl p = new PluginManagerImpl(ioExecutor, scheduler,
				eventBus, pluginConfig, connectionManager, connectionRegistry,
				settingsManager, transportPropertyManager, random, clock);

		// Two plugins should be started and stopped
		p.startService();
		p.stopService();

		context.assertIsSatisfied();
	}
}
