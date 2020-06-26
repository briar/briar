package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.connection.InterruptibleConnection;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.PluginConfig;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionClosedEvent;
import org.briarproject.bramble.api.rendezvous.event.RendezvousConnectionOpenedEvent;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConnectionRegistryImplTest extends BrambleMockTestCase {

	private final EventBus eventBus = context.mock(EventBus.class);
	private final PluginConfig pluginConfig = context.mock(PluginConfig.class);
	private final InterruptibleConnection conn1 =
			context.mock(InterruptibleConnection.class, "conn1");
	private final InterruptibleConnection conn2 =
			context.mock(InterruptibleConnection.class, "conn2");
	private final InterruptibleConnection conn3 =
			context.mock(InterruptibleConnection.class, "conn3");

	private final ContactId contactId1 = getContactId();
	private final ContactId contactId2 = getContactId();
	private final TransportId transportId1 = getTransportId();
	private final TransportId transportId2 = getTransportId();
	private final TransportId transportId3 = getTransportId();
	private final PendingContactId pendingContactId =
			new PendingContactId(getRandomId());

	private final Priority low =
			new Priority(fromHexString("00000000000000000000000000000000"));
	private final Priority high =
			new Priority(fromHexString("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));

	@Test
	public void testRegisterMultipleConnections() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyMap()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// The registry should be empty
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId1));
		assertEquals(emptyList(), c.getConnectedContacts(transportId2));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId2));
		assertEquals(emptyList(), c.getConnectedContacts(transportId3));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId3));
		assertFalse(c.isConnected(contactId1));
		assertFalse(c.isConnected(contactId1, transportId1));
		assertFalse(c.isConnected(contactId1, transportId2));
		assertFalse(c.isConnected(contactId1, transportId3));

		// Check that a registered connection shows up - this should
		// broadcast a ConnectionOpenedEvent and a ContactConnectedEvent
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn1);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));
		assertTrue(c.isConnected(contactId1));
		assertTrue(c.isConnected(contactId1, transportId1));

		// Register another connection with the same contact and transport -
		// this should broadcast a ConnectionOpenedEvent and lookup should be
		// unaffected
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn2);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));
		assertTrue(c.isConnected(contactId1));
		assertTrue(c.isConnected(contactId1, transportId1));

		// Unregister one of the connections - this should broadcast a
		// ConnectionClosedEvent and lookup should be unaffected
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
		}});
		c.unregisterConnection(contactId1, transportId1, conn1, true, false);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));
		assertTrue(c.isConnected(contactId1));
		assertTrue(c.isConnected(contactId1, transportId1));

		// Unregister the other connection - this should broadcast a
		// ConnectionClosedEvent and a ContactDisconnectedEvent
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					ContactDisconnectedEvent.class)));
		}});
		c.unregisterConnection(contactId1, transportId1, conn2, true, false);
		context.assertIsSatisfied();

		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId1));
		assertFalse(c.isConnected(contactId1));
		assertFalse(c.isConnected(contactId1, transportId1));

		// Try to unregister the connection again - exception should be thrown
		try {
			c.unregisterConnection(contactId1, transportId1, conn2,
					true, false);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}

	@Test
	public void testRegisterMultipleContacts() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyMap()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Register two contacts with one transport, then one of the contacts
		// with a second transport - this should broadcast three
		// ConnectionOpenedEvents and two ContactConnectedEvents
		context.checking(new Expectations() {{
			exactly(3).of(eventBus).broadcast(with(any(
					ConnectionOpenedEvent.class)));
			exactly(2).of(eventBus).broadcast(with(any(
					ContactConnectedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn1);
		c.registerIncomingConnection(contactId2, transportId1, conn2);
		c.registerIncomingConnection(contactId2, transportId2, conn3);
		context.assertIsSatisfied();

		assertTrue(c.isConnected(contactId1));
		assertTrue(c.isConnected(contactId2));

		assertTrue(c.isConnected(contactId1, transportId1));
		assertFalse(c.isConnected(contactId1, transportId2));

		assertTrue(c.isConnected(contactId2, transportId1));
		assertTrue(c.isConnected(contactId2, transportId2));

		Collection<ContactId> connected = c.getConnectedContacts(transportId1);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId1));
		assertTrue(connected.contains(contactId2));

		connected = c.getConnectedOrBetterContacts(transportId1);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId1));
		assertTrue(connected.contains(contactId2));

		assertEquals(singletonList(contactId2),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId2),
				c.getConnectedOrBetterContacts(transportId2));
	}

	@Test
	public void testConnectionsAreNotInterruptedUnlessPriorityIsSet() {
		// Prefer transport 2 to transport 1
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(
					singletonMap(transportId1, singletonList(transportId2))));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Connect via transport 1 (worse than 2) with no priority set
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn1);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(emptyList(), c.getConnectedContacts(transportId2));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 2 (better than 1) and set priority to high -
		// the old connection should not be interrupted, despite using a worse
		// transport, to remain compatible with old peers
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId2, conn2, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 3 (no preference) and set priority to high -
		// again, no interruptions are expected
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId3, conn3, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId3));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId3));
	}

	@Test
	public void testNewConnectionIsInterruptedIfOldConnectionUsesBetterTransport() {
		// Prefer transport 1 to transport 2
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(
					singletonMap(transportId2, singletonList(transportId1))));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Connect via transport 1 (better than 2) and set priority to low
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId1, conn1, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// The contact is not connected via transport 2 but is connected via a
		// better transport
		assertEquals(emptyList(), c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 2 (worse than 1) and set priority to high -
		// the new connection should be interrupted because it uses a worse
		// transport
		context.checking(new Expectations() {{
			oneOf(conn2).interruptOutgoingSession();
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId2, conn2, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 3 (no preference) and set priority to low -
		// no further interruptions
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId3, conn3, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId3));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId3));

		// Unregister the interrupted connection (transport 2)
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
		}});
		c.unregisterConnection(contactId1, transportId2, conn2, true, false);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// The contact is not connected via transport 2 but is connected via a
		// better transport
		assertEquals(emptyList(), c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId3));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId3));
	}

	@Test
	public void testOldConnectionIsInterruptedIfNewConnectionUsesBetterTransport() {
		// Prefer transport 2 to transport 1
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(
					singletonMap(transportId1, singletonList(transportId2))));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Connect via transport 1 (worse than 2) and set priority to high
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId1, conn1, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(emptyList(), c.getConnectedContacts(transportId2));
		assertEquals(emptyList(), c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 2 (better than 1) and set priority to low -
		// the old connection should be interrupted because it uses a worse
		// transport
		context.checking(new Expectations() {{
			oneOf(conn1).interruptOutgoingSession();
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId2, conn2, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		// Connect via transport 3 (no preference) and set priority to high -
		// no further interruptions
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId3, conn3, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId3));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId3));

		// Unregister the interrupted connection (transport 1)
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
		}});
		c.unregisterConnection(contactId1, transportId1, conn1, true, false);
		context.assertIsSatisfied();

		// The contact is not connected via transport 1 but is connected via a
		// better transport
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId2));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId2));

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId3));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId3));
	}

	@Test
	public void testNewConnectionIsInterruptedIfOldConnectionHasHigherPriority() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyMap()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Register a connection with high priority
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId1, conn1, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// Register another connection via the same transport (no priority yet)
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn2);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// Set the priority of the second connection to low - the second
		// connection should be interrupted
		context.checking(new Expectations() {{
			oneOf(conn2).interruptOutgoingSession();
		}});
		c.setPriority(contactId1, transportId1, conn2, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// Register a third connection with low priority - it should also be
		// interrupted
		context.checking(new Expectations() {{
			oneOf(conn3).interruptOutgoingSession();
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId1, conn3, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));
	}

	@Test
	public void testOldConnectionIsInterruptedIfNewConnectionHasHigherPriority() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyMap()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// Register a connection with low priority
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerOutgoingConnection(contactId1, transportId1, conn1, low);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// Register another connection via the same transport (no priority yet)
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerIncomingConnection(contactId1, transportId1, conn2);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));

		// Set the priority of the second connection to high - the first
		// connection should be interrupted
		context.checking(new Expectations() {{
			oneOf(conn1).interruptOutgoingSession();
		}});
		c.setPriority(contactId1, transportId1, conn2, high);
		context.assertIsSatisfied();

		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedOrBetterContacts(transportId1));
	}

	@Test
	public void testRegisterAndUnregisterPendingContacts() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyMap()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(
					RendezvousConnectionOpenedEvent.class)));
		}});
		assertTrue(c.registerConnection(pendingContactId));
		assertFalse(c.registerConnection(pendingContactId)); // Redundant
		context.assertIsSatisfied();

		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(
					RendezvousConnectionClosedEvent.class)));
		}});
		c.unregisterConnection(pendingContactId, true);
		context.assertIsSatisfied();

		try {
			c.unregisterConnection(pendingContactId, true);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}
	}
}
