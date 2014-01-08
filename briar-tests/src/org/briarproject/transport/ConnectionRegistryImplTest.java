package org.briarproject.transport;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.ContactId;
import org.briarproject.api.TransportId;
import org.briarproject.api.transport.ConnectionRegistry;

import org.junit.Test;

public class ConnectionRegistryImplTest extends BriarTestCase {

	private final ContactId contactId, contactId1;
	private final TransportId transportId, transportId1;

	public ConnectionRegistryImplTest() {
		contactId = new ContactId(1);
		contactId1 = new ContactId(2);
		transportId = new TransportId(TestUtils.getRandomId());
		transportId1 = new TransportId(TestUtils.getRandomId());
	}

	@Test
	public void testRegisterAndUnregister() {
		ConnectionRegistry c = new ConnectionRegistryImpl();
		// The registry should be empty
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Check that a registered connection shows up
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
		// Unregister the other connection - lookup should be affected
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
		// Register both contacts with one transport, one contact with both
		c.registerConnection(contactId, transportId);
		c.registerConnection(contactId1, transportId);
		c.registerConnection(contactId1, transportId1);
		Collection<ContactId> connected = c.getConnectedContacts(transportId);
		assertEquals(2, connected.size());
		assertTrue(connected.contains(contactId));
		assertTrue(connected.contains(contactId1));
		assertEquals(Arrays.asList(contactId1),
				c.getConnectedContacts(transportId1));
	}
}
