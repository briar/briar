package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.plugin.ConnectionHandler;
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
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.plugin.event.TransportInactiveEvent;
import org.briarproject.bramble.api.plugin.simplex.SimplexPlugin;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.briarproject.bramble.test.ImmediateExecutor;
import org.briarproject.bramble.test.RunAction;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.test.CollectionMatcher.collectionOf;
import static org.briarproject.bramble.test.PairMatcher.pairOf;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;

public class PollerImplTest extends BrambleMockTestCase {

	private final ScheduledExecutorService scheduler =
			context.mock(ScheduledExecutorService.class);
	private final ConnectionManager connectionManager =
			context.mock(ConnectionManager.class);
	private final ConnectionRegistry connectionRegistry =
			context.mock(ConnectionRegistry.class);
	private final PluginManager pluginManager =
			context.mock(PluginManager.class);
	private final TransportPropertyManager transportPropertyManager =
			context.mock(TransportPropertyManager.class);
	private final Clock clock = context.mock(Clock.class);
	private final ScheduledFuture future = context.mock(ScheduledFuture.class);
	private final SecureRandom random;

	private final Executor ioExecutor = new ImmediateExecutor();
	private final TransportId transportId = getTransportId();
	private final ContactId contactId = getContactId();
	private final TransportProperties properties = new TransportProperties();
	private final int pollingInterval = 60 * 1000;
	private final long now = System.currentTimeMillis();

	private PollerImpl poller;

	public PollerImplTest() {
		context.setImposteriser(ClassImposteriser.INSTANCE);
		random = context.mock(SecureRandom.class);
	}

	@Before
	public void setUp() {
		poller = new PollerImpl(ioExecutor, scheduler, connectionManager,
				connectionRegistry, pluginManager, transportPropertyManager,
				random, clock);
	}

