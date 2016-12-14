package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactStatusChangedEvent;
import org.briarproject.bramble.api.plugin.ConnectionManager;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportConnectionWriter;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.TransportEnabledEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class PollerTest extends BrambleTestCase {

	private final ContactId contactId = new ContactId(234);
	private final int pollingInterval = 60 * 1000;
	private final long now = System.currentTimeMillis();

	@Test
	public void testConnectOnContactStatusChanged() throws Exception {
		Mockery context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final Executor ioExecutor = new ImmediateExecutor();
		final ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final PluginManager pluginManager = context.mock(PluginManager.class);
		final SecureRandom random = context.mock(SecureRandom.class);
		final Clock clock = context.mock(Clock.class);

		// Two simplex plugins: one supports polling, the other doesn't
		final SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		final SimplexPlugin simplexPlugin1 =
				context.mock(SimplexPlugin.class, "simplexPlugin1");
		final TransportId simplexId1 = new TransportId("simplex1");
		final List<SimplexPlugin> simplexPlugins = Arrays.asList(simplexPlugin,
				simplexPlugin1);
		final TransportConnectionWriter simplexWriter =
				context.mock(TransportConnectionWriter.class);

		// Two duplex plugins: one supports polling, the other doesn't
		final DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		final TransportId duplexId = new TransportId("duplex");
		final DuplexPlugin duplexPlugin1 =
				context.mock(DuplexPlugin.class, "duplexPlugin1");
		final List<DuplexPlugin> duplexPlugins = Arrays.asList(duplexPlugin,
				duplexPlugin1);
		final DuplexTransportConnection duplexConnection =
				context.mock(DuplexTransportConnection.class);

		context.checking(new Expectations() {{
			// Get the simplex plugins
			oneOf(pluginManager).getSimplexPlugins();
			will(returnValue(simplexPlugins));
			// The first plugin doesn't support polling
			oneOf(simplexPlugin).shouldPoll();
			will(returnValue(false));
			// The second plugin supports polling
			oneOf(simplexPlugin1).shouldPoll();
			will(returnValue(true));
			// Check whether the contact is already connected
			oneOf(simplexPlugin1).getId();
			will(returnValue(simplexId1));
			oneOf(connectionRegistry).isConnected(contactId, simplexId1);
			will(returnValue(false));
			// Connect to the contact
			oneOf(simplexPlugin1).createWriter(contactId);
			will(returnValue(simplexWriter));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					simplexId1, simplexWriter);
			// Get the duplex plugins
			oneOf(pluginManager).getDuplexPlugins();
			will(returnValue(duplexPlugins));
			// The first plugin supports polling
			oneOf(duplexPlugin).shouldPoll();
			will(returnValue(true));
			// Check whether the contact is already connected
			oneOf(duplexPlugin).getId();
			will(returnValue(duplexId));
			oneOf(connectionRegistry).isConnected(contactId, duplexId);
			will(returnValue(false));
			// Connect to the contact
			oneOf(duplexPlugin).createConnection(contactId);
			will(returnValue(duplexConnection));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					duplexId, duplexConnection);
			// The second plugin doesn't support polling
			oneOf(duplexPlugin1).shouldPoll();
			will(returnValue(false));
		}});

		Poller p = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);

		p.eventOccurred(new ContactStatusChangedEvent(contactId, true));

		context.assertIsSatisfied();
	}

	@Test
	public void testRescheduleAndReconnectOnConnectionClosed()
			throws Exception {
		Mockery context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final Executor ioExecutor = new ImmediateExecutor();
		final ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final PluginManager pluginManager = context.mock(PluginManager.class);
		final SecureRandom random = context.mock(SecureRandom.class);
		final Clock clock = context.mock(Clock.class);

		final DuplexPlugin plugin = context.mock(DuplexPlugin.class);
		final TransportId transportId = new TransportId("id");
		final DuplexTransportConnection duplexConnection =
				context.mock(DuplexTransportConnection.class);

		context.checking(new Expectations() {{
			allowing(plugin).getId();
			will(returnValue(transportId));
			// reschedule()
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Schedule the next poll
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with((long) pollingInterval), with(MILLISECONDS));
			// connectToContact()
			// Check whether the contact is already connected
			oneOf(connectionRegistry).isConnected(contactId, transportId);
			will(returnValue(false));
			// Connect to the contact
			oneOf(plugin).createConnection(contactId);
			will(returnValue(duplexConnection));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					transportId, duplexConnection);
		}});

		Poller p = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);

		p.eventOccurred(new ConnectionClosedEvent(contactId, transportId,
				false));

		context.assertIsSatisfied();
	}


	@Test
	public void testRescheduleOnConnectionOpened() throws Exception {
		Mockery context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final Executor ioExecutor = new ImmediateExecutor();
		final ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final PluginManager pluginManager = context.mock(PluginManager.class);
		final SecureRandom random = context.mock(SecureRandom.class);
		final Clock clock = context.mock(Clock.class);

		final DuplexPlugin plugin = context.mock(DuplexPlugin.class);
		final TransportId transportId = new TransportId("id");

		context.checking(new Expectations() {{
			allowing(plugin).getId();
			will(returnValue(transportId));
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Schedule the next poll
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with((long) pollingInterval), with(MILLISECONDS));
		}});

		Poller p = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);

		p.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));

		context.assertIsSatisfied();
	}

	@Test
	public void testRescheduleDoesNotReplaceEarlierTask() throws Exception {
		Mockery context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final Executor ioExecutor = new ImmediateExecutor();
		final ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final PluginManager pluginManager = context.mock(PluginManager.class);
		final SecureRandom random = context.mock(SecureRandom.class);
		final Clock clock = context.mock(Clock.class);

		final DuplexPlugin plugin = context.mock(DuplexPlugin.class);
		final TransportId transportId = new TransportId("id");

		context.checking(new Expectations() {{
			allowing(plugin).getId();
			will(returnValue(transportId));
			// First event
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Schedule the next poll
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with((long) pollingInterval), with(MILLISECONDS));
			// Second event
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Don't replace the previously scheduled task, due earlier
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now + 1));
		}});

		Poller p = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);

		p.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
		p.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));

		context.assertIsSatisfied();
	}

	@Test
	public void testPollOnTransportEnabled() throws Exception {
		Mockery context = new Mockery();
		context.setImposteriser(ClassImposteriser.INSTANCE);
		final Executor ioExecutor = new ImmediateExecutor();
		final ScheduledExecutorService scheduler =
				context.mock(ScheduledExecutorService.class);
		final ConnectionManager connectionManager =
				context.mock(ConnectionManager.class);
		final ConnectionRegistry connectionRegistry =
				context.mock(ConnectionRegistry.class);
		final PluginManager pluginManager = context.mock(PluginManager.class);
		final SecureRandom random = context.mock(SecureRandom.class);
		final Clock clock = context.mock(Clock.class);

		final Plugin plugin = context.mock(Plugin.class);
		final TransportId transportId = new TransportId("id");
		final List<ContactId> connected = Collections.singletonList(contactId);

		context.checking(new Expectations() {{
			allowing(plugin).getId();
			will(returnValue(transportId));
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Schedule a polling task immediately
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)), with(0L),
					with(MILLISECONDS));
			will(new RunAction());
			// Running the polling task schedules the next polling task
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval));
			oneOf(random).nextDouble();
			will(returnValue(0.5));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now));
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with((long) (pollingInterval * 0.5)), with(MILLISECONDS));
			// Poll the plugin
			oneOf(connectionRegistry).getConnectedContacts(transportId);
			will(returnValue(connected));
			oneOf(plugin).poll(connected);
		}});

		Poller p = new Poller(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, random, clock);

		p.eventOccurred(new TransportEnabledEvent(transportId));

		context.assertIsSatisfied();
	}
}
