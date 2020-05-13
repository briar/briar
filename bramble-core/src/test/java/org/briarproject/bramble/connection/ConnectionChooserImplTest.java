package org.briarproject.bramble.connection;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.sync.Priority;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import static org.briarproject.bramble.api.sync.SyncConstants.PRIORITY_NONCE_BYTES;
import static org.briarproject.bramble.test.TestUtils.getContactId;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.test.TestUtils.getTransportId;
import static org.briarproject.bramble.util.StringUtils.fromHexString;

public class ConnectionChooserImplTest extends BrambleMockTestCase {

	private final InterruptibleConnection conn1 =
			context.mock(InterruptibleConnection.class, "conn1");
	private final InterruptibleConnection conn2 =
			context.mock(InterruptibleConnection.class, "conn2");

	private final ContactId contactId = getContactId();
	private final TransportId transportId = getTransportId();

	private final Priority low =
			new Priority(fromHexString("00000000000000000000000000000000"));
	private final Priority high =
			new Priority(fromHexString("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"));

	private ConnectionChooserImpl chooser;

	@Before
	public void setUp() {
		chooser = new ConnectionChooserImpl();
	}

	@Test
	public void testOldConnectionIsInterruptedIfNewHasHigherPriority() {
		chooser.addConnection(contactId, transportId, conn1, low);

		context.checking(new Expectations() {{
			oneOf(conn1).interruptOutgoingSession();
		}});

		chooser.addConnection(contactId, transportId, conn2, high);
	}

	@Test
	public void testNewConnectionIsInterruptedIfOldHasHigherPriority() {
		chooser.addConnection(contactId, transportId, conn1, high);

		context.checking(new Expectations() {{
			oneOf(conn2).interruptOutgoingSession();
		}});

		chooser.addConnection(contactId, transportId, conn2, low);
	}

	@Test
	public void testConnectionIsNotInterruptedAfterBeingRemoved() {
		chooser.addConnection(contactId, transportId, conn1, low);
		chooser.removeConnection(contactId, transportId, conn1);
		chooser.addConnection(contactId, transportId, conn2, high);
	}

	@Test
	public void testConnectionIsInterruptedIfAddedTwice() {
		chooser.addConnection(contactId, transportId, conn1,
				new Priority(getRandomBytes(PRIORITY_NONCE_BYTES)));

		context.checking(new Expectations() {{
			oneOf(conn1).interruptOutgoingSession();
		}});

		chooser.addConnection(contactId, transportId, conn1,
				new Priority(getRandomBytes(PRIORITY_NONCE_BYTES)));
	}
}