	@Test
	public void testConnectOnContactAdded() throws Exception {
		// Two simplex plugins: one supports polling, the other doesn't
		SimplexPlugin simplexPlugin = context.mock(SimplexPlugin.class);
		SimplexPlugin simplexPlugin1 =
				context.mock(SimplexPlugin.class, "simplexPlugin1");
		TransportId simplexId1 = getTransportId();
		List<SimplexPlugin> simplexPlugins =
				asList(simplexPlugin, simplexPlugin1);
		TransportConnectionWriter simplexWriter =
				context.mock(TransportConnectionWriter.class);

		// Two duplex plugins: one supports polling, the other doesn't
		DuplexPlugin duplexPlugin = context.mock(DuplexPlugin.class);
		TransportId duplexId = getTransportId();
		DuplexPlugin duplexPlugin1 =
				context.mock(DuplexPlugin.class, "duplexPlugin1");
		List<DuplexPlugin> duplexPlugins =
				asList(duplexPlugin, duplexPlugin1);
		DuplexTransportConnection duplexConnection =
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
			// Get the transport properties
			oneOf(transportPropertyManager).getRemoteProperties(contactId,
					simplexId1);
			will(returnValue(properties));
			// Connect to the contact
			oneOf(simplexPlugin1).createWriter(properties);
			will(returnValue(simplexWriter));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					simplexId1, simplexWriter);
			// Get the duplex plugins
			oneOf(pluginManager).getDuplexPlugins();
			will(returnValue(duplexPlugins));
			// The duplex plugin supports polling
			oneOf(duplexPlugin).shouldPoll();
			will(returnValue(true));
			// Check whether the contact is already connected
			oneOf(duplexPlugin).getId();
			will(returnValue(duplexId));
			oneOf(connectionRegistry).isConnected(contactId, duplexId);
			will(returnValue(false));
			// Get the transport properties
			oneOf(transportPropertyManager).getRemoteProperties(contactId,
					duplexId);
			will(returnValue(properties));
			// Connect to the contact
			oneOf(duplexPlugin).createConnection(properties);
			will(returnValue(duplexConnection));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					duplexId, duplexConnection);
			// The second plugin doesn't support polling
			oneOf(duplexPlugin1).shouldPoll();
			will(returnValue(false));
		}});

		poller.eventOccurred(new ContactAddedEvent(contactId, true));
	}

	@Test
	public void testRescheduleAndReconnectOnConnectionClosed()
			throws Exception {
		DuplexPlugin plugin = context.mock(DuplexPlugin.class);
		DuplexTransportConnection duplexConnection =
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
			will(returnValue(future));
			// connectToContact()
			// Check whether the contact is already connected
			oneOf(connectionRegistry).isConnected(contactId, transportId);
			will(returnValue(false));
			// Get the transport properties
			oneOf(transportPropertyManager).getRemoteProperties(contactId,
					transportId);
			will(returnValue(properties));
			// Connect to the contact
			oneOf(plugin).createConnection(properties);
			will(returnValue(duplexConnection));
			// Pass the connection to the connection manager
			oneOf(connectionManager).manageOutgoingConnection(contactId,
					transportId, duplexConnection);
		}});

		poller.eventOccurred(new ConnectionClosedEvent(contactId, transportId,
				false));
	}

	@Test
	public void testRescheduleOnConnectionOpened() {
		Plugin plugin = context.mock(Plugin.class);

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
			will(returnValue(future));
		}});

		poller.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
	}

	@Test
	public void testRescheduleDoesNotReplaceEarlierTask() {
		Plugin plugin = context.mock(Plugin.class);

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
			will(returnValue(future));
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

		poller.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
		poller.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
	}

	@Test
	public void testRescheduleReplacesLaterTask() {
		Plugin plugin = context.mock(Plugin.class);

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
			will(returnValue(future));
			// Second event
			// Get the plugin
			oneOf(pluginManager).getPlugin(transportId);
			will(returnValue(plugin));
			// The plugin supports polling
			oneOf(plugin).shouldPoll();
			will(returnValue(true));
			// Replace the previously scheduled task, due later
			oneOf(plugin).getPollingInterval();
			will(returnValue(pollingInterval - 2));
			oneOf(clock).currentTimeMillis();
			will(returnValue(now + 1));
			oneOf(future).cancel(false);
			oneOf(scheduler).schedule(with(any(Runnable.class)),
					with((long) pollingInterval - 2), with(MILLISECONDS));
		}});

		poller.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
		poller.eventOccurred(new ConnectionOpenedEvent(contactId, transportId,
				false));
	}

	@Test
	public void testPollsOnTransportActivated() throws Exception {
		DuplexPlugin plugin = context.mock(DuplexPlugin.class);

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
			will(returnValue(future));
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
			will(returnValue(future));
			// Get the transport properties and connected contacts
			oneOf(transportPropertyManager).getRemoteProperties(transportId);
			will(returnValue(singletonMap(contactId, properties)));
			oneOf(connectionRegistry).getConnectedContacts(transportId);
			will(returnValue(emptyList()));
			// Poll the plugin
			oneOf(plugin).poll(with(collectionOf(
					pairOf(equal(properties), any(ConnectionHandler.class)))));
		}});

		poller.eventOccurred(new TransportActiveEvent(transportId));
	}

	@Test
	public void testDoesNotPollIfAllContactsAreConnected() throws Exception {
		DuplexPlugin plugin = context.mock(DuplexPlugin.class);

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
			will(returnValue(future));
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
			will(returnValue(future));
			// Get the transport properties and connected contacts
			oneOf(transportPropertyManager).getRemoteProperties(transportId);
			will(returnValue(singletonMap(contactId, properties)));
			oneOf(connectionRegistry).getConnectedContacts(transportId);
			will(returnValue(singletonList(contactId)));
			// All contacts are connected, so don't poll the plugin
		}});

		poller.eventOccurred(new TransportActiveEvent(transportId));
	}

	@Test
	public void testCancelsPollingOnTransportDeactivated() {
		Plugin plugin = context.mock(Plugin.class);

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
			will(returnValue(future));
			// The plugin is deactivated before the task runs - cancel the task
			oneOf(future).cancel(false);
		}});

		poller.eventOccurred(new TransportActiveEvent(transportId));
		poller.eventOccurred(new TransportInactiveEvent(transportId));
	}
}
