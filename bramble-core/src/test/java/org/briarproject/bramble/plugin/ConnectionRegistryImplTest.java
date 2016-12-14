package org.briarproject.bramble.plugin;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.ConnectionRegistry;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.event.ConnectionClosedEvent;
import org.briarproject.bramble.api.plugin.event.ConnectionOpenedEvent;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.test.BrambleTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConnectionRegistryImplTest extends BrambleTestCase {

	private final ContactId contactId, contactId1;
	private final TransportId transportId, transportId1;

	public ConnectionRegistryImplTest() {
		contactId = new ContactId(1);
		contactId1 = new ContactId(2);
		transportId = new TransportId("id");
		transportId1 = new TransportId("id1");
	}

	@Test
	public void testRegisterAndUnregister() {
		Mockery context = new Mockery();
		final EventBus eventBus = context.mock(EventBus.class);
		context.checking(new Expectations() {{
			exactly(5).of(eventBus).broadcast(with(any(
					ConnectionOpenedEvent.class)));
			exactly(2).of(eventBus).broadcast(with(any(
					ConnectionClosedEvent.class)));
			exactly(3).of(eventBus).broadcast(with(any(
					ContactConnectedEvent.class)));
			oneOf(eventBus).broadcast(with(any(
					ContactDisconnectedEvent.class)));
		}});

		ConnectionRegistry c = new ConnectionRegistryImpl(eventBus);

		// The registry should be empty
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Check that a registered connection shows up - this should
		// broadcast a ConnectionOpenedEvent and a ContactConnectedEvent
		c.registerConnection(contactId, transportId, true);
		assertEquals(Collections.singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Register an identical connection - this should broadcast a
		// ConnectionOpenedEvent and lookup should be unaffected
		c.registerConnection(contactId, transportId, true);
		assertEquals(Collections.singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Unregister one of the connections - this should broadcast a
		// ConnectionClosedEvent and lookup should be unaffected
		c.unregisterConnection(contactId, transportId, true);
		assertEquals(Collections.singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Unregister the other connection - this should broadcast a
		// ConnectionClosedEvent and a ContactDisconnectedEvent
		c.unregisterConnection(contactId, transportId, true);
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
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
		c.registerConnection(contactId, transportId, true);
		c.registerConnection(contactId1, transportId, true);
		c.registerConnection(contactId1, transportId1, true);
		Collection<ContactId> connected = c.getConnectedContacts(transportId);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId));
		assertTrue(connected.contains(contactId1));
		assertEquals(Collections.singletonList(contactId1),
				c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();
	}
}
