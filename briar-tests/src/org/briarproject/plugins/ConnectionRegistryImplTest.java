package org.briarproject.plugins;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.briarproject.BriarTestCase;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.event.ContactConnectedEvent;
import org.briarproject.api.event.ContactDisconnectedEvent;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.plugins.ConnectionRegistryImpl;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class ConnectionRegistryImplTest extends BriarTestCase {

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
		// broadcast a ContactConnectedEvent
		c.registerConnection(contactId, transportId);
		assertEquals(Arrays.asList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Register an identical connection - lookup should be unaffected
		c.registerConnection(contactId, transportId);
		assertEquals(Arrays.asList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Unregister one of the connections - lookup should be unaffected
		c.unregisterConnection(contactId, transportId);
		assertEquals(Arrays.asList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Unregister the other connection - lookup should be affected -
		// this should broadcast a ContactDisconnectedEvent
		c.unregisterConnection(contactId, transportId);
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Try to unregister the connection again - exception should be thrown
		try {
			c.unregisterConnection(contactId, transportId);
			fail();
		} catch(IllegalArgumentException expected) {}
		// Register both contacts with one transport, one contact with both -
		// this should broadcast two ContactConnectedEvents
		c.registerConnection(contactId, transportId);
		c.registerConnection(contactId1, transportId);
		c.registerConnection(contactId1, transportId1);
		Collection<ContactId> connected = c.getConnectedContacts(transportId);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId));
		assertTrue(connected.contains(contactId1));
		assertEquals(Arrays.asList(contactId1),
				c.getConnectedContacts(transportId1));
		context.assertIsSatisfied();
	}
}
