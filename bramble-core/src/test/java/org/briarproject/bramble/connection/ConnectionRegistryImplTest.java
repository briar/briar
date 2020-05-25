package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
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
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.util.Collection;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConnectionRegistryImplTest extends BrambleMockTestCase {

	private final EventBus eventBus = context.mock(EventBus.class);
	private final PluginConfig pluginConfig = context.mock(PluginConfig.class);

	private final ContactId contactId = getContactId();
	private final ContactId contactId1 = getContactId();
	private final TransportId transportId = getTransportId();
	private final TransportId transportId1 = getTransportId();
	private final PendingContactId pendingContactId =
			new PendingContactId(getRandomId());

	@Test
	public void testRegisterAndUnregister() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyList()));
		}});

		ConnectionRegistry c =
				new ConnectionRegistryImpl(eventBus, pluginConfig);

		// The registry should be empty
		assertEquals(emptyList(), c.getConnectedContacts(transportId));
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));

		// Check that a registered connection shows up - this should
		// broadcast a ConnectionOpenedEvent and a ContactConnectedEvent
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
			oneOf(eventBus).broadcast(with(any(ContactConnectedEvent.class)));
		}});
		c.registerConnection(contactId, transportId, true);
		assertEquals(singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();

		// Register an identical connection - this should broadcast a
		// ConnectionOpenedEvent and lookup should be unaffected
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionOpenedEvent.class)));
		}});
		c.registerConnection(contactId, transportId, true);
		assertEquals(singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();

		// Unregister one of the connections - this should broadcast a
		// ConnectionClosedEvent and lookup should be unaffected
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
		}});
		c.unregisterConnection(contactId, transportId, true);
		assertEquals(singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();

		// Unregister the other connection - this should broadcast a
		// ConnectionClosedEvent and a ContactDisconnectedEvent
		context.checking(new Expectations() {{
			oneOf(eventBus).broadcast(with(any(ConnectionClosedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					ContactDisconnectedEvent.class)));
		}});
		c.unregisterConnection(contactId, transportId, true);
		assertEquals(emptyList(), c.getConnectedContacts(transportId));
		assertEquals(emptyList(), c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();

		// Try to unregister the connection again - exception should be thrown
		try {
			c.unregisterConnection(contactId, transportId, true);
			fail();
		} catch (IllegalArgumentException expected) {
			// Expected
		}

		// Register both contacts with one transport, one contact with both -
		// this should broadcast three ConnectionOpenedEvents and two
		// ContactConnectedEvents
		context.checking(new Expectations() {{
			exactly(3).of(eventBus).broadcast(with(any(
					ConnectionOpenedEvent.class)));
			exactly(2).of(eventBus).broadcast(with(any(
					ContactConnectedEvent.class)));
		}});
		c.registerConnection(contactId, transportId, true);
		c.registerConnection(contactId1, transportId, true);
		c.registerConnection(contactId1, transportId1, true);
		Collection<ContactId> connected = c.getConnectedContacts(transportId);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId));
		assertTrue(connected.contains(contactId1));
		assertEquals(singletonList(contactId1),
				c.getConnectedContacts(transportId1));
	}

	@Test
	public void testRegisterAndUnregisterPendingContacts() {
		context.checking(new Expectations() {{
			allowing(pluginConfig).getTransportPreferences();
			will(returnValue(emptyList()));
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
