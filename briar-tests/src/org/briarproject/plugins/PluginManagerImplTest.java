package org.briarproject.plugins;

import org.briarproject.BriarTestCase;
import org.briarproject.ImmediateExecutor;
import org.briarproject.api.TransportId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.event.ContactStatusChangedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.plugins.ConnectionManager;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.plugins.PluginConfig;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.plugins.duplex.DuplexPluginFactory;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;
import org.briarproject.api.plugins.simplex.SimplexPlugin;
import org.briarproject.api.plugins.simplex.SimplexPluginCallback;
import org.briarproject.api.plugins.simplex.SimplexPluginFactory;
import org.briarproject.api.properties.TransportPropertyManager;
import org.briarproject.api.settings.SettingsManager;
import org.briarproject.api.ui.UiCallback;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PluginManagerImplTest extends BriarTestCase {

	@Test
	public void testStartAndStop() throws Exception {
		Mockery context = new Mockery() {{
			setThreadingPolicy(new Synchroniser());
		}};
		final Executor ioExecutor = Executors.newSingleThreadExecutor();
		final EventBus eventBus = context.mock(EventBus.class);
		final PluginConfig pluginConfig = context.mock(PluginConfig.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
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
		final SimplexPluginFactory simplexFailFactory =
				context.mock(SimplexPluginFactory.class, "simplexFailFactory");
		final SimplexPlugin simplexFailPlugin =
				context.mock(SimplexPlugin.class, "simplexFailPlugin");
		final TransportId simplexFailId = new TransportId("simplex1");

		// Two duplex plugin factories: one creates a plugin, the other fails
		final DuplexPluginFactory duplexFactory =
				context.mock(DuplexPluginFactory.class);
		final DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		final TransportId duplexId = new TransportId("duplex");
		final DuplexPluginFactory duplexFailFactory =
				context.mock(DuplexPluginFactory.class, "duplexFailFactory");
		final TransportId duplexFailId = new TransportId("duplex1");

		context.checking(new Expectations() {{
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
			oneOf(simplexFailPlugin).start();
			will(returnValue(false)); // Failed to start
			// First duplex plugin
			oneOf(pluginConfig).getDuplexFactories();
			will(returnValue(Arrays.asList(duplexFactory, duplexFailFactory)));
			oneOf(duplexFactory).getId();
			will(returnValue(duplexId));
			oneOf(duplexFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(duplexPlugin)); // Created
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
			// Start listening for events
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			// stop()
			// Stop listening for events
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			// Stop the poller
			oneOf(poller).stop();
			// Stop the plugins
			oneOf(simplexPlugin).stop();
			oneOf(duplexPlugin).stop();
		}});

		PluginManagerImpl p = new PluginManagerImpl(ioExecutor, eventBus,
				pluginConfig, poller, connectionManager, connectionRegistry,
				settingsManager, transportPropertyManager, uiCallback);

		// Two plugins should be started and stopped
		p.startService();
		p.stopService();

		context.assertIsSatisfied();
	}

	@Test
	public void testConnectToNewContact() throws Exception {
		Mockery context = new Mockery();
		final Executor ioExecutor = new ImmediateExecutor();
		final EventBus eventBus = context.mock(EventBus.class);
		final PluginConfig pluginConfig = context.mock(PluginConfig.class);
		final Poller poller = context.mock(Poller.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final SettingsManager settingsManager =
				context.mock(SettingsManager.class);
		final TransportPropertyManager transportPropertyManager =
				context.mock(TransportPropertyManager.class);
		final UiCallback uiCallback = context.mock(UiCallback.class);
		final TransportConnectionWriter transportConnectionWriter =
				context.mock(TransportConnectionWriter.class);
		final DuplexTransportConnection duplexTransportConnection =
				context.mock(DuplexTransportConnection.class);

		final ContactId contactId = new ContactId(234);

		// Two simplex plugins: one supports polling, the other doesn't
		final SimplexPluginFactory simplexFactory =
				context.mock(SimplexPluginFactory.class);
		final SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		final TransportId simplexId = new TransportId("simplex");
		final SimplexPluginFactory simplexFactory1 =
				context.mock(SimplexPluginFactory.class, "simplexFactory1");
		final SimplexPlugin simplexPlugin1 =
				context.mock(SimplexPlugin.class, "simplexPlugin1");
		final TransportId simplexId1 = new TransportId("simplex1");

		// Two duplex plugins: one supports polling, the other doesn't
		final DuplexPluginFactory duplexFactory =
				context.mock(DuplexPluginFactory.class);
		final DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		final TransportId duplexId = new TransportId("duplex");
		final DuplexPluginFactory duplexFactory1 =
				context.mock(DuplexPluginFactory.class, "duplexFactory1");
		final DuplexPlugin duplexPlugin1 =
				context.mock(DuplexPlugin.class, "duplexPlugin1");
		final TransportId duplexId1 = new TransportId("duplex1");

		context.checking(new Expectations() {{
			// start()
			// First simplex plugin
			oneOf(pluginConfig).getSimplexFactories();
			will(returnValue(Arrays.asList(simplexFactory, simplexFactory1)));
			oneOf(simplexFactory).getId();
			will(returnValue(simplexId));
			oneOf(simplexFactory).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexPlugin)); // Created
			oneOf(simplexPlugin).start();
			will(returnValue(true)); // Started
			oneOf(simplexPlugin).shouldPoll();
			will(returnValue(true)); // Should poll
			oneOf(poller).addPlugin(simplexPlugin);
			// Second simplex plugin
			oneOf(simplexFactory1).getId();
			will(returnValue(simplexId1));
			oneOf(simplexFactory1).createPlugin(with(any(
					SimplexPluginCallback.class)));
			will(returnValue(simplexPlugin1)); // Created
			oneOf(simplexPlugin1).start();
			will(returnValue(true)); // Started
			oneOf(simplexPlugin1).shouldPoll();
			will(returnValue(false)); // Should not poll
			// First duplex plugin
			oneOf(pluginConfig).getDuplexFactories();
			will(returnValue(Arrays.asList(duplexFactory, duplexFactory1)));
			oneOf(duplexFactory).getId();
			will(returnValue(duplexId));
			oneOf(duplexFactory).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(duplexPlugin)); // Created
			oneOf(duplexPlugin).start();
			will(returnValue(true)); // Started
			oneOf(duplexPlugin).shouldPoll();
			will(returnValue(true)); // Should poll
			oneOf(poller).addPlugin(duplexPlugin);
			// Second duplex plugin
			oneOf(duplexFactory1).getId();
			will(returnValue(duplexId1));
			oneOf(duplexFactory1).createPlugin(with(any(
					DuplexPluginCallback.class)));
			will(returnValue(duplexPlugin1)); // Created
			oneOf(duplexPlugin1).start();
			will(returnValue(true)); // Started
			oneOf(duplexPlugin1).shouldPoll();
			will(returnValue(false)); // Should not poll
			// Start listening for events
			oneOf(eventBus).addListener(with(any(EventListener.class)));
			// eventOccurred()
			// First simplex plugin
			oneOf(simplexPlugin).shouldPoll();
			will(returnValue(true));
			oneOf(simplexPlugin).getId();
			will(returnValue(simplexId));
			oneOf(connectionRegistry).isConnected(contactId, simplexId);
			will(returnValue(false));
			oneOf(simplexPlugin).createWriter(contactId);
			will(returnValue(transportConnectionWriter));
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					simplexId, transportConnectionWriter);
			// Second simplex plugin
			oneOf(simplexPlugin1).shouldPoll();
			will(returnValue(false));
			// First duplex plugin
			oneOf(duplexPlugin).shouldPoll();
			will(returnValue(true));
			oneOf(duplexPlugin).getId();
			will(returnValue(duplexId));
			oneOf(connectionRegistry).isConnected(contactId, duplexId);
			will(returnValue(false));
			oneOf(duplexPlugin).createConnection(contactId);
			will(returnValue(duplexTransportConnection));
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					duplexId, duplexTransportConnection);
			// Second duplex plugin
			oneOf(duplexPlugin1).shouldPoll();
			will(returnValue(false));
			// stop()
			// Stop listening for events
			oneOf(eventBus).removeListener(with(any(EventListener.class)));
			// Stop the poller
			oneOf(poller).stop();
			// Stop the plugins
			oneOf(simplexPlugin).stop();
			oneOf(simplexPlugin1).stop();
			oneOf(duplexPlugin).stop();
			oneOf(duplexPlugin1).stop();
		}});

		PluginManagerImpl p = new PluginManagerImpl(ioExecutor, eventBus,
				pluginConfig, poller, connectionManager, connectionRegistry,
				settingsManager, transportPropertyManager, uiCallback);

		p.startService();
		p.eventOccurred(new ContactStatusChangedEvent(contactId, true));
		p.stopService();

		context.assertIsSatisfied();
	}
}
