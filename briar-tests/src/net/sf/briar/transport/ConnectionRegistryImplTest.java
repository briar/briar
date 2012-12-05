package net.sf.briar.transport;

import java.util.Arrays;
import java.util.Collections;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.protocol.TransportId;
import net.sf.briar.api.transport.ConnectionRegistry;

import org.junit.Test;

public class ConnectionRegistryImplTest extends BriarTestCase {

	private final ContactId contactId, contactId1;
	private final TransportId transportId, transportId1;

	public ConnectionRegistryImplTest() {
		super();
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
		assertEquals(Collections.singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Register an identical connection - lookup should be unaffected
		c.registerConnection(contactId, transportId);
		assertEquals(Collections.singletonList(contactId),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.emptyList(),
				c.getConnectedContacts(transportId1));
		// Unregister one of the connections - lookup should be unaffected
		c.unregisterConnection(contactId, transportId);
		assertEquals(Collections.singletonList(contactId),
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
		assertEquals(Arrays.asList(contactId, contactId1),
				c.getConnectedContacts(transportId));
		assertEquals(Collections.singletonList(contactId1),
				c.getConnectedContacts(transportId1));
	}
}
